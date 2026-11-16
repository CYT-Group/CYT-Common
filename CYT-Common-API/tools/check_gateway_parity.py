"""Compare executable public operation contracts without reading credentials or databases.

python scripts/check_gateway_parity.py --java-root D:/code/CYT-Common
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import math
import sys
from pathlib import Path
from typing import get_args

FIELDS = (
    "key",
    "platform",
    "quota",
    "sensitive",
    "idempotent",
    "retry_max",
    "timeout_s",
    "deprecated",
    "tags",
)


def normalize(schema, root=None):
    """Resolve local refs and equivalent nullable/integer-bound schema notation.

    Descriptions and property order do not change accepted inputs. Optional null
    defaults are omitted; all non-null defaults and validation constraints stay.
    """
    root = schema if root is None else root
    if isinstance(schema, list):
        return [normalize(x, root) for x in schema]
    if not isinstance(schema, dict):
        return schema
    if "$ref" in schema:
        node = root
        for part in schema["$ref"].removeprefix("#/").split("/"):
            node = node[part.replace("~1", "/").replace("~0", "~")]
        return normalize(node, root)
    result = {
        k: (
            {name: normalize(prop, root) for name, prop in v.items()}
            if k in {"properties", "patternProperties"}
            else normalize(v, root)
        )
        for k, v in schema.items()
        if k not in {"title", "description", "examples", "$defs"}
        and not (k == "default" and v is None)
    }
    if isinstance(result.get("type"), list):
        kinds = result.pop("type")
        extra = {k: v for k, v in result.items() if k != "default"}
        result = {
            "anyOf": [
                {"type": kind, **(extra if kind != "null" else {})} for kind in kinds
            ],
            **({"default": result["default"]} if "default" in result else {}),
        }
    if result.get("type") == "integer" and "exclusiveMinimum" in result:
        result["minimum"] = math.floor(result.pop("exclusiveMinimum")) + 1
    if result.get("type") == "integer" and "exclusiveMaximum" in result:
        result["maximum"] = math.ceil(result.pop("exclusiveMaximum")) - 1
    if "required" in result:
        if result["required"]:
            result["required"] = sorted(result["required"])
        else:
            result.pop("required")
    if "anyOf" in result:
        result["anyOf"] = sorted(
            result["anyOf"], key=lambda x: json.dumps(x, sort_keys=True)
        )
    return result


def factory_defaults(model, schema):
    from pydantic import BaseModel

    visited = set()

    def visit(current, node):
        if current in visited:
            return
        visited.add(current)
        for name, field in current.model_fields.items():
            prop = node.get("properties", {}).get(name)
            if prop is None:
                continue
            if field.default_factory in (list, dict):
                prop.setdefault("default", field.default_factory())

            def nested(annotation):
                if isinstance(annotation, type) and issubclass(annotation, BaseModel):
                    definition = schema.get("$defs", {}).get(annotation.__name__)
                    if definition:
                        visit(annotation, definition)
                for argument in get_args(annotation):
                    nested(argument)

            nested(field.annotation)

    visit(model, schema)
    return schema


def signature(row):
    schema = json.dumps(
        normalize(row["schema"]),
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return {
        **{k: row[k] for k in FIELDS},
        "path": row["path"],
        "schema_sha256": hashlib.sha256(schema.encode()).hexdigest(),
    }


def python_rows(root):
    sys.path.insert(0, str(root))
    from gateway.registry import REGISTRY

    return {
        key: {
            **{name: getattr(op, name) for name in FIELDS},
            "tags": list(op.tags),
            "path": "/integrations/" + key.replace(".", "/").replace("_", "-"),
            "schema": factory_defaults(op.params_model, copy.deepcopy(op.schema())),
        }
        for key, op in REGISTRY.items()
    }


def java_rows(root):
    resources = root / "CYT-Common-API/src/main/resources"
    contracts = [
        json.loads((resources / name).read_text(encoding="utf-8"))
        for name in ("contract.json", "business-contract.json", "discord-contract.json")
    ]
    rows = {op["key"]: op for contract in contracts for op in contract["operations"]}
    for extension in contracts[1]["native_extensions"]:
        schema = rows[extension["key"]]["schema"]
        schema["properties"].update(extension.get("properties", {}))
        schema.setdefault("$defs", {}).update(extension.get("definitions", {}))
    return rows


def compare(python, java):
    errors = []
    for label, keys in [
        ("Python only", python.keys() - java.keys()),
        ("Java only", java.keys() - python.keys()),
    ]:
        if keys:
            errors.append(label + ": " + ", ".join(sorted(keys)))
    for key in sorted(python.keys() & java.keys()):
        a, b = signature(python[key]), signature(java[key])
        for field in a:
            if a[field] != b[field]:
                errors.append(f"{key}: {field} differs")
        if a["schema_sha256"] != b["schema_sha256"]:
            left, right = (
                normalize(python[key]["schema"]),
                normalize(java[key]["schema"]),
            )

            def diff(x, y, path):
                if isinstance(x, dict) and isinstance(y, dict):
                    for name in sorted(x.keys() | y.keys()):
                        if name not in x or name not in y:
                            errors.append(
                                f"  {path}.{name}: Python={x.get(name)!r} Java={y.get(name)!r}"
                            )
                        elif x[name] != y[name]:
                            diff(x[name], y[name], path + "." + name)
                else:
                    errors.append(f"  {path}: Python={x!r} Java={y!r}")

            diff(left, right, "schema")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--python-root", type=Path, default=Path(__file__).resolve().parents[1]
    )
    parser.add_argument("--java-root", type=Path, required=True)
    parser.add_argument("--write-snapshot", type=Path)
    args = parser.parse_args()
    python, java = python_rows(args.python_root), java_rows(args.java_root)
    errors = compare(python, java)
    if errors:
        print("\n".join(errors))
        return 1
    print(
        f"{len(python)} operations: names, public paths, schemas, quotas, flags and retry settings match"
    )
    if args.write_snapshot:
        rows = [signature(python[key]) for key in sorted(python)]
        args.write_snapshot.write_text(
            json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
