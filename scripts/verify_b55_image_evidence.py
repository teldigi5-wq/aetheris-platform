#!/usr/bin/env python3
"""B55: local, unsigned CI evidence linking build source, image and scan files.

This is NOT a signature, SLSA provenance, registry digest, reproducible-build
claim or release approval. No secrets, network, Docker commands or live data.
"""
import hashlib
import json
import re
import sys
from pathlib import Path

from scripts.verify_b53_scanner_evidence import assess, load_json

REPOSITORY = "teldigi5-wq/aetheris-platform"
MODULES = ("gateway", "identity-service", "user-service", "audit-service")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
IMAGE_ID = re.compile(r"^sha256:[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
MAX_MANIFEST = 8192


def digest_file(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def strict_json(path, limit=MAX_MANIFEST):
    p = Path(path)
    if not p.is_file() or not 2 < p.stat().st_size <= limit:
        raise ValueError("missing, empty or oversized evidence")
    def no_duplicate(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("duplicate evidence key")
            result[key] = value
        return result
    with p.open("r", encoding="utf-8") as stream:
        return json.load(stream, object_pairs_hook=no_duplicate)


def make_record(service, image_id, archive_path, sbom_path, grype_path, source_sha):
    if service not in MODULES or not IMAGE_ID.fullmatch(image_id) or not COMMIT.fullmatch(source_sha):
        raise ValueError("invalid service, image ID or checked-out Git commit")
    sbom, grype = load_json(sbom_path), load_json(grype_path)
    if sbom.get("bomFormat") != "CycloneDX" or len(sbom.get("components", [])) < 10:
        raise ValueError("invalid SBOM")
    if not isinstance(grype.get("matches"), list) or not grype.get("descriptor"):
        raise ValueError("invalid Grype scan")
    return {
        "contract_version": 1,
        "repository": REPOSITORY,
        "checked_out_source_sha": source_sha,
        "service": service,
        "image_id": image_id,
        "docker_archive_sha256": digest_file(archive_path),
        "cyclonedx_sha256": digest_file(sbom_path),
        "grype_sha256": digest_file(grype_path),
        "components": len(sbom["components"]),
        "matches": len(grype["matches"]),
    }


def verify_records(folder, expected_sha):
    if not isinstance(expected_sha, str) or not COMMIT.fullmatch(expected_sha):
        raise ValueError("expected source revision invalid")
    root = Path(folder)
    if not root.is_dir():
        raise ValueError("missing reports directory")
    records = []
    for service in MODULES:
        r = strict_json(root / (service + ".evidence.json"))
        expected_keys = {
            "contract_version", "repository", "checked_out_source_sha", "service",
            "image_id", "docker_archive_sha256", "cyclonedx_sha256",
            "grype_sha256", "components", "matches"
        }
        if set(r) != expected_keys or r["contract_version"] != 1 or r["repository"] != REPOSITORY:
            raise ValueError("unexpected manifest contract or repository")
        if r["service"] != service or r["checked_out_source_sha"] != expected_sha:
            raise ValueError("source or service identity mismatch")
        if not isinstance(r["image_id"], str) or not IMAGE_ID.fullmatch(r["image_id"]):
            raise ValueError("invalid inspected image ID")
        for key in ("docker_archive_sha256", "cyclonedx_sha256", "grype_sha256"):
            if not isinstance(r[key], str) or not SHA256.fullmatch(r[key]):
                raise ValueError("missing or malformed digest")
        sbom_path = root / (service + ".cyclonedx.json")
        scan_path = root / (service + ".grype.json")
        if digest_file(sbom_path) != r["cyclonedx_sha256"] or digest_file(scan_path) != r["grype_sha256"]:
            raise ValueError("artifact hash mismatch")
        sbom, grype = load_json(sbom_path), load_json(scan_path)
        if type(r["components"]) is not int or type(r["matches"]) is not int:
            raise ValueError("invalid scan counters")
        if len(sbom.get("components", [])) != r["components"] or len(grype.get("matches", [])) != r["matches"]:
            raise ValueError("scan counters mismatch")
        if not isinstance(grype.get("descriptor"), dict) or sbom.get("bomFormat") != "CycloneDX":
            raise ValueError("invalid report format")
        records.append(r)
    ids = [r["image_id"] for r in records]
    if len(set(ids)) != len(MODULES):
        raise ValueError("duplicate service image IDs")
    return records


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    try:
        if len(args) == 8 and args[0] == "record":
            _, service, image_id, archive, sbom, scan, source_sha, out = args
            rec = make_record(service, image_id, archive, sbom, scan, source_sha)
            # File names are confined by caller to disposable CI report directory.
            Path(out).write_text(json.dumps(rec, sort_keys=True, indent=2) + "\n", encoding="utf-8")
            print("B55_EVIDENCE_RECORDED_" + service.upper().replace("-", "_"))
        elif len(args) == 3 and args[0] == "verify":
            _, folder, sha = args
            records = verify_records(folder, sha)
            print("B55_FOUR_IMAGE_SCAN_HASHES_MATCH_CHECKED_OUT_COMMIT")
            print("B55_IMAGE_SET_REVIEW_ONLY_" + str(len(records)))
        else:
            raise ValueError("invalid B55 command")
    except (OSError, ValueError, TypeError, UnicodeError, RecursionError, KeyError, AttributeError):
        print("B55_NO_GO_INVALID_IMAGE_EVIDENCE")
        return 2
    return 0  # Only validates local unsigned evidence. NOT production release GO.


if __name__ == "__main__":
    raise SystemExit(main())
