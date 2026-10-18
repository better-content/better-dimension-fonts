import hashlib
import io
import json
import zipfile
import unittest
import check_native_diagnostics as d


def record(message, logger='mixin/', level='WARN', thread='main'):
    return f'[17Oct2026 12:00:00.123] [{thread}/{level}] [{logger}]: {message}\n'


class NativeDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        self.header = 'Configuration file /fresh/config/font-common.toml is not correct. Correcting'
        self.key = 'Incorrect key charge.minimum was corrected from null to its default, 600. '
        self.metadata = {'directory': '/fresh', 'dist': 'DEDICATED_SERVER', 'mods': {'forge': '47.4.13'},
                         'corrections': {self.header: [self.key]}}

    def test_actual_pinned_native_inputs_and_tamper_rejection(self):
        d.validate_inputs()
        with self.assertRaises(ValueError):
            d.check_hash(b'changed native class', '0' * 64)

    def test_exact_optional_targets_language_metadata_and_union_inputs(self):
        self.metadata['dist'] = 'CLIENT'
        lines = []
        for target, (_, mixin, _) in d.TARGETS.items():
            lines += [record(f'Error loading class: {target.replace(".", "/")} (java.lang.ClassNotFoundException: {target})'),
                      record(f'@Mixin target {target} was not found better_compat_fixes.mixins.json:{mixin}')]
        for name, digest in d.LANGUAGES.items():
            lines.append(record(f'Mod file {d.module_file(name, digest)} is missing mods.toml file',
                                'net.minecraftforge.fml.loading.moddiscovery.ModFileParser/LOADING'))
        for folder in ('assets', 'data'):
            lines.append(record(f"Assets URL 'union:{d.MAPPED}%23181!/{folder}/.mcassetsroot' uses unexpected schema",
                                'net.minecraft.server.packs.VanillaPackResourcesBuilder/', thread='Render thread'))
        failures, counts = d.inspect(lines * 3, self.metadata)
        self.assertEqual([], failures)
        self.assertEqual(len(lines) * 3, sum(counts.values()))
        self.assertEqual(12, counts['native-language-metadata'])

    def test_classification_still_requires_context_but_correctness_does_not_require_a_label(self):
        target = next(iter(d.CLIENT_TARGETS))
        _, mixin, _ = d.TARGETS[target]
        line = record(f'@Mixin target {target} was not found better_compat_fixes.mixins.json:{mixin}')
        self.assertEqual({'unclassified-warning': 1}, d.inspect([line], self.metadata)[1])
        self.metadata['dist'] = 'CLIENT'
        self.assertEqual({'absent-optional-target': 1}, d.inspect([line], self.metadata)[1])
        self.metadata['mods'][d.TARGETS[target][0]] = 'present'
        failures, counts = d.inspect([line], self.metadata)
        self.assertEqual([], failures)
        self.assertEqual({'unclassified-warning': 1}, counts)

    def test_config_context_is_per_thread_and_repetition_is_observed(self):
        logger = 'net.minecraftforge.common.ForgeConfigSpec/CORE'
        lines = [record(self.header, logger), record(self.key, logger)]
        failures, counts = d.inspect(lines * 2, self.metadata)
        self.assertEqual([], failures)
        self.assertEqual(4, counts['registered-native-first-defaults'])
        failures, counts = d.inspect([record(self.header, logger), record(self.key, logger, thread='Server thread')], self.metadata)
        self.assertEqual([], failures)
        self.assertEqual(1, counts['unclassified-warning'])

    def test_client_assets_and_offline_notices_are_diagnostics_not_count_gates(self):
        self.metadata['dist'] = 'CLIENT'
        lines = [record('Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2 in the specified shader program.',
                        'net.minecraft.client.renderer.ShaderInstance/', thread='Render thread')]
        for event in ('item.goat_horn.play', 'entity.goat.screaming.horn_break'):
            lines.append(record('Missing sound for event: minecraft:' + event,
                                'net.minecraft.client.sounds.SoundEngine/', thread='Render thread'))
        failures, counts = d.inspect(lines * 5, self.metadata)
        self.assertEqual([], failures)
        self.assertEqual(5, counts['vanilla-dead-sampler'])
        self.assertEqual(10, counts['vanilla-undefined-sound-placeholder'])
        self.metadata.update(dist='DEDICATED_SERVER', loopback_offline=True)
        notices = [record(message, 'net.minecraft.server.dedicated.DedicatedServer/', thread='Server thread') for message in d.OFFLINE]
        self.assertEqual(8, d.inspect(notices * 2, self.metadata)[1]['declared-loopback-offline-fixture'])
        self.assertEqual([], d.inspect(notices[:1], self.metadata)[0])

    def test_unknown_repeated_timing_and_malformed_warnings_are_not_correctness(self):
        lines = [record('unclassified advisory')] * 3501 + [
            record("Can't keep up! Is the server overloaded? Running 3000ms or 60 ticks behind"),
            record("Mod 'better_dimension_fonts' took 1.2 s to run a deferred task."),
            record('advisory').replace('17Oct2026', '99Oct2026'),
            record('advisory').replace('[main/WARN]', 'main/WARN]')]
        self.assertEqual([], d.inspect(lines, self.metadata)[0])

    def test_error_severity_never_uses_a_compatibility_exemption(self):
        messages = ['Missing sound for event: minecraft:item.goat_horn.play', 'new advisory',
                    "Can't keep up! Is the server overloaded? Running 3000ms or 60 ticks behind"]
        for severity in ('ERROR', 'FATAL'):
            lines = [record(message, level=severity) for message in messages]
            self.assertEqual([1, 2, 3], [number for number, _ in d.inspect(lines, self.metadata)[0]])
        self.assertTrue(d.inspect(['garbled [main/ERROR] advisory'], self.metadata)[0])
        self.assertEqual([], d.inspect([record('quoted /ERROR] is text', level='INFO')], self.metadata)[0])

    def test_actual_operation_failures_block_at_any_severity_and_without_envelope(self):
        messages = ['Registry Object not present: font:missing', 'Registry registration failed',
                    'Error loading KubeJS script', 'KubeJS errors found [2]', 'Failed to save world',
                    'Corrupted chunk data', 'Mixin apply failed', 'java.lang.IllegalStateException: unexpected',
                    'java.lang.AssertionError: invariant', 'java.lang.OutOfMemoryError: heap']
        for severity in ('INFO', 'WARN', 'ERROR', 'FATAL'):
            lines = [record(message, level=severity) for message in messages]
            self.assertEqual(len(lines), len(d.inspect(lines, self.metadata)[0]))
        self.assertEqual(len(messages), len(d.inspect(messages, self.metadata)[0]))

    def test_fixture_version_and_input_hash_remain_gates(self):
        self.metadata['mods']['forge'] = 'different'
        with self.assertRaises(ValueError):
            d.inspect([], self.metadata)
        with self.assertRaises(ValueError):
            d.check_hash(b'changed native class', '0' * 64)


