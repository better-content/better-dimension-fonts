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
        failures, counts = d.inspect(lines, self.metadata)
        self.assertEqual([], failures)
        self.assertEqual(len(lines), sum(counts.values()))

    def test_wrong_dist_present_mod_new_target_error_and_extra_payload_fail(self):
        target = next(iter(d.CLIENT_TARGETS))
        _, mixin, _ = d.TARGETS[target]
        line = f'@Mixin target {target} was not found better_compat_fixes.mixins.json:{mixin}'
        self.assertTrue(d.inspect([record(line)], self.metadata)[0])
        self.metadata['dist'] = 'CLIENT'
        for bad in (record(line, level='ERROR'), record(line, level='FATAL'), record(line, logger='other/'),
                    record(line, thread='worker-2'), record(line + ' extra'), record(line.replace(target, target + 'New'))):
            self.assertTrue(d.inspect([bad], self.metadata)[0])
        self.metadata['mods'][d.TARGETS[target][0]] = 'present'
        self.assertTrue(d.inspect([record(line)], self.metadata)[0])

    def test_native_config_requires_exact_null_context_per_thread_and_no_replay(self):
        logger = 'net.minecraftforge.common.ForgeConfigSpec/CORE'
        lines = [record(self.header, logger), record(self.key, logger)]
        self.assertFalse(d.inspect(lines, self.metadata)[0])
        self.assertTrue(d.inspect([record(self.key, logger)], self.metadata)[0])
        self.assertTrue(d.inspect([record(self.header, logger), record(self.key, logger, thread='Server thread')], self.metadata)[0])
        for key in (self.key.replace('null', 'bad'), self.key.replace('600', '1'), self.key.rstrip(), self.key + 'extra'):
            self.assertTrue(d.inspect([record(self.header, logger), record(key, logger)], self.metadata)[0])
        self.assertTrue(d.inspect(lines + lines, self.metadata)[0])
        other = self.header.replace('font-common', 'other-common')
        self.metadata['corrections'][other] = [self.key]
        self.assertFalse(d.inspect(lines + [record(other, logger), record(self.key, logger)], self.metadata)[0])

    def test_client_dead_sampler_and_two_native_placeholders_are_exact_and_once(self):
        self.metadata['dist'] = 'CLIENT'
        shader = 'Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2 in the specified shader program.'
        lines = [record(shader, 'net.minecraft.client.renderer.ShaderInstance/', thread='Render thread')]
        for event in ('item.goat_horn.play', 'entity.goat.screaming.horn_break'):
            lines.append(record('Missing sound for event: minecraft:' + event, 'net.minecraft.client.sounds.SoundEngine/', thread='Render thread'))
        self.assertFalse(d.inspect(lines, self.metadata)[0])
        for value in (lines + [lines[0]], [lines[0].replace('Sampler2', 'Sampler0')],
                      [lines[0].replace('emissive', 'other')], [lines[1].replace('goat_horn', 'new_event')]):
            self.assertTrue(d.inspect(value, self.metadata)[0])
        self.metadata['dist'] = 'DEDICATED_SERVER'
        self.assertTrue(d.inspect(lines, self.metadata)[0])

    def test_sleep_pseudo_requires_provider_present_and_sleeping_mod_absent(self):
        line = record(f'Error loading class: {d.SLEEP.replace(".", "/")} (java.lang.ClassNotFoundException: {d.SLEEP})')
        self.assertTrue(d.inspect([line], self.metadata)[0])
        self.metadata['mods']['better_celestial_events'] = '0.1.0'
        self.assertFalse(d.inspect([line], self.metadata)[0])
        self.metadata['mods']['sleepingoverhaul'] = 'present'
        self.assertTrue(d.inspect([line], self.metadata)[0])

    def test_offline_notices_require_declared_loopback_network_profile(self):
        lines = [record(message, 'net.minecraft.server.dedicated.DedicatedServer/', thread='Server thread') for message in d.OFFLINE]
        self.assertTrue(d.inspect(lines, self.metadata)[0])
        self.metadata['loopback_offline'] = True
        self.assertFalse(d.inspect(lines, self.metadata)[0])
        self.assertTrue(d.inspect(lines + [lines[0]], self.metadata)[0])

    def test_unknown_warning_timing_errors_malformed_stack_and_wrong_version_fail(self):
        for message in ('unclassified', "Can't keep up! Is the server overloaded? Running 3000ms or 60 ticks behind",
                        "Mod 'better_dimension_fonts' took 1.2 s to run a deferred task."):
            self.assertTrue(d.inspect([record(message)], self.metadata)[0])
        valid = record(f'Mod file {d.module_file("fmlcore", d.LANGUAGES["fmlcore"])} is missing mods.toml file',
                       'net.minecraftforge.fml.loading.moddiscovery.ModFileParser/LOADING')
        self.assertTrue(d.inspect([valid, 'java.lang.IllegalStateException: unexpected\n'], self.metadata)[0])
        for malformed in (valid.replace('17Oct2026', '99Oct2026'), valid.replace('[main/WARN]', 'main/WARN]')):
            self.assertTrue(d.inspect([malformed], self.metadata)[0])
        self.metadata['mods']['forge'] = 'different'
        with self.assertRaises(ValueError):
            d.inspect([], self.metadata)


if __name__ == '__main__':
    unittest.main()
