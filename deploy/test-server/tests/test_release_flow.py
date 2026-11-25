"""Exercise the real Bash release entry points using only local fake executables."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


REPOSITORY = Path(__file__).resolve().parents[3]
BASH = shutil.which('bash')
if not BASH:
    for candidate in [Path('D:/Git/bin/bash.exe'), Path('C:/Program Files/Git/bin/bash.exe')]:
        if candidate.is_file():
            BASH = str(candidate)
            break


@unittest.skipUnless(BASH, 'Bash is required; all Docker, curl and migration calls are faked')
class ReleaseFlowTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='common-release-flow-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / 'source checkout'
        self.deploy = self.source / 'deploy/test-server'
        self.deploy.mkdir(parents=True)
        for name in ['ci-build.sh', 'redeploy.sh', 'build-on-server.sh', 'publish-jenkins-base-images.sh',
                     'db-init.sh', 'db-upgrade.sh', 'migrate-database.py', 'docker-compose.yml', 'Dockerfile']:
            shutil.copyfile(REPOSITORY / 'deploy/test-server' / name, self.deploy / name)
        (self.deploy / 'jenkins').mkdir()
        for name in ['ci-base-images.Dockerfile', 'maven-settings.xml']:
            shutil.copyfile(REPOSITORY / 'deploy/test-server/jenkins' / name, self.deploy / 'jenkins' / name)
        # Network checks are intercepted by the fake Python executable below.
        (self.deploy / 'verify-frontend.py').write_text('# public verifier fixture\n', encoding='utf-8')
        self.sha = '0123456789abcdef0123456789abcdef01234567'
        (self.source / '.deploy-sha').write_text(self.sha + '\n', encoding='utf-8')
        self.compose = self.root / 'compose state'
        self.compose.mkdir()
        self.environment_file = self.compose / '.env'
        self.original_environment = (
            'COMMON_JAVA_IMAGE=registry.invalid/common:old\n'
            'APP_PORT=8088\n'
            'POSTGRES_DATABASE=cyt_common\n'
            'POSTGRES_USERNAME=fixture\n'
            'POSTGRES_PASSWORD=fixture-db-password\n'
            'ADMIN_PASSWORD=fixture-admin-password\n'
            'ADMIN_JWT_SECRET=fixture-existing-jwt\n'
            'CREDENTIAL_MASTER_KEY=fixture-existing-master\n'
        )
        self.environment_file.write_text(self.original_environment, encoding='utf-8', newline='\n')
        self.old_compose = b'# existing deployment configuration must survive failed preflight\n'
        (self.compose / 'docker-compose.yml').write_bytes(self.old_compose)
        self.bin = self.root / 'fake bin'
        self.bin.mkdir()
        self.event_file = self.root / 'events.tsv'
        self.event_file.write_text('', encoding='utf-8')
        self.clock_file = self.root / 'clock'
        self.database_id = 'c' * 64
        self.env = dict(os.environ)
        for key in ['VERIFY_ONLY', 'SKIP_DEPLOY', 'NO_PUSH', 'IMAGE_TAG', 'COMMON_JAVA_IMAGE',
                    'REGISTRY', 'BASE_REGISTRY', 'NPM_REGISTRY', 'CI_BASE_UPSTREAM', 'APP_PORT', 'HEALTH_URL',
                    'SOURCE_SHA', 'BUILD_NUMBER', 'COMMON_FLYWAY_IMAGE', 'COMMON_DATABASE_CONTAINER',
                    'INITIAL_ADMIN_PASSWORD', 'PUBLIC_BASE_URL', 'CORS_ALLOW_ORIGINS']:
            self.env.pop(key, None)
        self.env.update({
            'RELEASE_TEST_BIN': self.bin.as_posix(),
            'RELEASE_TEST_EVENTS': self.event_file.as_posix(),
            'RELEASE_TEST_CLOCK': self.clock_file.as_posix(),
            'RELEASE_TEST_REAL_PYTHON': Path(sys.executable).as_posix(),
            'RELEASE_TEST_HEALTH_BODY': json.dumps({
                'ok': True, 'database': 'postgresql', 'service': 'cyt-common-api-java'}),
            'COMPOSE_DIR': self.compose.as_posix(),
            'WAIT_SECONDS': '1',
            'BUILD_NUMBER': '822',
            'REGISTRY': 'registry.invalid:5000',
            'RELEASE_TEST_DATABASE_ID': self.database_id,
        })
        self.executable('id', 'printf "%s\\n" "${RELEASE_TEST_UID:-0}"\n')
        self.executable('sudo', '''
# Mimic sudo discarding unapproved public Compose variables. Explicit `env KEY=...`
# arguments in the release script must restore them for the child command.
unset COMMON_JAVA_IMAGE APP_PORT COMPOSE_PROFILES COMMON_DATABASE_CONTAINER CORS_ALLOW_ORIGINS
exec "$@"
''')
        self.executable('sleep', 'exit 0\n')
        self.executable('openssl', '''
count="${3:-16}"
for ((i=0; i<count*2; i++)); do printf 'a'; done
printf '\\n'
''')
        self.executable('date', '''
if [[ "${1:-}" == '+%s' ]]; then
  counter=1700000000
  if [[ -f "$RELEASE_TEST_CLOCK" ]]; then read -r counter < "$RELEASE_TEST_CLOCK"; fi
  counter=$((counter + 1))
  printf '%s\\n' "$counter" > "$RELEASE_TEST_CLOCK"
  printf '%s\\n' "$counter"
else
  printf '20261112T000000Z\\n'
fi
''')
        self.executable('docker', '''
event="docker.other"
image="${COMMON_JAVA_IMAGE:-}"
case "${1:-}" in
  info) event='docker.info' ;;
  build)
    event='image.build'
    previous=''
    for value in "$@"; do
      if [[ "$previous" == '-t' || "$previous" == '--tag' ]]; then image="$value"; fi
      previous="$value"
    done
    ;;
  push) event='image.push'; image="${2:-}" ;;
  compose)
    if [[ "$*" == *' config -q' ]]; then event='compose.validate';
    elif [[ "$*" == *' config --environment' ]]; then event='compose.environment';
    elif [[ "$*" == *' up '* && "${@: -1}" == 'db' ]]; then event='database.wait';
    elif [[ "$*" == *' pull app' ]]; then event='application.pull';
    elif [[ "$*" == *' up '* && "${@: -1}" == 'app' ]]; then event='application.replace';
    elif [[ "$*" == *' ps -q db' ]]; then event='database.select';
    elif [[ "$*" == *' port app 8189' ]]; then event='application.port';
    else event='compose.unexpected'; fi
    ;;
  logs) event='application.logs' ;;
esac
printf '%s\\t%s\\t%s; APP_PORT=%s; COMPOSE_PROFILES=%s; CORS_ALLOW_ORIGINS=%s\\n' "$event" "$image" "$*" "${APP_PORT:-}" "${COMPOSE_PROFILES:-}" "${CORS_ALLOW_ORIGINS:-}" >> "$RELEASE_TEST_EVENTS"
if [[ "$event" == 'compose.unexpected' || "$event" == 'docker.other' ]]; then exit 94; fi
if [[ "$event" == 'database.select' ]]; then printf '%s\\n' "$RELEASE_TEST_DATABASE_ID"; fi
if [[ "$event" == 'application.port' ]]; then printf '%s\\n' "${RELEASE_TEST_PUBLISHED_BINDING:-0.0.0.0:${APP_PORT:-8088}}"; fi
if [[ "$event" == 'compose.environment' ]]; then
  printf 'POSTGRES_PASSWORD=fixture-hidden-from-public-check-log\\nPUBLIC_BASE_URL=%s\\n' "${RELEASE_TEST_PUBLIC_BASE_URL:-}"
fi
exit 0
''')
        self.executable('curl', '''
printf 'health.request\\t\\t%s\\n' "$*" >> "$RELEASE_TEST_EVENTS"
printf '%s' "$RELEASE_TEST_HEALTH_BODY"
''')
        self.executable('python3', '''
case "${1:-}" in
  *migrate-database.py)
    operation="${@: -1}"
    printf 'migration.%s\\t%s\\t%s; DATABASE_CONTAINER=%s\\n' "$operation" "${COMMON_JAVA_IMAGE:-}" "$*" "${COMMON_DATABASE_CONTAINER:-}" >> "$RELEASE_TEST_EVENTS"
    if [[ "$operation" == 'migrate' ]]; then exit "${RELEASE_TEST_MIGRATION_EXIT:-0}"; fi
    if [[ "$operation" != 'lint' ]]; then exit 95; fi
    ;;
  *verify-frontend.py)
    printf 'frontend.verify\\t%s\\t%s\\n' "${COMMON_JAVA_IMAGE:-}" "$*" >> "$RELEASE_TEST_EVENTS"
    exit "${RELEASE_TEST_FRONTEND_EXIT:-0}"
    ;;
  -c)
    if [[ "${2:-}" == *'PUBLIC_BASE_URL'* ]]; then
      printf 'frontend.configuration\\t\\t\\n' >> "$RELEASE_TEST_EVENTS"
    else
      printf 'health.validate\\t\\t\\n' >> "$RELEASE_TEST_EVENTS"
    fi
    exec "$RELEASE_TEST_REAL_PYTHON" "$@"
    ;;
  -) exec "$RELEASE_TEST_REAL_PYTHON" "$@" ;;
  *) exit 96 ;;
esac
''')

    def executable(self, name, body):
        path = self.bin / name
        path.write_text('#!/usr/bin/env bash\nset -Eeuo pipefail\n' + body,
                        encoding='utf-8', newline='\n')
        path.chmod(0o755)

    def run_release(self, entry='ci-build.sh', action=None, **environment):
        env = dict(self.env, **environment)
        args = [] if entry in ('ci-build.sh', 'publish-jenkins-base-images.sh') else [self.source.as_posix()]
        if action is not None:
            args.append(action)
        # Resolve the fake directory inside Bash so this also works with Git Bash on Windows.
        launcher = 'export PATH="$(cd -- "$RELEASE_TEST_BIN" && pwd):/usr/bin:/bin:$PATH"; exec bash "$@"'
        return subprocess.run([BASH, '--noprofile', '--norc', '-c', launcher, 'release-test',
                               (self.deploy / entry).as_posix(), *args],
                              env=env, capture_output=True, text=True, encoding='utf-8',
                              errors='replace', timeout=25)

    def events(self):
        return [line.split('\t', 2) for line in self.event_file.read_text(encoding='utf-8').splitlines()]

    def names(self):
        return [event[0] for event in self.events()]

    def success(self, result):
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_verify_only_true_does_not_build_push_migrate_or_deploy(self):
        self.success(self.run_release(VERIFY_ONLY='true'))
        self.assertEqual(['migration.lint'], self.names())
        self.assertEqual(self.original_environment, self.environment_file.read_text(encoding='utf-8'))
        self.assertEqual(self.old_compose, (self.compose / 'docker-compose.yml').read_bytes())

    def test_skip_deploy_true_builds_and_pushes_without_touching_database(self):
        base_registry = '192.168.1.182:5000/cyt-common-ci-r1/'
        npm_registry = 'https://registry.example.invalid/npm/'
        self.success(self.run_release(SKIP_DEPLOY='true', BASE_REGISTRY=base_registry, NPM_REGISTRY=npm_registry))
        self.assertEqual(['migration.lint', 'docker.info', 'image.build', 'image.push'], self.names())
        docker_build = next(event[2] for event in self.events() if event[0] == 'image.build')
        self.assertIn('--build-arg BASE_REGISTRY=' + base_registry, docker_build)
        self.assertIn('--build-arg NPM_REGISTRY=' + npm_registry, docker_build)
        self.assertEqual(self.original_environment, self.environment_file.read_text(encoding='utf-8'))
        self.assertEqual(self.old_compose, (self.compose / 'docker-compose.yml').read_bytes())

    def test_publishes_all_three_dedicated_base_images(self):
        self.success(self.run_release('publish-jenkins-base-images.sh'))
        stages = [('frontend', 'node:22-alpine'), ('backend', 'maven:3.9-eclipse-temurin-21'),
                  ('runtime', 'eclipse-temurin:21-jre-noble')]
        references = ['192.168.1.182:5000/cyt-common-ci-r1/' + tag for _, tag in stages]
        builds, pushes = self.events()[:3], self.events()[3:]
        self.assertEqual(['image.build'] * 3 + ['image.push'] * 3, self.names())
        self.assertEqual(references, [event[1] for event in builds])
        self.assertEqual(references, [event[1] for event in pushes])
        for (stage, _), (_, _, args) in zip(stages, builds):
            self.assertIn(f'--target {stage}', args)
            self.assertIn('jenkins/ci-base-images.Dockerfile', args)
            self.assertIn('--build-arg CI_BASE_UPSTREAM=docker.io/library/', args)

    def test_failed_migration_cannot_pull_or_replace_application(self):
        result = self.run_release(RELEASE_TEST_MIGRATION_EXIT='17')
        self.assertNotEqual(0, result.returncode)
        names = self.names()
        self.assertIn('image.push', names)
        self.assertIn('database.wait', names)
        self.assertIn('migration.migrate', names)
        self.assertNotIn('application.pull', names)
        self.assertNotIn('application.replace', names)
        self.assertNotIn('health.request', names)
        self.assertEqual(self.original_environment, self.environment_file.read_text(encoding='utf-8'))

    def test_successful_release_orders_build_migration_and_application_replacement(self):
        self.success(self.run_release(IMAGE_TAG='release-unique-822'))
        names = self.names()
        for before, after in [('migration.lint', 'image.build'), ('image.build', 'image.push'),
                              ('image.push', 'migration.migrate'), ('database.wait', 'migration.migrate'),
                              ('migration.migrate', 'application.pull'),
                              ('application.pull', 'application.replace'),
                              ('application.replace', 'health.validate')]:
            self.assertLess(names.index(before), names.index(after), (before, after, names))
        expected = 'registry.invalid:5000/cyt-common-java:release-unique-822'
        tagged = [event for event in self.events() if event[0] in {
            'image.build', 'image.push', 'migration.migrate', 'application.pull', 'application.replace'}]
        self.assertEqual(5, len(tagged))
        self.assertTrue(all(event[1] == expected for event in tagged), tagged)

    def test_ci_generated_image_tag_uses_the_same_source_and_build_number_everywhere(self):
        self.success(self.run_release())
        expected = 'registry.invalid:5000/cyt-common-java:git-' + self.sha[:12] + '-822'
        for event in self.events():
            if event[0] in {'image.build', 'image.push', 'application.pull', 'application.replace'}:
                self.assertEqual(expected, event[1], event)
        build = next(event for event in self.events() if event[0] == 'image.build')
        self.assertIn('org.opencontainers.image.revision=' + self.sha, build[2])

    def test_prepare_only_waits_for_database_without_migration_or_application_start(self):
        self.success(self.run_release('redeploy.sh', 'prepare'))
        self.assertEqual(['compose.validate', 'database.wait'], self.names())
        self.assertEqual(self.original_environment, self.environment_file.read_text(encoding='utf-8'))

    def test_existing_environment_is_preserved_and_never_executed_as_shell(self):
        marker = self.root / 'dotenv-was-executed'
        original = self.original_environment + 'UNUSED=$(touch "' + marker.as_posix() + '")\n'
        self.environment_file.write_text(original, encoding='utf-8', newline='\n')
        self.success(self.run_release())
        image = 'registry.invalid:5000/cyt-common-java:git-' + self.sha[:12] + '-822'
        expected = original.replace('COMMON_JAVA_IMAGE=registry.invalid/common:old',
                                    'COMMON_JAVA_IMAGE=' + image)
        self.assertEqual(expected, self.environment_file.read_text(encoding='utf-8'))
        self.assertFalse(marker.exists(), 'Compose .env must not execute shell substitutions')

    def test_nonroot_sudo_keeps_the_exact_image_port_and_optional_profile(self):
        self.success(self.run_release(RELEASE_TEST_UID='1000', APP_PORT='18088',
                                      COMPOSE_PROFILES='discord', IMAGE_TAG='release-nonroot-822'))
        image = 'registry.invalid:5000/cyt-common-java:release-nonroot-822'
        for event in self.events():
            if event[0] in {'image.build', 'image.push', 'application.pull', 'application.replace'}:
                self.assertEqual(image, event[1], event)
            if event[0] in {'compose.validate', 'database.wait', 'application.pull', 'application.replace'}:
                self.assertIn('APP_PORT=18088', event[2])
                self.assertIn('COMPOSE_PROFILES=discord', event[2])
        health = next(event for event in self.events() if event[0] == 'health.request')
        self.assertIn('http://127.0.0.1:18088/health', health[2])

    def test_application_receives_exact_cors_allowlist_even_through_sudo(self):
        allowlist = 'https://cytcommon-test.lrbelec.top,http://localhost:8190'
        self.success(self.run_release(RELEASE_TEST_UID='1000', CORS_ALLOW_ORIGINS=allowlist))
        update = next(event for event in self.events() if event[0] == 'application.replace')
        self.assertIn('CORS_ALLOW_ORIGINS=' + allowlist, update[2])
        compose = (REPOSITORY / 'deploy/test-server/docker-compose.yml').read_text(encoding='utf-8')
        application = compose.split('\n  app:\n', 1)[1].split('\nvolumes:', 1)[0]
        setting = next(line.strip() for line in application.splitlines() if line.strip().startswith('CORS_ALLOW_ORIGINS:'))
        self.assertEqual('CORS_ALLOW_ORIGINS: "${CORS_ALLOW_ORIGINS:-http://localhost:8190,http://127.0.0.1:8190}"', setting)
        self.assertNotIn('*', setting)

    def test_environment_example_names_public_origin_and_keeps_verification_optional(self):
        example = (REPOSITORY / 'deploy/test-server/.env.example').read_text(encoding='utf-8')
        values = dict(line.split('=', 1) for line in example.splitlines() if line and not line.startswith('#') and '=' in line)
        self.assertEqual('', values['PUBLIC_BASE_URL'])
        self.assertIn('https://cytcommon-test.lrbelec.top', values['CORS_ALLOW_ORIGINS'].split(','))
        self.assertNotIn('*', values['CORS_ALLOW_ORIGINS'])
        self.assertIn('# 例如 PUBLIC_BASE_URL=https://cytcommon-test.lrbelec.top', example)

    def test_unconfigured_public_url_skips_frontend_verifier(self):
        self.success(self.run_release())
        self.assertNotIn('frontend.verify', self.names())

    def test_public_frontend_is_checked_after_health_and_before_recording_image(self):
        public_url = 'https://cytcommon-test.lrbelec.top'
        self.success(self.run_release(RELEASE_TEST_PUBLIC_BASE_URL=public_url))
        verifier = next(event for event in self.events() if event[0] == 'frontend.verify')
        self.assertIn('--base-url ' + public_url, verifier[2])
        self.assertIn('--timeout 10', verifier[2])
        self.assertLess(self.names().index('health.validate'), self.names().index('frontend.verify'))
        expected_image = 'registry.invalid:5000/cyt-common-java:git-' + self.sha[:12] + '-822'
        self.assertIn('COMMON_JAVA_IMAGE=' + expected_image, self.environment_file.read_text(encoding='utf-8'))

    def test_shell_public_url_takes_precedence_over_compose_environment(self):
        public_url = 'https://shell-public.example.invalid'
        self.success(self.run_release(PUBLIC_BASE_URL=public_url, RELEASE_TEST_PUBLIC_BASE_URL='https://file-public.example.invalid'))
        verifier = next(event for event in self.events() if event[0] == 'frontend.verify')
        self.assertIn('--base-url ' + public_url, verifier[2])
        self.assertNotIn('https://file-public.example.invalid', verifier[2])

    def test_failed_public_asset_verification_fails_release_without_recording_image(self):
        result = self.run_release(RELEASE_TEST_PUBLIC_BASE_URL='https://cytcommon-test.lrbelec.top', RELEASE_TEST_FRONTEND_EXIT='7')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('frontend.verify', self.names())
        self.assertEqual(self.original_environment, self.environment_file.read_text(encoding='utf-8'))
        self.assertNotIn('fixture-hidden-from-public-check-log', result.stdout + result.stderr)

    def test_public_verifier_does_not_run_for_verify_only_or_skip_deploy(self):
        for switch in ['VERIFY_ONLY', 'SKIP_DEPLOY']:
            with self.subTest(switch=switch):
                self.event_file.write_text('', encoding='utf-8')
                self.success(self.run_release(**{switch: 'true', 'PUBLIC_BASE_URL': 'https://cytcommon-test.lrbelec.top'}))
                self.assertNotIn('frontend.verify', self.names())

    def test_dotenv_port_quotes_and_inline_comments_use_actual_compose_binding(self):
        for declaration, port in [
            ('8088 # Common public port', 8088),
            ("'18089' # quoted port", 18089),
            ('"18090" # quoted port', 18090),
        ]:
            with self.subTest(declaration=declaration):
                original = self.original_environment.replace('APP_PORT=8088', 'APP_PORT=' + declaration)
                self.environment_file.write_text(original, encoding='utf-8', newline='\n')
                self.event_file.write_text('', encoding='utf-8')
                self.success(self.run_release(RELEASE_TEST_PUBLISHED_BINDING=f'0.0.0.0:{port}'))
                health = next(event for event in self.events() if event[0] == 'health.request')
                self.assertIn(f'http://127.0.0.1:{port}/health', health[2])
                self.assertIn('APP_PORT=' + declaration, self.environment_file.read_text(encoding='utf-8'))
                self.assertLess(self.names().index('application.replace'), self.names().index('application.port'))

    def test_ipv6_only_compose_binding_uses_ipv6_loopback(self):
        self.success(self.run_release(RELEASE_TEST_PUBLISHED_BINDING='[::]:18091'))
        health = next(event for event in self.events() if event[0] == 'health.request')
        self.assertIn('http://[::1]:18091/health', health[2])

    def test_invalid_published_binding_cannot_report_deployment_success(self):
        result = self.run_release(RELEASE_TEST_PUBLISHED_BINDING='not-a-port-mapping')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('health.request', self.names())
        self.assertEqual(self.original_environment, self.environment_file.read_text(encoding='utf-8'))

    def test_release_uses_actual_compose_database_id_instead_of_stale_override(self):
        self.success(self.run_release(COMMON_DATABASE_CONTAINER='another-project-database'))
        migration = next(event for event in self.events() if event[0] == 'migration.migrate')
        self.assertIn('--database-container ' + self.database_id, migration[2])
        self.assertIn('DATABASE_CONTAINER=' + self.database_id, migration[2])
        self.assertNotIn('another-project-database', migration[2])
        self.assertLess(self.names().index('database.select'), self.names().index('migration.migrate'))

    def test_initialization_uses_actual_compose_database_id_instead_of_stale_override(self):
        self.success(self.run_release('db-init.sh', COMMON_DATABASE_CONTAINER='another-project-database', RELEASE_TEST_UID='1000'))
        migration = next(event for event in self.events() if event[0] == 'migration.migrate')
        self.assertIn('--database-container ' + self.database_id, migration[2])
        self.assertIn('DATABASE_CONTAINER=' + self.database_id, migration[2])
        self.assertNotIn('another-project-database', migration[2])
        self.assertNotIn('application.replace', self.names())

    def test_release_and_initialization_stop_if_compose_database_id_is_ambiguous(self):
        for entry in ['redeploy.sh', 'db-init.sh']:
            with self.subTest(entry=entry):
                self.event_file.write_text('', encoding='utf-8')
                result = self.run_release(entry, RELEASE_TEST_DATABASE_ID='')
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn('migration.migrate', self.names())
                self.assertNotIn('application.replace', self.names())

    def test_initial_password_dollar_quotes_and_backslashes_are_preserved_without_argv(self):
        for password in ["dollar$secret'with#hash", 'trailing\\', "slash\\'quote$", 'double"quote\\tab\\t$']:
            with self.subTest(password=password):
                if self.environment_file.exists():
                    self.environment_file.unlink()
                self.event_file.write_text('', encoding='utf-8')
                result = self.run_release('redeploy.sh', 'prepare', INITIAL_ADMIN_PASSWORD=password)
                self.success(result)
                contents = self.environment_file.read_text(encoding='utf-8')
                encoded = next(line.split('=', 1)[1] for line in contents.splitlines() if line.startswith('ADMIN_PASSWORD='))
                if encoded.startswith('"'):
                    actual = json.loads(encoded).replace('$$', '$')
                else:
                    self.assertTrue(encoded.startswith("'") and encoded.endswith("'"))
                    actual = encoded[1:-1].replace("\\'", "'")
                self.assertEqual(password, actual)
                self.assertNotIn(password, result.stdout + result.stderr + self.event_file.read_text(encoding='utf-8'))
                self.assertEqual(1, sum(line.startswith('POSTGRES_DATABASE=') for line in contents.splitlines()))
                if os.name != 'nt':
                    self.assertEqual(0o600, self.environment_file.stat().st_mode & 0o777)

    def test_multiline_initial_password_is_rejected_before_installing_environment(self):
        for password in ['fixture\nPOSTGRES_DATABASE=wrong', 'fixture\rpassword']:
            with self.subTest(control=repr(password)):
                if self.environment_file.exists():
                    self.environment_file.unlink()
                self.event_file.write_text('', encoding='utf-8')
                result = self.run_release('redeploy.sh', 'prepare', INITIAL_ADMIN_PASSWORD=password)
                self.assertNotEqual(0, result.returncode)
                self.assertFalse(self.environment_file.exists())
                self.assertEqual([], self.names())

    def test_initial_environment_writer_rejects_nul_even_before_os_process_validation(self):
        # Operating systems already reject NUL in environment values. Exercise the
        # embedded writer directly as well so this validation cannot regress.
        script = (self.deploy / 'redeploy.sh').read_text(encoding='utf-8')
        writer = script.split("<<'PY_INIT_ENV'\n", 1)[1].split('\nPY_INIT_ENV', 1)[0]
        target = self.root / 'nul-password.env'
        with patch.object(os, 'environ', {'INITIAL_ADMIN_PASSWORD': 'fixture\0password'}), \
                patch.object(sys, 'argv', ['writer', str(target)]), self.assertRaises(SystemExit):
            exec(compile(writer, 'initial-environment-writer', 'exec'), {})
        self.assertFalse(target.exists())

    def test_first_prepare_creates_configuration_without_starting_application(self):
        self.environment_file.unlink()
        self.success(self.run_release('redeploy.sh', 'prepare'))
        contents = self.environment_file.read_text(encoding='utf-8')
        self.assertIn('POSTGRES_PASSWORD=', contents)
        self.assertIn('JAVA_SEED_ENABLED=false', contents)
        self.assertEqual(['compose.validate', 'database.wait'], self.names())

    def test_legacy_mysql_environment_is_rejected_before_compose_sync(self):
        original = 'MYSQL_PASSWORD=fixture-old-mysql\nADMIN_PASSWORD=fixture-existing\n'
        self.environment_file.write_text(original, encoding='utf-8', newline='\n')
        result = self.run_release('redeploy.sh')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], self.names())
        self.assertEqual(self.old_compose, (self.compose / 'docker-compose.yml').read_bytes())
        self.assertEqual(original, self.environment_file.read_text(encoding='utf-8'))

    def test_old_mysql_health_response_cannot_pass_postgresql_release_check(self):
        body = json.dumps({'ok': True, 'database': 'mysql', 'service': 'cyt-common-api-java'})
        result = self.run_release(RELEASE_TEST_HEALTH_BODY=body)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('application.replace', self.names())
        self.assertIn('health.validate', self.names())
        self.assertIn('application.logs', self.names())
        self.assertEqual(self.original_environment, self.environment_file.read_text(encoding='utf-8'))

    def test_no_push_without_skip_deploy_is_rejected_before_any_side_effect(self):
        result = self.run_release(NO_PUSH='true')
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], self.names())


if __name__ == '__main__':
    unittest.main()