class OptionalNativeProbeTest(unittest.TestCase):
    def jarjar(self, **kwargs):
        return record('Failed to load resource META-INF/jarjar/metadata.json from nativeFixture, '
                      'it does not contain dependency information.',
                      logger='net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileDependencyLocator/',
                      level='DEBUG', **kwargs)

    def netty(self, array=False):
        if array:
            message = 'jdk.internal.misc.Unsafe.allocateUninitializedArray(int): unavailable'
            heading = ('java.lang.IllegalAccessException: class io.netty.util.internal.PlatformDependent0$7 '
                       '(in module io.netty.common) cannot access class jdk.internal.misc.Unsafe '
                       '(in module java.base) because module java.base does not export jdk.internal.misc to module io.netty.common')
            frame = '\tat io.netty.util.internal.PlatformDependent0$7.run(PlatformDependent0.java:410) ~[netty-common-4.1.82.Final.jar:4.1.82.Final]'
        else:
            message = 'direct buffer constructor: unavailable'
            heading = 'java.lang.UnsupportedOperationException: Reflective setAccessible(true) disabled'
            frame = '\tat io.netty.util.internal.ReflectionUtil.trySetAccessible(ReflectionUtil.java:31) ~[netty-common-4.1.82.Final.jar:4.1.82.Final]'
        return [record(message, logger='io.netty.util.internal.PlatformDependent0/', level='DEBUG'), heading, frame]

    def test_optional_jarjar_miss_is_exact_debug_diagnostic_without_count_budget(self):
        lines = [self.jarjar()] * 20
        failures, counts = d.scan_operations(lines)
        self.assertEqual([], failures)
        self.assertEqual(20, counts['native-optional-jarjar-probe'])
        for text in [self.jarjar().replace('/DEBUG]', '/ERROR]'),
                     self.jarjar().replace('/DEBUG]', '/FATAL]'),
                     self.jarjar().replace('/DEBUG]', '/WARN]'),
                     self.jarjar(thread='Server thread'),
                     self.jarjar().replace('AbstractJarFileDependencyLocator/', 'OtherLocator/'),
                     self.jarjar().replace('metadata.json', 'required.json'),
                     self.jarjar().replace('nativeFixture', 'nativeFixture AssertionError: invariant'),
                     self.jarjar().replace('nativeFixture', 'nativeFixture OutOfMemoryError: heap'),
                     self.jarjar().replace('it does not contain dependency information.', 'JSON is malformed.'),
                     self.jarjar().rstrip() + ' java.lang.AssertionError: failure',
                     self.jarjar().replace('17Oct2026', '99Oct2026')]:
            with self.subTest(text=text):
                self.assertTrue(d.scan_operations([text])[0])

    def test_caught_netty_reflection_stack_has_record_and_frame_provenance(self):
        lines = (self.netty() + self.netty(array=True)) * 12
        failures, counts = d.scan_operations(lines)
        self.assertEqual([], failures)
        self.assertEqual(24, counts['native-netty-reflection-probe'])
        # A debug capability miss without a throwable is also not a failed operation.
        self.assertEqual([], d.scan_operations(self.netty()[:1])[0])

    def test_netty_context_cannot_be_borrowed_by_other_loggers_threads_levels_or_records(self):
        for array in (False, True):
            original = self.netty(array)
            variants = []
            for old, new in [('/DEBUG]', '/ERROR]'), ('/DEBUG]', '/FATAL]'),
                             ('/DEBUG]', '/INFO]'), ('main/DEBUG', 'Server thread/DEBUG'),
                             ('io.netty.util.internal.PlatformDependent0/', 'other/'),
                             ('17Oct2026', '99Oct2026')]:
                variants.append([original[0].replace(old, new), *original[1:]])
            variants += [original[1:], original[:2],
                         [original[0], record('unrelated', level='INFO'), *original[1:]],
                         [original[0], '', *original[1:]],
                         [original[0], original[1], '\tat unrelated.Probe.run(Probe.java:1)'],
                         [original[0], original[1] + ' extra failure payload', original[2]]]
            for lines in variants:
                with self.subTest(lines=lines):
                    self.assertTrue(d.scan_operations(lines)[0])

    def test_debug_probe_does_not_hide_separate_failures_causes_or_uncaught_errors(self):
        for failure in ['java.lang.AssertionError: invariant', 'java.lang.OutOfMemoryError: heap',
                        'java.lang.Error: catastrophic', 'java.lang.Exception: uncaught',
                        'Caused by: java.lang.IllegalStateException: failed',
                        'Exception in thread "server" java.lang.IllegalStateException: uncaught',
                        'Failed to load required registry data', 'Error loading KubeJS script']:
            lines = self.netty() + [failure]
            findings = d.scan_operations(lines)[0]
            self.assertIn((len(lines), failure), findings)
        error = record('direct buffer constructor: unavailable', logger='io.netty.util.internal.PlatformDependent0/', level='ERROR')
        self.assertTrue(d.scan_operations([error])[0])
        self.assertTrue(d.scan_operations([record('Failed to read required save data', level='DEBUG')])[0])

    def test_public_inspect_reports_probe_categories_and_still_blocks_following_error(self):
        metadata = {'dist': 'DEDICATED_SERVER', 'mods': {'forge': '47.4.13'}, 'corrections': {}}
        lines = self.netty() + [self.jarjar()]
        failures, counts = d.inspect(lines, metadata)
        self.assertEqual([], failures)
        self.assertEqual(1, counts['native-netty-reflection-probe'])
        self.assertEqual(1, counts['native-optional-jarjar-probe'])
        error = record('Failed to save required world data', level='ERROR')
        failures, _ = d.inspect(lines + [error], metadata)
        self.assertIn((len(lines) + 1, error.rstrip()), failures)

    def test_jarjar_debug_probe_does_not_hide_following_unstructured_failure(self):
        self.assertEqual([(2, 'java.lang.IllegalStateException: required resource failure')],
                         d.scan_operations([self.jarjar(), 'java.lang.IllegalStateException: required resource failure'])[0])


