"""Export compiled Java catalogue metadata without starting Spring or connecting to a database."""
import argparse
from functools import lru_cache
import json
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


@lru_cache(maxsize=4)
def load_catalog(root=ROOT):
    jars = sorted((Path(root) / 'target').glob('common-api-*.jar'), key=lambda p: p.stat().st_mtime, reverse=True)
    java = shutil.which('java')
    if not java or not jars:
        raise RuntimeError('Build the Java catalogue first with mvn package -DskipTests; Java 21 is required.')
    result = subprocess.run([java, '-jar', str(jars[0]), '--export-catalog'], capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError('Java catalogue export failed; rebuild with mvn package -DskipTests.')
    document = json.loads(result.stdout.decode('utf-8'))
    if not isinstance(document.get('contract', {}).get('credentials'), list):
        raise RuntimeError('The JAR does not contain a supported Java catalogue export.')
    return document


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, help='Optional generated snapshot; never the catalogue source.')
    args = parser.parse_args()
    try:
        output = json.dumps(load_catalog(), ensure_ascii=False, indent=2) + '\n'
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(output, encoding='utf-8')
        else:
            sys.stdout.reconfigure(encoding='utf-8')
            print(output, end='')
    except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
