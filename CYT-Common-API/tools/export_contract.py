"""Development-only contract snapshot. No database access or credential values."""
import json
import sys
from dataclasses import asdict
from pathlib import Path
from typing import get_args

from pydantic import BaseModel

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path('D:/code/cyt-common-api')
sys.path.insert(0, str(ROOT))
from gateway import registry, models, naming, credentials_spec
from gateway.seed import SYSTEMS, GRANTS


def referenced_models(annotation):
    """Unwrap Optional/list/dict annotations down to the BaseModel classes they carry."""
    args = get_args(annotation)
    if args:
        found = []
        for arg in args:
            found.extend(referenced_models(arg))
        return found
    return [annotation] if isinstance(annotation, type) and issubclass(annotation, BaseModel) else []


def inject_factory_defaults(model, schema):
    """Pydantic omits default_factory values from JSON Schema, so a nested list field would
    arrive at the upstream without the [] the original gateway always sent. Walk every
    reachable model, not just the top level."""
    defs = schema.get('$defs', {})
    pending = [(model, schema)]
    seen = set()
    while pending:
        current_model, current_schema = pending.pop()
        if id(current_model) in seen:
            continue
        seen.add(id(current_model))
        properties = current_schema.get('properties', {})
        for name, field in current_model.model_fields.items():
            prop = properties.get(name)
            if prop is None:
                continue
            if 'default' not in prop and field.default_factory in (list, dict):
                prop['default'] = field.default_factory()
            for nested in referenced_models(field.annotation):
                definition = next(
                    (node for key, node in defs.items() if key == nested.__name__ or key.endswith('.' + nested.__name__)),
                    None,
                )
                if definition and 'properties' in definition:
                    pending.append((nested, definition))


DEST = Path(__file__).resolve().parents[1] / 'src/main/resources'
DEST.mkdir(parents=True, exist_ok=True)
ops = []
for op in registry.all_operations():
    d = {k: getattr(op, k) for k in ('key', 'platform', 'summary', 'quota', 'sensitive', 'idempotent', 'timeout_s', 'retry_max', 'since', 'deprecated', 'description', 'tags', 'cost', 'category', 'sort_order')}
    d['schema'] = op.schema()
    inject_factory_defaults(op.params_model, d['schema'])
    d['path'] = naming.operation_path(op.key)
    ops.append(d)
platforms = [{'platform': a.platform, 'display_name': a.display_name, 'platform_quota': a.platform_quota} for a in registry.PLATFORMS.values() if a.platform != 'custom']
contract = {'operations': ops, 'platforms': platforms, 'aliases': naming.LEGACY_OPERATION_ALIASES,
            'credentials': [asdict(c) for c in credentials_spec.PLATFORM_CREDS],
            'systems': [{'key': k, 'display_name': n, 'description': d} for k, n, d in SYSTEMS],
            'grants': [{'system': s, 'operation_pattern': naming.canonical_operation_key(p), 'quota': q, 'note': n} for s, p, q, n in GRANTS]}
(DEST / 'contract.json').write_text(json.dumps(contract, ensure_ascii=False, indent=2), encoding='utf-8')
from gateway.adapters import shopify
queries = {name: value for name, value in vars(shopify).items() if name.startswith('_') and name.isupper() and isinstance(value, str) and ('QUERY' in name or 'MUTATION' in name)}
(DEST / 'shopify-queries.json').write_text(json.dumps(queries, ensure_ascii=False, indent=2), encoding='utf-8')

# Flyway migrations are maintained independently; metadata exports never rewrite them.
print(f'Exported {len(ops)} operations, {len(platforms)} platforms; no migrations or secrets exported.')
