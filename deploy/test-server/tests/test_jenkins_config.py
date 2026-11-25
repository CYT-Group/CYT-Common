import contextlib
import importlib.util
import io
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET


PATH = Path(__file__).parents[1] / 'configure-jenkins.py'
SPEC = importlib.util.spec_from_file_location('common_jenkins_config', PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
SOURCE = b'''<project><description>old</description><displayName>Existing Common job</displayName>
<properties><hudson.model.ParametersDefinitionProperty><parameterDefinitions>
<hudson.model.ChoiceParameterDefinition><name>BRANCH</name><choices><string>main</string></choices></hudson.model.ChoiceParameterDefinition>
<hudson.model.BooleanParameterDefinition><name>SKIP_DEPLOY</name><defaultValue>false</defaultValue></hudson.model.BooleanParameterDefinition>
</parameterDefinitions></hudson.model.ParametersDefinitionProperty></properties>
<scm class="hudson.scm.NullSCM"/><concurrentBuild>true</concurrentBuild>
<builders><hudson.tasks.Shell><command>old download script</command></hudson.tasks.Shell></builders>
<publishers><hudson.tasks.JUnitResultArchiver/></publishers><buildWrappers/>
</project>'''


class JenkinsConfigurationTests(unittest.TestCase):
    def configured(self):
        return ET.fromstring(MODULE.job_config(SOURCE))

    def test_dynamic_branch_tracks_common_repository_and_nested_names(self):
        root = self.configured()
        definition = root.find('.//net.uaznia.lukanus.hudson.plugins.gitparameter.GitParameterDefinition')
        self.assertEqual('PT_BRANCH', definition.findtext('type'))
        self.assertEqual('main', definition.findtext('defaultValue'))
        self.assertTrue(re.fullmatch(definition.findtext('useRepository'), MODULE.REPOSITORY))
        self.assertEqual('feature/team/change', re.fullmatch(definition.findtext('branchFilter'), 'origin/feature/team/change').group(1))
        self.assertEqual('cyt-common-github', root.findtext('scm/userRemoteConfigs/hudson.plugins.git.UserRemoteConfig/credentialsId'))

    def test_native_scm_changes_and_local_branch_match_builded_source(self):
        root = self.configured()
        self.assertEqual('hudson.plugins.git.GitSCM', root.find('scm').get('class'))
        self.assertEqual('*/${BRANCH}', root.findtext('scm/branches/hudson.plugins.git.BranchSpec/name'))
        self.assertEqual('${BRANCH}', root.findtext('.//hudson.plugins.git.extensions.impl.LocalBranch/localBranch'))
        self.assertEqual('true', root.findtext('.//hudson.plugins.git.extensions.impl.FirstBuildChangelog/makeChangelog'))
        self.assertEqual('false', root.findtext('.//hudson.plugins.git.extensions.impl.CloneOption/shallow'))
        self.assertNotIn('git-hub.sh', root.findtext('builders/hudson.tasks.Shell/command'))
        self.assertIn('/opt/cyt-common/deploy/jenkins-build.sh', root.findtext('builders/hudson.tasks.Shell/command'))

    def test_preserves_publishers_and_archives_only_current_build_metadata(self):
        root = self.configured()
        self.assertEqual(MODULE.JOB_DISPLAY_NAME, root.findtext('displayName'))
        self.assertEqual(['BRANCH', 'SKIP_DEPLOY', 'VERIFY_ONLY'], [n.findtext('name') for n in root.find('.//parameterDefinitions')])
        self.assertIsNotNone(root.find('publishers/hudson.tasks.JUnitResultArchiver'))
        self.assertEqual('.jenkins-build/${BUILD_NUMBER}/*', root.findtext('.//hudson.tasks.ArtifactArchiver/artifacts'))
        self.assertEqual('true', root.findtext('.//hudson.tasks.ArtifactArchiver/allowEmptyArchive'))
        self.assertEqual('false', root.findtext('concurrentBuild'))
        self.assertIn('GIT_REVISION', root.findtext('.//org.jenkinsci.plugins.buildnamesetter.BuildNameSetter/template'))
        self.assertNotIn('TARGET', ET.tostring(root, encoding='unicode'))

    def test_configuration_is_repeatable(self):
        first = MODULE.job_config(SOURCE)
        self.assertEqual(first, MODULE.job_config(first))

    def test_unknown_jobs_and_custom_build_steps_are_not_overwritten(self):
        with self.assertRaises(ValueError):
            MODULE.job_config(SOURCE, 'cyt-cs-deploy')
        root = ET.fromstring(SOURCE)
        ET.SubElement(root.find('builders'), 'custom.build.Step')
        with self.assertRaises(ValueError):
            MODULE.job_config(ET.tostring(root))

    def test_offline_generation_never_connects_to_jenkins(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'original.xml'
            path.write_bytes(SOURCE)
            with patch.object(MODULE, 'Jenkins') as client, contextlib.redirect_stdout(io.StringIO()) as output:
                MODULE.main(['--input-xml', str(path)])
            client.assert_not_called()
            self.assertEqual('project', ET.fromstring(output.getvalue()).tag)

    def live_client(self):
        client = Mock()
        client.json.side_effect = [
            {'plugins': [{'shortName': name, 'active': True} for name in MODULE.PLUGINS]},
            {'builds': [], 'inQueue': False},
        ]
        client.request.return_value = SOURCE
        client.groovy.return_value = 'Common Git credential ready'
        return client

    def test_apply_backs_up_original_before_updates_and_only_uses_common_token(self):
        with tempfile.TemporaryDirectory() as directory:
            client = self.live_client()
            def create_credential(script):
                backups = list(Path(directory).glob('*.xml'))
                self.assertEqual(1, len(backups))
                self.assertEqual(SOURCE, backups[0].read_bytes())
                self.assertIn("new File('/opt/jenkins/github_token_common')", script)
                self.assertNotIn("new File('/opt/jenkins/github_token')", script)
                return 'Common Git credential ready'
            client.groovy.side_effect = create_credential
            with patch.object(MODULE, 'Jenkins', return_value=client), contextlib.redirect_stdout(io.StringIO()):
                MODULE.main(['--auth-file', 'fixture.json', '--backup-dir', directory, '--apply'])
            writes = [call for call in client.request.call_args_list if len(call.args) > 1]
            self.assertEqual(1, len(writes))
            self.assertEqual('/job/cyt-common-deploy/config.xml', writes[0].args[0])
            self.assertEqual('application/xml; charset=UTF-8', writes[0].args[2])

    def test_apply_refuses_queued_or_running_jobs(self):
        for job in ({'inQueue': True, 'builds': []}, {'builds': [{'building': True}]}):
            client = self.live_client()
            client.json.side_effect = [
                {'plugins': [{'shortName': name, 'active': True} for name in MODULE.PLUGINS]}, job]
            with patch.object(MODULE, 'Jenkins', return_value=client), self.assertRaises(SystemExit):
                MODULE.main(['--auth-file', 'fixture.json', '--apply'])
            client.groovy.assert_not_called()
            self.assertTrue(all(len(call.args) == 1 for call in client.request.call_args_list))

    def test_apply_does_not_overwrite_concurrent_configuration_change(self):
        client = self.live_client()
        client.request.side_effect = [SOURCE, SOURCE + b'\n']
        with tempfile.TemporaryDirectory() as directory, patch.object(MODULE, 'Jenkins', return_value=client), self.assertRaises(SystemExit):
            MODULE.main(['--auth-file', 'fixture.json', '--backup-dir', directory, '--apply'])
        self.assertTrue(all(len(call.args) == 1 for call in client.request.call_args_list))

    def test_checked_in_template_has_same_configuration(self):
        template = PATH.parent / 'jenkins/job-deploy.xml'
        existing = ET.fromstring(template.read_bytes())
        self.assertEqual('hudson.plugins.git.GitSCM', existing.find('scm').get('class'))
        self.assertEqual(MODULE.job_config(template.read_bytes()), template.read_bytes().strip())


if __name__ == '__main__':
    unittest.main()
