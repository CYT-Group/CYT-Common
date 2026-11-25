import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).parents[1] / 'jenkins-build.sh'
GIT = shutil.which('git')
BASH = shutil.which('bash')
if not BASH and GIT:
    bundled = Path(GIT).parents[1] / 'bin/bash.exe'
    if bundled.is_file():
        BASH = str(bundled)


@unittest.skipUnless(GIT and BASH, '需要 Git 和 Bash')
class JenkinsBuildTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='cyt-common-jenkins-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.branch = 'feature/team/分支验收'
        self.git('init', '-b', self.branch)
        self.git('-c', 'user.name=CI fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '--allow-empty', '-qm', 'metadata fixture')
        self.sha = self.git('rev-parse', 'HEAD').strip()
        deploy = self.root / 'deploy/test-server'
        deploy.mkdir(parents=True)
        (deploy / 'ci-build.sh').write_text('''#!/usr/bin/env bash
# Common release protocol: 1
set -Eeuo pipefail
printf '%s\n%s\n%s\n%s\n' "$VERIFY_ONLY" "$SKIP_DEPLOY" "$IMAGE_TAG" "$COMMON_JAVA_IMAGE" > entrypoint-arguments
case "${VERIFY_ONLY:-false}" in true|TRUE|1|yes|YES) exit 0 ;; esac
echo build >> stages
case "${SKIP_DEPLOY:-false}" in true|TRUE|1|yes|YES) exit 0 ;; esac
echo migrate >> stages
[[ "${FIXTURE_MIGRATION_EXIT:-0}" == 0 ]] || exit "$FIXTURE_MIGRATION_EXIT"
echo deploy >> stages
''', encoding='utf-8')
        for entry in ['migrate-database.py', 'db-upgrade.sh']:
            (deploy / entry).write_text('# release fixture\n', encoding='utf-8')
        resources = self.root / 'CYT-Common-API/src/main/resources'
        resources.mkdir(parents=True)
        (resources / 'application-prod.yml').write_text('spring:\n  flyway:\n    enabled: false\n', encoding='utf-8')
        # Windows 的 python3 可能是 Store 别名；固定使用当前测试解释器。
        self.bin = self.root / 'test-bin'
        self.bin.mkdir()
        python3 = self.bin / 'python3'
        python3.write_text('#!/bin/sh\nexec ' + shlex.quote(Path(sys.executable).as_posix()) + ' "$@"\n', encoding='utf-8')
        python3.chmod(0o755)

    def git(self, *args):
        return subprocess.check_output([GIT, *args], cwd=self.root, text=True, encoding='utf-8', stderr=subprocess.DEVNULL)

    def run_build(self, **extra):
        env = os.environ.copy()
        env.update(WORKSPACE=str(self.root), BRANCH=self.branch, GIT_COMMIT=self.sha,
                   BUILD_NUMBER='42', SKIP_DEPLOY='true', VERIFY_ONLY='true',
                   PYTHONIOENCODING='utf-8', PYTHONUTF8='1')
        env['PATH'] = str(self.bin) + os.pathsep + env['PATH']
        env.update(extra)
        return subprocess.run([BASH, str(SCRIPT)], cwd=self.root, env=env,
                              text=True, encoding='utf-8', stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    def test_verify_only_keeps_unicode_branch_and_never_builds_or_migrates(self):
        result = self.run_build()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse((self.root / 'stages').exists())
        metadata = json.loads((self.root / '.jenkins-build/42/build-info.json').read_text(encoding='utf-8'))
        self.assertEqual(self.branch, metadata['branch'])
        self.assertEqual(self.sha, metadata['commit'])
        self.assertTrue(metadata['verifyOnly'])
        self.assertIn('%2F', metadata['branchUrl'])
        self.assertEqual(f'build-42-{self.sha[:12]}', metadata['imageTag'])
        self.assertTrue(metadata['image'].endswith(':' + metadata['imageTag']))

    def test_wrong_checkout_commit_stops_before_entrypoint(self):
        result = self.run_build(GIT_COMMIT='0' * 40, VERIFY_ONLY='false')
        self.assertEqual(3, result.returncode)
        self.assertFalse((self.root / 'entrypoint-arguments').exists())

    def test_wrong_branch_stops_before_entrypoint(self):
        result = self.run_build(BRANCH='other/branch', VERIFY_ONLY='false')
        self.assertEqual(3, result.returncode)
        self.assertFalse((self.root / 'entrypoint-arguments').exists())

    def test_missing_scm_commit_stops_before_entrypoint(self):
        result = self.run_build(GIT_COMMIT='')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / 'entrypoint-arguments').exists())

    def test_skip_deploy_passes_original_flag_and_immutable_image_to_entrypoint(self):
        result = self.run_build(VERIFY_ONLY='false', SKIP_DEPLOY='true', REGISTRY='registry.example.invalid/team')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(['build'], (self.root / 'stages').read_text().splitlines())
        args = (self.root / 'entrypoint-arguments').read_text().splitlines()
        self.assertEqual(['false', 'true'], args[:2])
        self.assertEqual(f'registry.example.invalid/team/cyt-common-java:build-42-{self.sha[:12]}', args[3])

    def test_full_deployment_delegates_stage_order_exactly_once(self):
        result = self.run_build(VERIFY_ONLY='false', SKIP_DEPLOY='false')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(['build', 'migrate', 'deploy'], (self.root / 'stages').read_text().splitlines())

    def test_failed_migration_propagates_and_prevents_application_update(self):
        result = self.run_build(VERIFY_ONLY='false', SKIP_DEPLOY='false', FIXTURE_MIGRATION_EXIT='42')
        self.assertEqual(42, result.returncode)
        self.assertEqual(['build', 'migrate'], (self.root / 'stages').read_text().splitlines())

    def test_build_artifacts_and_image_tags_are_isolated_by_build_number(self):
        first = self.run_build(BUILD_NUMBER='42')
        second = self.run_build(BUILD_NUMBER='43')
        self.assertEqual(0, first.returncode, first.stderr)
        self.assertEqual(0, second.returncode, second.stderr)
        data = [json.loads((self.root / f'.jenkins-build/{n}/build-info.json').read_text(encoding='utf-8')) for n in (42, 43)]
        self.assertNotEqual(data[0]['imageTag'], data[1]['imageTag'])

    def test_invalid_build_number_cannot_escape_artifact_directory(self):
        result = self.run_build(BUILD_NUMBER='../previous')
        self.assertEqual(2, result.returncode)
        self.assertFalse((self.root / '.jenkins-build').exists())

    def test_old_branch_without_migration_entrypoint_stops_before_build(self):
        (self.root / 'deploy/test-server/db-upgrade.sh').unlink()
        result = self.run_build(VERIFY_ONLY='false', SKIP_DEPLOY='false')
        self.assertEqual(4, result.returncode)
        self.assertFalse((self.root / 'entrypoint-arguments').exists())
        self.assertFalse((self.root / 'stages').exists())

    def test_old_branch_with_files_but_no_release_protocol_stops_before_build(self):
        entry = self.root / 'deploy/test-server/ci-build.sh'
        entry.write_text(entry.read_text(encoding='utf-8').replace('# Common release protocol: 1\n', ''), encoding='utf-8')
        result = self.run_build(VERIFY_ONLY='false', SKIP_DEPLOY='false')
        self.assertEqual(4, result.returncode)
        self.assertFalse((self.root / 'entrypoint-arguments').exists())


if __name__ == '__main__':
    unittest.main()
