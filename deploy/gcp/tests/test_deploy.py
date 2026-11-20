"""Cloud 发布计划及失败边界验证；不调用云端或业务数据库。"""
from contextlib import redirect_stdout
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import yaml


ROOT = Path(__file__).resolve().parents[3]
DEPLOY = ROOT / 'deploy/gcp'
SPEC = importlib.util.spec_from_file_location('common_gcp_deploy', DEPLOY / 'deploy.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def argv(*extra):
    return [
        '--service-account', 'common@cyt-digital.iam.gserviceaccount.com',
        '--db-url-secret', 'COMMON_JAVA_POSTGRES_URL:3', '--db-user-secret', 'COMMON_JAVA_POSTGRES_USER:2',
        '--db-password-secret', 'COMMON_JAVA_POSTGRES_PASSWORD:4',
        '--admin-password-secret', 'COMMON_API-ADMIN_PASSWORD:2',
        '--admin-jwt-secret', 'COMMON_API-ADMIN_JWT_SECRET:2',
        '--credential-master-key-secret', 'COMMON_API-CREDENTIAL_MASTER_KEY:2', *extra,
    ]


def arguments(*extra):
    return MODULE.parser().parse_args(argv(*extra))


def decode_dictionary(value):
    delimiter, payload = value[1:].split('^', 1)
    return dict(item.split('=', 1) for item in payload.split(delimiter))


class DeploymentArgumentsTests(unittest.TestCase):
    def test_same_database_and_gateway_secrets_reach_migration_and_runtime(self):
        values = MODULE.substitutions(arguments('--public'))
        migration = decode_dictionary(values['_MIGRATION_SECRETS'])
        runtime = decode_dictionary(values['_RUNTIME_SECRETS'])
        for before, after in [('FLYWAY_URL', 'SPRING_DATASOURCE_URL'), ('FLYWAY_USER', 'SPRING_DATASOURCE_USERNAME'),
                              ('FLYWAY_PASSWORD', 'SPRING_DATASOURCE_PASSWORD')]:
            self.assertEqual(migration[before], runtime[after])
        for name in ['ADMIN_PASSWORD', 'ADMIN_JWT_SECRET', 'CREDENTIAL_MASTER_KEY']:
            self.assertTrue(MODULE.SECRET_REF.fullmatch(runtime[name]))
        self.assertNotIn('REDIS_URL', runtime)
        environment = decode_dictionary(values['_RUNTIME_ENV'])
        self.assertEqual('prod', environment['SPRING_PROFILES_ACTIVE'])
        self.assertEqual('production', environment['ENV'])
        for name in ['SPRING_FLYWAY_ENABLED', 'MOCK_ALL', 'GATEWAY_DEV_MODE', 'JAVA_SEED_ENABLED', 'ADMIN_DEV_LOGIN']:
            self.assertEqual('false', environment[name])
        self.assertEqual('--allow-unauthenticated', values['_AUTH_FLAG'])
        self.assertEqual('--no-allow-unauthenticated', MODULE.substitutions(arguments())['_AUTH_FLAG'])
        self.assertIn('SPRING_DATASOURCE_PASSWORD', values['_REMOVE_ENV_VARS'].split(','))
        for alias in ['POSTGRES_URL', 'POSTGRES_USERNAME', 'POSTGRES_PASSWORD']:
            self.assertIn(alias, values['_REMOVE_ENV_VARS'].split(','))
            self.assertIn(alias, values['_REMOVE_SECRET_VARS'].split(','))

    def test_nested_dictionaries_preserve_commas_json_and_unicode(self):
        values = MODULE.substitutions(arguments(
            '--env', 'CORS_ALLOW_ORIGINS=https://a.example,https://b.example',
            '--env', 'RELEASE_DESCRIPTION=中文|发布',
            '--env', 'GATEWAY_SA_SYSTEM_MAP={"cs@example.com":"cs","oa@example.com":"oa"}',
            '--env', 'REQUEST_TIMEOUT=300', '--secret', 'WEWORK_PROXY_TOKEN=WEWORK_PROXY_TOKEN:2'))
        recovered = decode_dictionary(MODULE.dictionary_flag(values))
        self.assertEqual(values, recovered)
        environment = decode_dictionary(recovered['_RUNTIME_ENV'])
        self.assertEqual('https://a.example,https://b.example', environment['CORS_ALLOW_ORIGINS'])
        self.assertEqual('中文|发布', environment['RELEASE_DESCRIPTION'])
        self.assertEqual({'cs@example.com': 'cs', 'oa@example.com': 'oa'}, json.loads(environment['GATEWAY_SA_SYSTEM_MAP']))

    def test_existing_python_service_is_protected_without_blocking_java_names(self):
        with self.assertRaisesRegex(ValueError, 'Python'):
            MODULE.substitutions(arguments('--service', 'cyt-common-api'))
        for service in ['cyt-common-java', 'cyt-common-api-java', 'common-api-java-prod']:
            self.assertEqual(service, MODULE.substitutions(arguments('--service', service))['_SERVICE'])

    def test_rejects_plaintext_secrets_managed_overrides_and_invalid_options(self):
        cases = [('--db-password-secret', 'plain-text'), ('--admin-jwt-secret', 'plain-text'),
                 ('--env', 'ADMIN_PASSWORD=plain-text'), ('--env', 'CREDENTIAL_MASTER_KEY=plain-text'),
                 ('--env', 'WEWORK_PROXY_TOKEN=plain-text'), ('--env', 'GMAIL_MOCK=true'),
                 ('--env', 'SPRING_PROFILES_ACTIVE=local'), ('--env', 'SPRING_FLYWAY_ENABLED=true'),
                 ('--env', 'ENV=local'), ('--env', 'PORT=8189'), ('--env', 'SPRING_DATASOURCE_URL=other'),
                 ('--env', 'SPRING_APPLICATION_JSON={"spring":{"profiles":{"active":"local"}}}'),
                 ('--secret', 'POSTGRES_PASSWORD=OTHER:1'), ('--env', 'X=line\nnext'),
                 ('--secret', 'WEWORK_PROXY_TOKEN=WEWORK_PROXY_TOKEN'), ('--env', 'X=one', '--env', 'X=two'),
                 ('--env', 'X=value', '--secret', 'X=SECRET:1'), ('--max-instances', '0')]
        for case in cases:
            with self.subTest(case=case), self.assertRaises(ValueError):
                MODULE.substitutions(arguments(*case))

    def test_cloud_build_substitution_size_is_checked_before_submission(self):
        with self.assertRaisesRegex(ValueError, '4000'):
            MODULE.substitutions(arguments('--env', 'LONG_VALUE=' + '中' * 1500))

    def test_large_binding_manifest_passes_only_its_path_to_cloud_build(self):
        manifest = 'deploy/gcp/unit-bindings.json'
        references = {f'CUSTOM_VENDOR_{i}_TOKEN': f'common-prod-vendor-token-{i}:7'
                      for i in range(150)}
        original = Path.read_text
        def read_text(path, *args, **kwargs):
            if path.name == 'unit-bindings.json':
                return json.dumps(references)
            return original(path, *args, **kwargs)
        self.assertGreater(len(json.dumps(references)), 4000)
        with patch.object(Path, 'read_text', read_text):
            self.assertEqual(references, MODULE.load_secret_bindings(manifest))
            values = MODULE.substitutions(arguments('--secret-bindings-file', manifest))
            self.assertEqual(manifest, values['_SECRET_BINDINGS_FILE'])
            self.assertNotIn('CUSTOM_VENDOR_149_TOKEN', values['_RUNTIME_SECRETS'])
            with self.assertRaises(ValueError):
                MODULE.substitutions(arguments('--secret-bindings-file', manifest,
                    '--secret', 'CUSTOM_VENDOR_149_TOKEN=another:8'))

    def test_credential_catalog_rejects_literal_quickcep_and_custom_secrets(self):
        for name in ['QUICKCEP_SHOP_ACCESS_ID', 'CUSTOM_VENDOR', 'GOOGLE_APPLICATION_CREDENTIALS']:
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'Secret Manager'):
                MODULE.substitutions(arguments('--env', name + '=fixture'))

    def test_redis_is_optional_but_required_for_more_than_one_instance(self):
        with self.assertRaisesRegex(ValueError, 'redis-secret'):
            MODULE.substitutions(arguments('--max-instances', '2'))
        values = MODULE.substitutions(arguments('--max-instances', '2', '--redis-secret', 'REDIS_URL:7'))
        self.assertEqual('REDIS_URL:7', decode_dictionary(values['_RUNTIME_SECRETS'])['REDIS_URL'])
        self.assertEqual('2', values['_MAX_INSTANCES'])

    def test_discord_listener_has_an_always_running_instance_and_cpu(self):
        values = MODULE.substitutions(arguments('--discord-listener'))
        self.assertEqual('true', decode_dictionary(values['_RUNTIME_ENV'])['DISCORD_GATEWAY_ENABLED'])
        self.assertEqual('1', values['_MIN_INSTANCES'])
        self.assertEqual('--no-cpu-throttling', values['_CPU_FLAG'])
        regular = MODULE.substitutions(arguments())
        self.assertEqual('false', decode_dictionary(regular['_RUNTIME_ENV'])['DISCORD_GATEWAY_ENABLED'])
        self.assertEqual('0', regular['_MIN_INSTANCES'])

    def test_separate_accounts_and_unique_job_names_fit_cloud_limits(self):
        values = MODULE.substitutions(arguments('--service', 'common-' + 'x' * 42,
            '--migration-service-account', 'migrate@cyt-digital.iam.gserviceaccount.com'))
        self.assertEqual('migrate@cyt-digital.iam.gserviceaccount.com', values['_MIGRATION_SA'])
        self.assertNotEqual(values['_RUNTIME_SA'], values['_MIGRATION_SA'])
        self.assertLessEqual(len(values['_JOB_PREFIX']) + 1 + 36, 63)

    def test_dry_run_never_resolves_gcloud_or_starts_a_subprocess(self):
        with patch.object(MODULE, 'gcloud_command') as cli, patch.object(MODULE.subprocess, 'run') as execute:
            with redirect_stdout(io.StringIO()) as output:
                self.assertEqual(0, MODULE.main(argv('--dry-run')))
            cli.assert_not_called()
            execute.assert_not_called()
            self.assertRegex(output.getvalue(), r'SQL versions: [1-9][0-9]*')
            self.assertIn('Dry run complete', output.getvalue())

    def test_submit_propagates_failure_and_never_uses_a_shell(self):
        with patch.object(MODULE, 'gcloud_command', return_value=['python', 'gcloud.py']):
            with patch.object(MODULE.subprocess, 'run', return_value=subprocess.CompletedProcess([], 17)) as execute:
                with redirect_stdout(io.StringIO()):
                    self.assertEqual(17, MODULE.main(argv('--env', 'CORS_ALLOW_ORIGINS=https://a,https://b')))
                command = execute.call_args.args[0]
                self.assertEqual(['python', 'gcloud.py', 'builds', 'submit'], command[:4])
                self.assertNotIn('shell', execute.call_args.kwargs)
                self.assertIn('--config=' + str(DEPLOY / 'cloudbuild-common.yaml'), command)
                self.assertIn('--ignore-file=' + str(DEPLOY / '.gcloudignore'), command)
                encoded = next(value.split('=', 1)[1] for value in command if value.startswith('--substitutions='))
                self.assertEqual('https://a,https://b', decode_dictionary(decode_dictionary(encoded)['_RUNTIME_ENV'])['CORS_ALLOW_ORIGINS'])

    @unittest.skipUnless(os.name == 'nt', 'Windows SDK entrypoint')
    def test_windows_sdk_uses_python_entrypoint_instead_of_shell_wrapper(self):
        with tempfile.TemporaryDirectory() as temporary:
            sdk = Path(temporary) / 'Cloud SDK'
            (sdk / 'bin').mkdir(parents=True)
            (sdk / 'lib').mkdir()
            (sdk / 'lib/gcloud.py').write_text('', encoding='utf-8')
            with patch.object(MODULE.shutil, 'which', return_value=str(sdk / 'bin/gcloud.cmd')):
                command = MODULE.gcloud_command()
            self.assertEqual(str(sdk / 'lib/gcloud.py'), command[1])
            self.assertFalse(command[0].endswith('.cmd'))


class CloudBuildContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = yaml.safe_load((DEPLOY / 'cloudbuild-common.yaml').read_text(encoding='utf-8'))
        cls.steps = {step['id']: step for step in cls.config['steps']}

    def test_migration_success_is_required_before_application_update(self):
        sequence = list(self.steps)
        self.assertLess(sequence.index('push-api'), sequence.index('configure-migration-job'))
        self.assertLess(sequence.index('push-migrations'), sequence.index('configure-migration-job'))
        self.assertLess(sequence.index('configure-migration-job'), sequence.index('migrate'))
        self.assertLess(sequence.index('migrate'), sequence.index('deploy-api'))
        self.assertIn('--wait', self.steps['migrate']['args'])
        self.assertIn('--max-retries=0', self.steps['configure-migration-job']['args'])
        for step in self.steps.values():
            self.assertFalse({'allowFailure', 'allowExitCodes', 'waitFor'} & step.keys())
        self.assertNotIn('availableSecrets', self.config)

    def test_build_scoped_versions_keep_api_sql_and_jobs_together(self):
        self.assertIn('${_JOB_PREFIX}-$BUILD_ID', self.steps['configure-migration-job']['args'])
        self.assertIn('${_JOB_PREFIX}-$BUILD_ID', self.steps['migrate']['args'])
        self.assertIn('--image=${_REGISTRY}/cyt-common-migrate:$BUILD_ID', self.steps['configure-migration-job']['args'])
        self.assertIn('--image=${_REGISTRY}/cyt-common-java:$BUILD_ID', self.steps['deploy-api']['args'])
        self.assertIn('--port=8080', self.steps['deploy-api']['args'])

    def test_runtime_updates_preserve_unrelated_service_configuration(self):
        args = self.steps['deploy-api']['args']
        self.assertIn('--update-secrets=${_RUNTIME_SECRETS}', args)
        self.assertIn('--update-env-vars=${_RUNTIME_ENV}', args)
        self.assertFalse(any(arg.startswith(('--set-env-vars', '--set-secrets', '--clear-')) for arg in args))
        self.assertIn('--remove-env-vars=${_REMOVE_ENV_VARS}', args)
        self.assertIn('--remove-secrets=${_REMOVE_SECRET_VARS}', args)

    def test_all_custom_substitutions_are_provided_by_the_launcher(self):
        template = (DEPLOY / 'cloudbuild-common.yaml').read_text(encoding='utf-8')
        declared = set(re.findall(r'\$\{(_[A-Z0-9_]+)\}', template))
        self.assertEqual(declared, set(MODULE.substitutions(arguments())))

    def test_cloud_flyway_matches_the_test_server_policy(self):
        policy = runpy.run_path(str(ROOT / 'deploy/test-server/migrate-database.py'))
        dockerfile = (DEPLOY / 'Dockerfile.migrate').read_text(encoding='utf-8')
        command = json.loads(next(line[4:] for line in dockerfile.splitlines() if line.startswith('CMD ')))
        local = policy['flyway_command']('fixture', ROOT, 'flyway:test', 'migrate')
        self.assertEqual(set(command), set(local[local.index('-locations=filesystem:/flyway/sql'):]))
        self.assertIn('FROM ' + policy['FLYWAY_IMAGE'], dockerfile)
        self.assertIn('COPY CYT-Common-API/src/main/resources/db/migration/', dockerfile)

    def test_cloud_image_is_same_origin_and_excludes_local_credentials_and_backups(self):
        config = yaml.safe_load((DEPLOY / 'application-prod.yml').read_text(encoding='utf-8'))
        self.assertEqual('${SPRING_DATASOURCE_PASSWORD}', config['spring']['datasource']['password'])
        self.assertEqual('${PORT:8080}', config['server']['port'])
        self.assertFalse(config['spring']['flyway']['enabled'])
        self.assertFalse(config['GATEWAY_DEV_MODE'])
        dockerfile = (DEPLOY / 'Dockerfile.api').read_text(encoding='utf-8')
        self.assertIn('pnpm install --frozen-lockfile', dockerfile)
        self.assertIn('COPY --from=frontend /build/frontend/dist ./src/main/resources/web', dockerfile)
        self.assertIn('COPY sdk/ /build/sdk/', dockerfile)
        self.assertIn('COPY deploy/gcp/application-prod.yml ./src/main/resources/application-prod.yml', dockerfile)
        self.assertIn('USER app', dockerfile)
        for filename in ['.gcloudignore', 'Dockerfile.api.dockerignore', 'Dockerfile.migrate.dockerignore']:
            with self.subTest(filename=filename):
                policy = (DEPLOY / filename).read_text(encoding='utf-8').splitlines()
                for entry in ['work', '**/application-local.properties', '**/.env', '**/.env.*',
                              '**/application-local.yml', '**/application-prod.yml', '!deploy/gcp/application-prod.yml']:
                    self.assertIn(entry, policy)


if __name__ == '__main__':
    unittest.main()
