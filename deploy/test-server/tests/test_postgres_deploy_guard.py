"""Exercise the real shell entry point with local command fixtures, never Docker."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
BASH = shutil.which("bash") or (
    "D:/Git/bin/bash.exe" if Path("D:/Git/bin/bash.exe").is_file() else None
)


def shell_path(path):
    value = Path(path).resolve().as_posix()
    return (
        "/" + value[0].lower() + value[2:]
        if len(value) > 2 and value[1] == ":"
        else value
    )


@unittest.skipUnless(BASH, "Bash is required for deployment control-flow tests")
class PostgresDeploymentGuardTests(unittest.TestCase):
    def run_deploy(self, postgres, config_ok=True, ci=False, skip_deploy=False):
        with tempfile.TemporaryDirectory(prefix="common-pg-deploy-") as folder:
            base = Path(folder)
            source = base / "source"
            compose = base / "compose"
            commands = base / "bin"
            (source / "deploy/test-server").mkdir(parents=True)
            compose.mkdir()
            commands.mkdir()
            shutil.copyfile(
                ROOT / "deploy/test-server/docker-compose.yml",
                source / "deploy/test-server/docker-compose.yml",
            )
            for name in ("ci-build.sh", "build-on-server.sh", "redeploy.sh"):
                shutil.copyfile(
                    ROOT / "deploy/test-server" / name,
                    source / "deploy/test-server" / name,
                )
            (source / ".deploy-sha").write_text("a" * 40, encoding="utf-8")
            (source / "deploy/test-server/Dockerfile").write_text(
                "FROM scratch\n", encoding="utf-8"
            )
            (compose / "docker-compose.yml").write_text(
                "original-compose", encoding="utf-8"
            )
            (compose / ".env").write_text(
                "POSTGRES_PASSWORD=fixture-password\n"
                if postgres
                else "MYSQL_PASSWORD=fixture-password\n",
                encoding="utf-8",
            )
            fixtures = {
                "docker": '#!/usr/bin/env bash\nprintf "%s\\n" "$*" >> "$COMMAND_LOG"\nif [[ "$*" == *"config --quiet"* && "$CONFIG_OK" != "true" ]]; then exit 1; fi\nexit 0\n',
                "id": "#!/usr/bin/env bash\necho 0\n",
                "curl": '#!/usr/bin/env bash\necho \'{"ok":true,"database":"postgresql"}\'\n',
            }
            for name, content in fixtures.items():
                p = commands / name
                p.write_text(content, encoding="utf-8", newline="\n")
                p.chmod(0o755)
            environment = {
                **os.environ,
                "FIXTURE_PATH": shell_path(commands),
                "COMPOSE_DIR": shell_path(compose),
                "COMMAND_LOG": shell_path(base / "commands.log"),
                "CONFIG_OK": str(config_ok).lower(),
                "WAIT_SECONDS": "1",
                "SKIP_DEPLOY": "true" if skip_deploy else "false",
            }
            # Git Bash prepends its own bin directory during startup. Set PATH
            # inside that shell so the isolated id/docker/curl fixtures win.
            result = subprocess.run(
                [
                    BASH,
                    "-c",
                    'export PATH="$FIXTURE_PATH:/usr/bin:/bin"; source "$1" "$2"',
                    "deploy-test",
                    shell_path(
                        source
                        / "deploy/test-server"
                        / ("ci-build.sh" if ci else "redeploy.sh")
                    ),
                    shell_path(source),
                ],
                env=environment,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                timeout=20,
            )
            log = (
                (base / "commands.log").read_text(encoding="utf-8")
                if (base / "commands.log").exists()
                else ""
            )
            final_compose = (compose / "docker-compose.yml").read_text(encoding="utf-8")
            return result, log, final_compose

    def test_mysql_environment_is_rejected_before_replacing_composition(self):
        result, log, composition = self.run_deploy(False)
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertEqual("original-compose", composition)
        self.assertEqual("", log)

    def test_invalid_postgres_config_does_not_replace_composition_or_start_containers(
        self,
    ):
        result, log, composition = self.run_deploy(True, config_ok=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("config --quiet", log)
        self.assertNotIn("up -d", log)
        self.assertEqual("original-compose", composition)

    def test_valid_postgres_config_proceeds_to_the_existing_deployment_flow(self):
        result, log, composition = self.run_deploy(True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("up -d", log)
        self.assertIn("jdbc:postgresql://db:5432/", composition)
        self.assertIn("common_java_postgres:", composition)

    def test_jenkins_boolean_skip_builds_versioned_candidate_without_deploying(self):
        result, log, composition = self.run_deploy(True, ci=True, skip_deploy=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("cyt-common-java:sha-aaaaaaaaaaaa", log)
        self.assertIn("build ", log)
        self.assertNotIn("compose ", log)
        self.assertEqual("original-compose", composition)

    def test_ci_false_skip_reaches_deployment_after_versioned_build(self):
        result, log, composition = self.run_deploy(True, ci=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("cyt-common-java:sha-aaaaaaaaaaaa", log)
        self.assertIn("up -d", log)
        self.assertIn("CORS_ALLOW_ORIGINS:", composition)
