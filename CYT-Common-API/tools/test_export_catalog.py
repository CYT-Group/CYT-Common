import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from export_catalog import load_catalog


class CatalogExportTest(unittest.TestCase):
    def tearDown(self):
        load_catalog.cache_clear()

    def test_missing_build_explains_how_to_compile_the_catalogue(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, 'mvn package'):
                load_catalog(Path(directory))

    def test_export_uses_the_jar_without_starting_spring_and_is_cached(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'target').mkdir()
            (root / 'target/common-api-fixture.jar').touch()
            document = {'contract': {'credentials': []}}
            completed = subprocess.CompletedProcess([], 0, json.dumps(document).encode(), b'')
            with patch('export_catalog.shutil.which', return_value='java'), patch('export_catalog.subprocess.run', return_value=completed) as run:
                self.assertEqual(document, load_catalog(root))
                self.assertEqual(document, load_catalog(root))
            run.assert_called_once()
            self.assertEqual('--export-catalog', run.call_args.args[0][-1])


if __name__ == '__main__':
    unittest.main()
