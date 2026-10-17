#!/usr/bin/env python3
"""Fail-closed diagnostics for declared Font native fixtures, not a full-pack log waiver.
Classifications are bounded by current native specs, exact SDK/provider inputs and loaded mods.
"""
import collections
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path.home() / '.gradle/caches'
VERSION = '1.20.1-47.4.13'
MAPPED = CACHE / 'forge_gradle/minecraft_user_repo/net/minecraftforge/forge' / (VERSION + '_mapped_official_1.20.1') / ('forge-' + VERSION + '_mapped_official_1.20.1.jar')
LANGUAGES = {'javafmllanguage': 'cf01e2179f72bd2829c65f5226d7dd0cfb6c686',
             'lowcodelanguage': 'e3f96129ea14eba84316f2d69816253e228e6e4c',
             'mclanguage': '85796906112d9b621a7b2996f8bfda49da991ef0',
             'fmlcore': 'f77e3021d1eb31d61f8d1b92d18ed4fb3c2806e5'}
TARGETS = {
 'slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity': ('tconstruct', 'tconstruct.PartBuilderBlockEntityMixin', '167ed1a4c24560cc35b4e7b4d370aa92b98212dd0dc8f7999921575117bb1e9a'),
 'slimeknights.tconstruct.tables.block.entity.inventory.PartBuilderContainerWrapper': ('tconstruct', 'tconstruct.PartBuilderContainerWrapperMixin', '35878ff70f15800b6de1b5ae07c58f06d886f7d316823a24e88ed331f929d426'),
 'slimeknights.tconstruct.tables.menu.PartBuilderContainerMenu$PatternSlot': ('tconstruct', 'tconstruct.PartBuilderPatternSlotPlacementMixin', 'e9e0006c03c87c0ae29372640b3e58bb06f169fac7269e5a750d333383226f27'),
 'com.ferreusveritas.dynamictrees.compat.season.NormalSeasonManager': ('dynamictrees', 'dynamictrees.NormalSeasonManagerMixin', '5805ee0fe52f752c26c2ec48e9a1f0828d837fe706daa0d08241050e7dc36f13'),
 'team.creative.ambientsounds.sound.AmbientSoundEngine': ('ambientsounds', 'ambientsounds.AmbientSoundEngineMixin', '0418bfd4b04eb8004eb66c70b382e10c7a06d84af372c3d3366c82b289be0489'),
 'slimeknights.tconstruct.tables.client.inventory.PartBuilderScreen': ('tconstruct', 'tconstruct.PartBuilderPatternSlotBackgroundMixin', 'f37024baa836eb38caf4ded7d389fd460b6103e17a8609643ad27d328ade2989'),
 'weather2.weathersystem.fog.FogAdjuster': ('weather2', 'weather2.FogAdjusterMixin', '74e16667f32e87273f500e4e014b095759f0163156534480f809c19dc33286b3')}
CLIENT_TARGETS = {'team.creative.ambientsounds.sound.AmbientSoundEngine',
                  'slimeknights.tconstruct.tables.client.inventory.PartBuilderScreen',
                  'weather2.weathersystem.fog.FogAdjuster'}
SLEEP = 'github.cosmicdan.sleepingoverhaul.server.ServerState'
OFFLINE = ('**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!',
 'The server will make no attempt to authenticate usernames. Beware.',
 'While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.',
 'To change this, set "online-mode" to "true" in the server.properties file.')
RECORD = re.compile(r'^\[(\d{2}[A-Z][a-z]{2}\d{4} \d{2}:\d{2}:\d{2}\.\d{3})\] \[([^\]\n]+)/([A-Z]+)\] \[([^\]\n]+)\]: (.*)$')


def module_file(name, digest):
    return CACHE / 'modules-2/files-2.1/net.minecraftforge' / name / VERSION / digest / (name + '-' + VERSION + '.jar')


def check_hash(data, expected, algorithm='sha256'):
    if hashlib.new(algorithm, data).hexdigest() != expected:
        raise ValueError('Native diagnostic input hash changed')


def provider(repository, artifact):
    return Path(os.environ.get('BC_CUSTOM_MOD_JAR_DIR', str(ROOT.parent / repository / 'build/libs'))) / artifact


