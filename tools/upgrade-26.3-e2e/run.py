"""Launch an isolated real 26.3 server, exercise Plus over TCP, then restart it."""
import argparse
import hashlib
import json
import re
import shutil
import socket
import subprocess
import sys
import time
import traceback
import uuid
from pathlib import Path
from zipfile import ZipFile

sys.dont_write_bytecode = True
from support import HERE, OUT, ROOT, java_tool, jvm_properties
from client import Client, varint, readvar, string

PORT = 25579
NAME = 'UpgradeTester'


def prepare(run, jar, defaults=False):
    run.mkdir(parents=True)
    with ZipFile(jar) as archive:
        digest, url, filename = archive.read('META-INF/download-context').decode().strip().split('\t')
    vanilla = ROOT / 'DeerFoliaPlus-server/.gradle/caches/paperweight/taskCache/downloadServerJar.jar'
    if not vanilla.is_file() or hashlib.sha256(vanilla.read_bytes()).hexdigest() != digest:
        raise RuntimeError('Build the matching server first; missing/mismatched cached vanilla jar')
    (run / 'cache').mkdir()
    shutil.copyfile(vanilla, run / 'cache' / filename)
    (run / 'plugins').mkdir()
    shutil.copyfile(OUT / 'PlusUpgradeE2E.jar', run / 'plugins/PlusUpgradeE2E.jar')
    (run / 'eula.txt').write_text('eula=true\n')
    offline_uuid = uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + NAME).encode()).digest(), version=3)
    (run / 'ops.json').write_text(json.dumps([{'uuid': str(offline_uuid), 'name': NAME, 'level': 4, 'bypassesPlayerLimit': True}]))
    (run / 'server.properties').write_text(f'''server-ip=127.0.0.1
server-port={PORT}
online-mode=false
enforce-secure-profile=false
view-distance=4
simulation-distance=4
level-type=minecraft:flat
level-seed=719225
spawn-protection=0
gamemode=creative
max-players=8
network-compression-threshold=256
white-list=false
generator-settings={{"layers":[{{"block":"minecraft:bedrock","height":1}},{{"block":"minecraft:dirt","height":2}},{{"block":"minecraft:grass_block","height":1}}],"biome":"minecraft:plains"}}
''')
    (run / 'config').mkdir()
    (run / 'config/paper-global.yml').write_text('_version: 31\nthreaded-regions:\n  threads: 2\n')
    (run / 'config/deer-folia.yml').write_text('lithium-optimize:\n  enabled: true\nafk-network-optimization:\n  afk-threshold-ticks: 12000\n')
    # Keep an old enabled section only in this upgrade fixture, never in production.
    if not defaults:
        (run / 'config/deer-folia-plus.yml').write_text('''fake-player:
  enable: true
  prefix: ""
  resident-bot: true
  amount-per-player: 4
  spawn-phantom: false
posture:
  enabled: true
custom-recipe:
  enabled: true
syncmatica:
  enable: true
servux:
  structure-protocol: true
  entity-protocol: true
recipe-sync:
  neoforge: true
  fabric: true
''')
    recipes = '''recipes:
  upgrade_shaped:
    type: shaped
    pattern: ["D"]
    ingredients:
      D: minecraft:dirt
    result:
      item: minecraft:diamond
      amount: 2
      custom-id: upgrade_gem
  upgrade_shapeless:
    type: shapeless
    ingredients:
      - item: minecraft:diamond
        custom-id: upgrade_gem
    result:
      item: minecraft:emerald
'''
    for kind in ['smelting', 'blasting', 'smoking', 'campfire_cooking', 'stonecutting']:
        recipes += f'''  upgrade_{kind}:
    type: {kind}
    ingredient: minecraft:dirt
    result:
      item: minecraft:emerald
'''
    (run / 'config/custom-recipes.yml').write_text(recipes)


