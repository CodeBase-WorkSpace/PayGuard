"""Convert authorized offline Identity JSON using a persistent approved ID mapping."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import uuid


def identifier(value, prefix, mapping):
    if not isinstance(value, str):
        raise ValueError("Identifier must be an exact string")
    if re.fullmatch(prefix + r"_[A-Za-z0-9]{22}", value):
        return value
    converted = mapping.get(value)
    if not isinstance(converted, str) or not re.fullmatch(prefix + r"_[A-Za-z0-9]{22}", converted):
        raise ValueError("Approved historical identifier mapping required")
    return converted


def timestamp(value):
    if value is None:
        return None
    if not isinstance(value, str) or not re.search(r"(?:Z|[+-]\d{2}:\d{2})$", value):
        raise ValueError("Timestamp offset required")
    value = re.sub(r"(\.\d{6})\d+", r"\1", value)
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc).isoformat(timespec="microseconds")


def convert(source, mapping, mapping_bytes):
    if not mapping.get("approved_by") or not mapping.get("approval_reference"):
        raise ValueError("Approved provenance required")
    customers, challenges = mapping.get("customers", {}), mapping.get("challenges", {})
    result = {"challenges": [], "audit": []}
    identities = {}
    for original in source["challenges"]:
        row = dict(original)
        row["id"] = identifier(row["id"], "otp", challenges)
        row["user_id"] = identifier(row["user_id"], "cus", customers)
        if row["id"] in identities:
            raise ValueError("Duplicate converted challenge")
        identities[row["id"]] = row["user_id"]
        for field in ("created_at", "expires_at", "consumed_at", "retention_until"):
            row[field] = timestamp(row.get(field))
        for field in ("attempts", "max_attempts"):
            if type(row.get(field)) is not int:
                raise ValueError("Integer attempts required")
        result["challenges"].append(row)
    audit_ids = set()
    for original in source["audit"]:
        row = dict(original)
        row["id"] = str(uuid.UUID(row["id"]))
        row["challenge_id"] = identifier(row["challenge_id"], "otp", challenges)
        row["user_id"] = identifier(row["user_id"], "cus", customers)
        if row["id"] in audit_ids or identities.get(row["challenge_id"]) != row["user_id"]:
            raise ValueError("Duplicate or orphan/mismatched audit reference")
        audit_ids.add(row["id"])
        row["occurred_at"] = timestamp(row["occurred_at"])
        result["audit"].append(row)
    result["manifest"] = {"approved_by": mapping["approved_by"], "approval_reference": mapping["approval_reference"],
                          "mapping_sha256": hashlib.sha256(mapping_bytes).hexdigest(),
                          "challenge_count": len(result["challenges"]), "audit_count": len(result["audit"]),
                          "timestamp_policy": "UTC microseconds; truncate sub-microsecond precision"}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("mapping", type=Path)
    parser.add_argument("destination", type=Path)
    arguments = parser.parse_args()
    try:
        mapping_bytes = arguments.mapping.read_bytes()
        converted = convert(json.loads(arguments.source.read_text()), json.loads(mapping_bytes), mapping_bytes)
        descriptor = os.open(arguments.destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w") as output:
            json.dump(converted, output, indent=2)
        print("Protected export converted with approved mapping and reconciliation manifest")
    except (OSError, ValueError, KeyError, TypeError):
        raise SystemExit("Conversion refused; protected source details suppressed") from None


if __name__ == "__main__":
    main()