def validate_inputs(client=False):
    for name, digest in LANGUAGES.items():
        file = module_file(name, digest)
        check_hash(file.read_bytes(), digest.zfill(40), 'sha1')
        with zipfile.ZipFile(file) as jar:
            if 'META-INF/mods.toml' in jar.namelist():
                raise ValueError('Native language artifact acquired mod metadata')
    with zipfile.ZipFile(MAPPED) as jar:
        for name, digest in {
            'net/minecraftforge/common/ForgeConfigSpec.class': '633184c06812ebeb2fc6ee3f101da946657b5d46c7c8cfb22d63130cd491db3d',
            'net/minecraft/server/packs/VanillaPackResourcesBuilder.class': 'cf63fa701dbde417c8ccd241325e0eaa0c03bbfa4c56c509a36f729e6ace9485',
            'net/minecraft/client/renderer/ShaderInstance.class': 'd3f1e096abfb6fc4fe3a71124c195508152e6a1cabb48407baf0c2e4d7d5b344',
            'net/minecraft/client/sounds/SoundEngine.class': '919aeb01ae7c97e760aeda0fb6055778ad923943be01c8280a7cdc68dfb10078',
            'net/minecraft/sounds/SoundEvents.class': '718fde508138fb35c01c91ddb9f969cb622b8d265037b75be6b1d7873320ae95',
            'net/minecraft/server/dedicated/DedicatedServer.class': 'ff3419741829f8ef4359e7dda8093252c4bce0a4f3ee02da001f1da5ea868228'
        }.items():
            check_hash(jar.read(name), digest)
    with zipfile.ZipFile(module_file('fmlloader', 'b27b57b2f641bfe6ecdaf206ee54880d26bb9c10')) as jar:
        check_hash(jar.read('net/minecraftforge/fml/loading/FMLConfig.class'), '65ac0669310f3a98e1de85654c124f535acacd721f7bfeec80db3001d3ef2cd0')
        check_hash(jar.read('net/minecraftforge/fml/loading/moddiscovery/ModFileParser.class'), 'bdfe1926139ccdb5cf69570a5985e560d925dd3f5c99d552f4e5adac674888a4')
    with zipfile.ZipFile(provider('better-compat-fixes', 'better-compat-fixes-0.1.9.jar')) as jar:
        descriptor = jar.read('better_compat_fixes.mixins.json')
        check_hash(descriptor, '0fb334f27fd59f2260d71b66b725e2ae6b1c6b0d444f59dcc2772565fc1e85aa')
        config = json.loads(descriptor)
        if config['required'] is not False:
            raise ValueError('Optional native mixins became required')
        for _, mixin, digest in TARGETS.values():
            if mixin not in config['mixins'] + config['client']:
                raise ValueError('Optional target no longer declared')
            check_hash(jar.read('com/bettercontent/bettercompatfixes/mixin/' + mixin.replace('.', '/') + '.class'), digest)
    with zipfile.ZipFile(provider('better-celestial-events', 'better-celestial-events-0.1.0.jar')) as jar:
        check_hash(jar.read('better_celestial_events.mixins.json'), '99f22292a3e93963902bb53ce18a09633b6f205cfe3fc98feb994cd39030216f')
        cls = jar.read('com/bettercontent/celestial/mixin/SleepingOverhaulMixin.class')
        check_hash(cls, '169988da4c9f630fb9f33221ff584777eaf92edb9851bc5734bacca8234796ba')
        if b'org/spongepowered/asm/mixin/Pseudo' not in cls:
            raise ValueError('Sleeping target ceased to be @Pseudo')
    if not client:
        return
    # Vanilla declares Sampler2 but never uses it; GL optimizes this sole dead binding away.
    with zipfile.ZipFile(CACHE / 'forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar') as jar:
        base = 'assets/minecraft/shaders/core/rendertype_entity_translucent_emissive.'
        for suffix, digest in {'json': 'd4ea27980006208a198c7f181e25aab6d6abeea49518c8ddbd5fac2edc80d515',
             'vsh': 'dad84bd4d8e538c775704199d3953055e12ec76450904ad46f39073982161cde',
             'fsh': '968b666a761dd7c3641b4aa852ce266680db57f82e3bb8d4218d8c7af51312d4'}.items():
            check_hash(jar.read(base + suffix), digest)
        if jar.read(base + 'vsh').count(b'Sampler2') != 1 or b'Sampler2' in jar.read(base + 'fsh'):
            raise ValueError('Vanilla sampler became live')
    sound = CACHE / 'forge_gradle/assets/objects/40/40a4222b7ada165fa98cb9215129aeb8d9b6b379'
    check_hash(sound.read_bytes(), '52131d187d88128bde6dcd808768213543f01503fab3ed6a51b2002d5a4c279c')
    sounds = json.loads(sound.read_bytes())
    if any(key in sounds for key in ('item.goat_horn.play', 'entity.goat.screaming.horn_break')):
        raise ValueError('Vanilla placeholder sound acquired a definition')