def execute(run, jar, restart=False, defaults=False):
    logfile = OUT / ('defaults.log' if defaults else 'restart.log' if restart else 'initial.log')
    stream = logfile.open('w')
    command = [java_tool('java'), '-Xms1G', '-Xmx3G', '--enable-native-access=ALL-UNNAMED', *jvm_properties(),
               '-Dminecraft.api.discovery.host=http://127.0.0.1:25576/discovery', f'-Dplus.e2e.defaults={str(defaults).lower()}', '-jar', str(jar), '--nogui']
    process = subprocess.Popen(command, cwd=run, stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT, text=True)
    client = None
    result = {'restart': restart, 'default_config_run': defaults, 'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'checks': [], 'status': 'failed'}

    def content():
        stream.flush()
        return logfile.read_text()

    def console(command):
        process.stdin.write(command + '\n')
        process.stdin.flush()

    def until(predicate, description, timeout=120):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            data = content()
            if 'PLUS_FAIL' in data:
                raise AssertionError(data[data.index('PLUS_FAIL'):][-5000:])
            if process.poll() is not None:
                raise RuntimeError('Server exited: ' + data[-4000:])
            if client and client.errors:
                raise RuntimeError('TCP client: ' + repr(client.errors))
            if predicate():
                return
            time.sleep(.1)
        raise TimeoutError(description + '\n' + content()[-5000:])

    def marker(text, timeout=120):
        until(lambda: text in content(), text, timeout)

    def assertion(stage, *args):
        text = f'{stage} {NAME}' + ((' ' + ' '.join(map(str, args))) if args else '')
        expected = 'PLUS_PASS complete-' + text.replace(' ', '-')
        offset = len(content())
        console('pluse2e ' + text)
        until(lambda: expected in content()[offset:], expected)

    def command(text):
        client.command(text)
        time.sleep(.4)

    def payload_check(channel, description, predicate=lambda data: bool(data)):
        until(lambda: any(predicate(data) for data in client.payloads.get(channel, [])), description, 60)
        result['checks'].append(description)

    try:
        marker('Done (')
        marker('PLUS_PASS plugin-enabled')
        if defaults:
            generated=(run / 'config/deer-folia-plus.yml').read_text()
            if re.search(r'(?m)^\s*posture\s*:',generated):
                raise AssertionError('Fresh generated config still contains posture')
            result['checks'].append('fresh-generated-default-config-has-no-posture')
            result['status']='passed'
            return result
        client = Client(NAME, PORT)
        until(lambda: client.ready.is_set(), 'real TCP login')
        result['checks'].append('tcp-login-protocol-777')
        if restart:
            # Resident restore is asynchronous; wait for the bot's placement before assertions.
            marker('UpgradeBot[Local] logged in')
            assertion('restart')
            command('bot remove UpgradeBot')
            assertion('bot-removed')
        else:
            assertion('setup')
            command('fly')
            command('god')
            command('more 17')
            assertion('commands')
            assertion('god-temporary-setup')
            command('god')
            assertion('god-permanent', 'true')
            command('god')
            assertion('god-permanent', 'false')
            assertion('god-cleanup')
            command('hat')
            assertion('hat')
            for menu, kind in [('workbench', 'WORKBENCH'), ('stonecutter', 'STONECUTTER'), ('anvil', 'ANVIL'), ('enderchest', 'ENDER_CHEST')]:
                command(menu)
                assertion('menu', kind)
            assertion('removed-posture')
            # Preserve basic 26.3 movement and position-bearing teleport ACK coverage.
            before_look = len(client.teleports)
            client.look(33.0, 12.0)
            assertion('rotation', 33.0, 12.0)
            if len(client.teleports) != before_look:
                raise AssertionError('Look-only packet unexpectedly caused a position correction')
            result['checks'].append('look-only-accepted-without-position-correction')
            client.move(9.0, 96., 8.5)
            assertion('position', 9.0, 96., 8.5)
            result['checks'].append('ordinary-position-movement-accepted')
            before_teleport = len(client.teleports)
            client.teleport_offset_once = (.25, 0., 0.)
            assertion('teleport', 9.0, 96., 8.5)
            until(lambda: len(client.teleports) > before_teleport, 'native teleport packet and ack')
            assertion('position', 9.25, 96., 8.5)
            result['checks'].append('position-bearing-teleport-ack-accepted-without-posture-lock')
            client.move(8.5, 96., 8.5)
            assertion('position', 8.5, 96., 8.5)
            for cmd, tick in [('day', 1000), ('noon', 6000), ('night', 13000)]:
                command(cmd)
                assertion('time', tick)
            command('rain')
            assertion('weather', 'true')
            command('sun')
            assertion('weather', 'false')

            payload_check('neoforge:recipe_content', 'neoforge-recipe-content-includes-custom', lambda data: b'deerfoliaplus:upgrade_shaped' in data)
            serializers = ['crafting_shaped', 'crafting_shapeless', 'smelting', 'blasting', 'smoking', 'campfire_cooking', 'stonecutting']
            client.payload('fabric:recipe_sync/supported_serializers', varint(len(serializers)) + b''.join(string('minecraft:' + s) for s in serializers))
            payload_check('fabric:recipe_sync', 'fabric-supported-serializers-reply-includes-custom', lambda data: b'deerfoliaplus:upgrade_shaped' in data)
            payload_check('syncmatica:main', 'syncmatica-version-handshake-start', lambda data: b'syncmatica:register_version' in data)
            client.payload('syncmatica:main', string('syncmatica:register_version') + string('0.3.11-pre'))
            payload_check('syncmatica:main', 'syncmatica-version-response-advances-features', lambda data: b'syncmatica:feature' in data)
            client.payload('syncmatica:main', string('syncmatica:feature') + string(''))
            payload_check('syncmatica:main', 'syncmatica-feature-handshake-confirmed', lambda data: b'syncmatica:confirm_user' in data)
            payload_check('servux:structures', 'servux-structure-metadata', lambda data: readvar(data)[0] == 1)
            client.payload('servux:structures', varint(11) + b'\x0a\x00')
            payload_check('servux:structures', 'servux-spawn-metadata-request-reply', lambda data: readvar(data)[0] == 10)

            command('bot create UpgradeBot')
            marker('UpgradeBot[Local] logged in')
            assertion('bot-created')
            command('bot action UpgradeBot sneak')
            assertion('bot-action')
            bot_id = int(re.findall(r'PLUS_PASS bot-entity-id-(\d+)', content())[-1])
            # Different explicit attack/interact components prove correct 26.3
            # animation choice, hand, duration and real watcher packet delivery.
            for action, hand, animation, duration in [('use', 0, 1, 4), ('use_offhand', 1, 2, 3), ('use_to', 0, 1, 4), ('use_to_offhand', 1, 2, 3), ('attack', 0, 2, 9), ('use_on', 0, 1, 4), ('use_on_offhand', 1, 2, 3), ('break', 0, 2, 9)]:
                if action in ['use_on', 'use_on_offhand', 'break']:
                    assertion('bot-face-down')
                before_swing = len(client.swings)
                command('bot action UpgradeBot ' + action + ' 0 1')
                expected = [bot_id, hand, animation, duration]
                until(lambda: expected in client.swings[before_swing:], 'bot animation ' + action, 20)
                command('bot action UpgradeBot stop all')
                result['checks'].append('bot-animation-' + action + '-' + str(expected))
            assertion('bot-knockback')
            command('bot save UpgradeBot')
            assertion('bot-saved')
            before_load = len(content())
            command('bot load UpgradeBot')
            until(lambda: 'UpgradeBot[Local] logged in' in content()[before_load:], 'explicit bot load')
            assertion('bot-restored')
            marker('PLUS_PASS inherited-path-complete')
            marker('PLUS_PASS inherited-sleep-complete')
        result['status'] = 'passed'
    except Exception:
        result['error'] = traceback.format_exc()
        print(result['error'], flush=True)
    finally:
        if process.poll() is None:
            console('stop')
            try:
                process.wait(timeout=60)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=15)
        if client:
            client.close()
            result['packets'] = dict(client.counts)
            result['swing_packets'] = client.swings
            result['payloads'] = {name: len(data) for name, data in client.payloads.items()}
        stream.close()
        data = logfile.read_text()
        result['exit_code'] = process.returncode
        result['plugin_checks'] = re.findall(r'PLUS_PASS ([^\n]+)', data)
        result['errors_in_log'] = [line for line in data.splitlines() if re.search(r'\b(ERROR|SEVERE)\b|Exception|PLUS_FAIL', line)]
        if result['exit_code'] or result['errors_in_log']:
            result['status'] = 'failed'
        logfile.with_suffix('.json').write_text(json.dumps(result, indent=2))
        print(json.dumps(result, indent=2), flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, default=ROOT / 'DeerFoliaPlus-server/build/libs/DeerFoliaPlus-paperclip-26.3-R0.1-SNAPSHOT.jar')
    parser.add_argument('--tag', default=time.strftime('%Y%m%d-%H%M%S'))
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    run = OUT / 'runs' / args.tag
    prepare(run, args.jar.resolve())
    relay = None
    relay_stream = None
    try:
        try:
            with socket.create_connection(('127.0.0.1', 25576), timeout=1):
                pass
        except OSError:
            relay_stream = (OUT / 'relay.log').open('w')
            relay = subprocess.Popen([sys.executable, str(HERE / 'services_proxy.py')], stdout=relay_stream, stderr=subprocess.STDOUT)
            time.sleep(.3)
        legacy_config=(run / 'config/deer-folia-plus.yml').read_text()
        if 'posture:\n  enabled: true' not in legacy_config:
            raise AssertionError('Missing legacy enabled-posture fixture')
        results = [execute(run, args.jar.resolve())]
        results[0]['legacy_enabled_posture_fixture']=True
        if results[0]['status'] == 'passed':
            results.append(execute(run, args.jar.resolve(), restart=True))
        if len(results)==2 and all(result['status']=='passed' for result in results):
            defaults_run=OUT / 'runs' / (args.tag+'-defaults')
            prepare(defaults_run,args.jar.resolve(),defaults=True)
            results.append(execute(defaults_run,args.jar.resolve(),defaults=True))
        summary = {'status': 'passed' if len(results) == 3 and all(result['status'] == 'passed' for result in results) else 'failed', 'run_directory': str(run), 'results': results}
        (OUT / 'summary.json').write_text(json.dumps(summary, indent=2))
        return 0 if summary['status'] == 'passed' else 1
    finally:
        if relay:
            relay.terminate()
            relay.wait(timeout=10)
        if relay_stream:
            relay_stream.close()


if __name__ == '__main__':
    sys.exit(main())