class CompatDescriptorContractTest(unittest.TestCase):
    def setUp(self):
        self.payloads = {'fixture.Common': b'pinned common target class',
                         'fixture.Client': b'pinned client target class'}
        self.targets = {f'native.Target{index}': ('optional_mod', mixin, hashlib.sha256(payload).hexdigest())
                        for index, (mixin, payload) in enumerate(self.payloads.items())}
        self.config = {'required': False, 'minVersion': '0.8',
                       'package': 'com.bettercontent.bettercompatfixes.mixin',
                       'plugin': 'com.bettercontent.bettercompatfixes.mixin.BetterContentMixinPlugin',
                       'compatibilityLevel': 'JAVA_17', 'injectors': {'defaultRequire': 0},
                       'mixins': ['fixture.Common'], 'client': ['fixture.Client'], 'server': []}

    def validate(self, config, *, payloads=None, omit=()):
        archive = io.BytesIO()
        with zipfile.ZipFile(archive, 'w') as jar:
            jar.writestr('better_compat_fixes.mixins.json', json.dumps(config, sort_keys=True, indent=3))
            for mixin, data in (self.payloads if payloads is None else payloads).items():
                if mixin not in omit:
                    jar.writestr('com/bettercontent/bettercompatfixes/mixin/' + mixin.replace('.', '/') + '.class', data)
        archive.seek(0)
        with zipfile.ZipFile(archive) as jar:
            d.validate_compat_inputs(jar, self.targets, client_targets={'fixture.Client'})

    def test_unrelated_inventory_and_descriptor_formatting_do_not_change_contract(self):
        self.validate(self.config)
        self.config['mixins'].insert(0, 'unrelated.NewIntegrationMixin')
        self.config['client'].append('unrelated.NewVisualMixin')
        self.validate(self.config)

    def test_required_package_plugin_mapping_and_loader_policy_drift_fail(self):
        for key, value in [('required', True), ('required', 0), ('package', 'other.mixin'),
                           ('plugin', 'other.Plugin'), ('compatibilityLevel', 'JAVA_21'),
                           ('minVersion', '0.9'), ('refmap', 'other.refmap.json'),
                           ('injectors', {'defaultRequire': 1}), ('injectors', {'defaultRequire': False})]:
            changed = dict(self.config, **{key: value})
            with self.subTest(key=key, value=value), self.assertRaisesRegex(ValueError, 'better_compat_fixes.mixins.json'):
                self.validate(changed)

    def test_relevant_missing_duplicate_or_wrong_side_mapping_fails(self):
        for config in [dict(self.config, mixins=[]), dict(self.config, client=[]),
                       dict(self.config, mixins=['fixture.Common', 'fixture.Common']),
                       dict(self.config, mixins=['fixture.Common', 'fixture.Client']),
                       dict(self.config, mixins=[], server=['fixture.Common']),
                       dict(self.config, mixins=['fixture.Client'], client=['fixture.Common']),
                       dict(self.config, client='fixture.Client')]:
            with self.subTest(config=config), self.assertRaisesRegex(ValueError, 'better_compat_fixes.mixins.json'):
                self.validate(config)

    def test_relevant_class_bytes_and_missing_archive_entries_fail_with_context(self):
        for mixin in self.payloads:
            changed = dict(self.payloads, **{mixin: b'changed target annotations or executable mapping'})
            with self.subTest(mixin=mixin), self.assertRaisesRegex(ValueError, mixin.split('.')[-1] + r'\.class.*sha256 mismatch'):
                self.validate(self.config, payloads=changed)
            with self.assertRaisesRegex(ValueError, mixin.split('.')[-1] + r'\.class.*exactly one'):
                self.validate(self.config, omit={mixin})


if __name__ == '__main__':
    unittest.main()