def classify(thread, logger, message, metadata, contexts):
    if thread == 'main' and logger == 'net.minecraftforge.fml.loading.moddiscovery.ModFileParser/LOADING':
        for name, digest in LANGUAGES.items():
            if message == f'Mod file {module_file(name, digest)} is missing mods.toml file':
                return 'native-language-metadata'
    if thread == 'main' and logger == 'mixin/':
        for target, (mod, mixin, _) in TARGETS.items():
            if target in CLIENT_TARGETS and metadata['dist'] != 'CLIENT':
                continue
            if mod not in metadata['mods'] and message in (
                f'Error loading class: {target.replace(".", "/")} (java.lang.ClassNotFoundException: {target})',
                f'@Mixin target {target} was not found better_compat_fixes.mixins.json:{mixin}'):
                return 'absent-optional-target'
        if ('better_celestial_events' in metadata['mods'] and 'sleepingoverhaul' not in metadata['mods']
                and message == f'Error loading class: {SLEEP.replace(".", "/")} (java.lang.ClassNotFoundException: {SLEEP})'):
            return 'absent-pseudo-sleep-target'
    if logger == 'net.minecraft.server.packs.VanillaPackResourcesBuilder/' and thread == (
            'Render thread' if metadata['dist'] == 'CLIENT' else 'main'):
        if re.fullmatch(r"Assets URL 'union:" + re.escape(str(MAPPED)) + r"%23[1-9][0-9]*!/(assets|data)/\.mcassetsroot' uses unexpected schema", message):
            return 'native-union-assets'
    if thread == 'main' and logger == 'net.minecraftforge.fml.loading.FMLConfig/CORE':
        if message == f'Configuration file {metadata["directory"]}/config/fml.toml is not correct. Correcting':
            return 'native-loader-first-defaults'
    if logger == 'net.minecraftforge.common.ForgeConfigSpec/CORE' and (thread in ('main', 'Server thread', 'Render thread') or re.fullmatch(r'modloading-worker-[0-9]+', thread)):
        if message in metadata['corrections']:
            contexts[thread] = message
            return 'registered-native-first-defaults'
        if message in metadata['corrections'].get(contexts.get(thread), []):
            return 'registered-native-first-defaults'
    if metadata['dist'] == 'CLIENT' and thread == 'Render thread':
        if logger == 'net.minecraft.client.renderer.ShaderInstance/' and message == 'Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2 in the specified shader program.':
            return 'vanilla-dead-sampler'
        if logger == 'net.minecraft.client.sounds.SoundEngine/' and message in (
            'Missing sound for event: minecraft:item.goat_horn.play',
            'Missing sound for event: minecraft:entity.goat.screaming.horn_break'):
            return 'vanilla-undefined-sound-placeholder'
    if (metadata['dist'] == 'DEDICATED_SERVER' and metadata.get('loopback_offline') is True
            and thread == 'Server thread' and logger == 'net.minecraft.server.dedicated.DedicatedServer/'
            and message in OFFLINE):
        return 'declared-loopback-offline-fixture'
    return None


def inspect(lines, metadata):
    if metadata.get('dist') not in ('CLIENT', 'DEDICATED_SERVER') or metadata['mods'].get('forge') != '47.4.13':
        raise ValueError('Undeclared native fixture/version')
    failures, counts, seen, contexts = [], collections.Counter(), set(), {}
    previous = None
    for number, raw in enumerate(lines, 1):
        line = raw.rstrip('\n')
        match = RECORD.fullmatch(line)
        if not match:
            if re.search(r'/(WARN|ERROR|FATAL)\]', line) or previous in ('WARN', 'ERROR', 'FATAL') and line.strip():
                failures.append((number, line))
            continue
        stamp, thread, level, logger, message = match.groups()
        try:
            datetime.datetime.strptime(stamp, '%d%b%Y %H:%M:%S.%f')
        except ValueError:
            failures.append((number, line))
            continue
        previous = level
        if level in ('TRACE', 'DEBUG', 'INFO'):
            continue
        category = classify(thread, logger, message, metadata, contexts) if level == 'WARN' else None
        identity = (logger, message, contexts.get(thread) if logger == 'net.minecraftforge.common.ForgeConfigSpec/CORE' else None)
        if category is None or identity in seen:
            failures.append((number, line))
        else:
            counts[category] += 1
            seen.add(identity)
    return failures, dict(counts)


def main(directory):
    directory = directory.resolve()
    metadata = json.loads((directory / 'native-diagnostic-inputs.json').read_text())
    validate_inputs(client=metadata['dist'] == 'CLIENT')
    if metadata['nonce'] != (directory / 'native-diagnostic-nonce').read_text().strip() or Path(metadata['directory']) != directory:
        raise ValueError('Native metadata is not from this invocation/directory')
    properties = (directory / 'server.properties').read_text() if (directory / 'server.properties').exists() else ''
    metadata['loopback_offline'] = {'online-mode=false', 'server-ip=127.0.0.1'} <= set(properties.splitlines())
    failed = False
    for filename in ('debug.log', 'latest.log'):
        log = directory / 'logs' / filename
        failures, counts = inspect(log.read_text().splitlines(keepends=True), metadata)
        if counts.get('native-language-metadata') != 4:
            raise ValueError('Incomplete native bootstrap diagnostics: ' + str(log))
        if counts.get('declared-loopback-offline-fixture', 0) not in (0, 4):
            raise ValueError('Incomplete declared loopback fixture notice')
        print('Font native classified diagnostics:', filename, json.dumps(counts, sort_keys=True))
        for number, line in failures:
            print(f'UNCLASSIFIED {log}:{number}: {line}', file=sys.stderr)
        failed |= bool(failures)
    return 1 if failed else 0


if __name__ == '__main__':
    try:
        sys.exit(main(Path(sys.argv[1])))
    except Exception as error:
        print('Native diagnostic input failure:', error, file=sys.stderr)
        sys.exit(1)
