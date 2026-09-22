#!/usr/bin/env python3
"""Build a deterministic, content-only AI-runtime extraction bundle from the Step D manifest."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import shutil
import subprocess
import sys
import tarfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "architecture" / "ai-runtime-extraction-manifest.json"
EXPECTED_DESTINATION = "teldigi5-wq/aetheris-ai-runtime"
EXPECTED_STATUS = "BLOCKED_PENDING_DESTINATION_REPOSITORY"
SUPPORTED_GIT_MODES = {"100644": 0o644, "100755": 0o755}
ARCHIVE_NAME = "aetheris-ai-runtime-export.tar"
INTERNAL_MANIFEST = "EXPORT-MANIFEST.json"
INTERNAL_README = "README-EXTRACTION.md"
REPORT_NAME = "ai-runtime-export-bundle-report.json"
SHA_NAME = "aetheris-ai-runtime-export.sha256"


@dataclass(frozen=True)
class ExportEntry:
    path: str
    transfer_class: str
    git_mode: str
    tar_mode: int
    sha256: str
    data: bytes


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_json_bytes(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode(
        "utf-8"
    )


def tracked_records(pathspec: str) -> list[tuple[str, str]]:
    raw = subprocess.check_output(["git", "ls-files", "-s", "-z", "--", pathspec], cwd=ROOT)
    records: list[tuple[str, str]] = []
    for item in raw.split(b"\0"):
        if not item:
            continue
        try:
            metadata, raw_path = item.split(b"\t", 1)
            mode, _object_id, stage = metadata.decode("ascii").split(" ")
            path = raw_path.decode("utf-8")
        except (ValueError, UnicodeDecodeError) as exc:
            raise RuntimeError(f"could not parse git ls-files record for {pathspec!r}: {item!r}") from exc
        if stage != "0":
            raise RuntimeError(f"unmerged index entry is not exportable: {path} (stage {stage})")
        records.append((mode, path))
    return records


def add_entry(entries: dict[str, ExportEntry], path: str, transfer_class: str, git_mode: str) -> None:
    if path in entries:
        raise RuntimeError(
            f"export path {path!r} is declared by more than one transfer class: "
            f"{entries[path].transfer_class!r} and {transfer_class!r}"
        )
    if git_mode not in SUPPORTED_GIT_MODES:
        raise RuntimeError(
            f"unsupported Git mode for deterministic export: {path} has mode {git_mode}; "
            "symlinks, submodules and special files are intentionally refused"
        )
    absolute = ROOT / path
    if not absolute.is_file() or absolute.is_symlink():
        raise RuntimeError(f"tracked export payload is not a regular file: {path}")
    data = absolute.read_bytes()
    entries[path] = ExportEntry(
        path=path,
        transfer_class=transfer_class,
        git_mode=git_mode,
        tar_mode=SUPPORTED_GIT_MODES[git_mode],
        sha256=sha256_bytes(data),
        data=data,
    )


def collect_entries(manifest: dict[str, object]) -> list[ExportEntry]:
    entries: dict[str, ExportEntry] = {}

    move_roots = manifest.get("move_roots")
    transfer_workflows = manifest.get("transfer_workflows")
    copy_bootstrap = manifest.get("copy_bootstrap")
    if not isinstance(move_roots, list) or not all(isinstance(item, str) for item in move_roots):
        raise RuntimeError("manifest move_roots must be a list of strings")
    if not isinstance(transfer_workflows, list) or not all(
        isinstance(item, str) for item in transfer_workflows
    ):
        raise RuntimeError("manifest transfer_workflows must be a list of strings")
    if not isinstance(copy_bootstrap, list) or not all(isinstance(item, str) for item in copy_bootstrap):
        raise RuntimeError("manifest copy_bootstrap must be a list of strings")

    for root in move_roots:
        records = tracked_records(root)
        if not records:
            raise RuntimeError(f"move_root has no tracked files: {root}")
        prefix = root.rstrip("/") + "/"
        for mode, path in records:
            if path != root and not path.startswith(prefix):
                raise RuntimeError(f"Git returned path outside move_root {root!r}: {path!r}")
            add_entry(entries, path, f"move_root:{root}", mode)

    for logical_class, declared in (
        ("transfer_workflow", transfer_workflows),
        ("copy_bootstrap", copy_bootstrap),
    ):
        for expected_path in declared:
            records = tracked_records(expected_path)
            exact = [(mode, path) for mode, path in records if path == expected_path]
            if len(exact) != 1:
                raise RuntimeError(
                    f"declared {logical_class} must resolve to exactly one tracked regular file: "
                    f"{expected_path!r}; matches={records!r}"
                )
            mode, path = exact[0]
            add_entry(entries, path, logical_class, mode)

    if not entries:
        raise RuntimeError("export payload is empty")
    return [entries[path] for path in sorted(entries)]


def payload_tree_sha256(entries: list[ExportEntry]) -> str:
    hasher = hashlib.sha256()
    for entry in entries:
        hasher.update(entry.transfer_class.encode("utf-8"))
        hasher.update(b"\0")
        hasher.update(entry.path.encode("utf-8"))
        hasher.update(b"\0")
        hasher.update(entry.git_mode.encode("ascii"))
        hasher.update(b"\0")
        hasher.update(entry.sha256.encode("ascii"))
        hasher.update(b"\n")
    return hasher.hexdigest()


def read_manifest() -> dict[str, object]:
    if not MANIFEST_PATH.is_file():
        raise RuntimeError(f"missing extraction manifest: {MANIFEST_PATH}")
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    if manifest.get("schema_version") != 1:
        raise RuntimeError("extraction manifest schema_version must be 1")
    if manifest.get("status") != EXPECTED_STATUS:
        raise RuntimeError(
            f"extraction manifest status must remain {EXPECTED_STATUS!r} until destination cutover"
        )
    if manifest.get("destination_repository") != EXPECTED_DESTINATION:
        raise RuntimeError(f"destination_repository must be {EXPECTED_DESTINATION!r}")
    return manifest


def extraction_readme(manifest: dict[str, object], revision: str, tree_hash: str) -> bytes:
    text = f"""# Aetheris AI Runtime Extraction Bundle

This archive is a deterministic, content-only transfer bundle generated from
`teldigi5-wq/aetheris-platform` at revision `{revision}`.

Destination repository: `{manifest['destination_repository']}`
Certified Step D source baseline: `{manifest['source_certified_sha']}`
Payload tree SHA-256: `{tree_hash}`

The payload is defined exclusively by `architecture/ai-runtime-extraction-manifest.json`:
- all tracked files under `move_roots`;
- the exact `transfer_workflows` files;
- the exact `copy_bootstrap` files.

This archive does **not** contain Git history and does not authorize deletion of the
source trees. Git history must be preserved separately where practical, and platform
source removal remains blocked until every deletion gate in Issue #140 is satisfied.

Truth boundary: this is hosted content-transfer evidence only. It does not prove
physical-PC validation, production deployment, or registry publication.
"""
    return text.encode("utf-8")


def tar_info(name: str, data: bytes, mode: int) -> tarfile.TarInfo:
    info = tarfile.TarInfo(name=name)
    info.size = len(data)
    info.mode = mode
    info.mtime = 0
    info.uid = 0
    info.gid = 0
    info.uname = ""
    info.gname = ""
    info.type = tarfile.REGTYPE
    return info


def write_archive(
    archive_path: Path,
    entries: list[ExportEntry],
    internal_manifest_bytes: bytes,
    readme_bytes: bytes,
) -> None:
    payload: list[tuple[str, bytes, int]] = [
        (entry.path, entry.data, entry.tar_mode) for entry in entries
    ]
    payload.extend(
        [
            (INTERNAL_MANIFEST, internal_manifest_bytes, 0o644),
            (INTERNAL_README, readme_bytes, 0o644),
        ]
    )
    names = [row[0] for row in payload]
    if len(names) != len(set(names)):
        raise RuntimeError("archive metadata names collide with transfer payload")

    with tarfile.open(archive_path, mode="w", format=tarfile.USTAR_FORMAT) as archive:
        for name, data, mode in sorted(payload, key=lambda row: row[0]):
            archive.addfile(tar_info(name, data, mode), io.BytesIO(data))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output-dir",
        default="build-evidence/architecture/export-bundle",
        help="directory for archive and detached evidence",
    )
    parser.add_argument(
        "--expected-revision",
        default=None,
        help="optional exact Git revision that HEAD must equal",
    )
    args = parser.parse_args()

    try:
        revision = git("rev-parse", "HEAD")
        if args.expected_revision and revision != args.expected_revision:
            raise RuntimeError(
                f"revision mismatch: checked out {revision}, expected {args.expected_revision}"
            )

        manifest = read_manifest()
        entries = collect_entries(manifest)
        tree_hash = payload_tree_sha256(entries)
        exported_files = [
            {
                "path": entry.path,
                "transfer_class": entry.transfer_class,
                "git_mode": entry.git_mode,
                "sha256": entry.sha256,
                "bytes": len(entry.data),
            }
            for entry in entries
        ]
        internal_manifest = {
            "schema_version": 1,
            "bundle_type": "AETHERIS_AI_RUNTIME_CONTENT_EXPORT",
            "export_revision": revision,
            "source_repository": manifest.get("source_repository"),
            "source_branch": manifest.get("source_branch"),
            "source_certified_sha": manifest.get("source_certified_sha"),
            "destination_repository": manifest.get("destination_repository"),
            "migration_status": manifest.get("status"),
            "history_included": False,
            "history_policy": manifest.get("history_policy"),
            "payload_tree_sha256": tree_hash,
            "manifest_sha256": sha256_bytes(MANIFEST_PATH.read_bytes()),
            "exported_files": exported_files,
            "truth_boundaries": manifest.get("truth_boundaries", {}),
        }
        internal_manifest_bytes = canonical_json_bytes(internal_manifest)
        readme_bytes = extraction_readme(manifest, revision, tree_hash)

        output_dir = Path(args.output_dir)
        if not output_dir.is_absolute():
            output_dir = ROOT / output_dir
        if output_dir == ROOT or ROOT not in output_dir.parents:
            raise RuntimeError("output directory must be a child of the repository root")
        if output_dir.exists():
            shutil.rmtree(output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

        archive_path = output_dir / ARCHIVE_NAME
        write_archive(archive_path, entries, internal_manifest_bytes, readme_bytes)
        archive_sha = sha256_bytes(archive_path.read_bytes())

        (output_dir / INTERNAL_MANIFEST).write_bytes(internal_manifest_bytes)
        (output_dir / INTERNAL_README).write_bytes(readme_bytes)
        (output_dir / SHA_NAME).write_text(f"{archive_sha}  {ARCHIVE_NAME}\n", encoding="utf-8")

        transfer_class_counts: dict[str, int] = {}
        for entry in entries:
            transfer_class_counts[entry.transfer_class] = (
                transfer_class_counts.get(entry.transfer_class, 0) + 1
            )

        report = {
            "status": "PASS",
            "schema_version": 1,
            "bundle_type": "AETHERIS_AI_RUNTIME_CONTENT_EXPORT",
            "export_revision": revision,
            "source_certified_sha": manifest.get("source_certified_sha"),
            "destination_repository": manifest.get("destination_repository"),
            "migration_status": manifest.get("status"),
            "history_included": False,
            "archive_format": "ustar",
            "archive_name": ARCHIVE_NAME,
            "archive_sha256": archive_sha,
            "archive_bytes": archive_path.stat().st_size,
            "payload_tree_sha256": tree_hash,
            "payload_file_count": len(entries),
            "payload_bytes": sum(len(entry.data) for entry in entries),
            "transfer_class_counts": transfer_class_counts,
            "internal_manifest_sha256": sha256_bytes(internal_manifest_bytes),
            "manifest_sha256": sha256_bytes(MANIFEST_PATH.read_bytes()),
            "exported_paths": [entry.path for entry in entries],
            "truth_boundaries": manifest.get("truth_boundaries", {}),
        }
        (output_dir / REPORT_NAME).write_bytes(canonical_json_bytes(report))

        print(
            "AI-runtime deterministic export bundle: PASS "
            f"({len(entries)} files, {report['payload_bytes']} payload bytes, sha256={archive_sha})"
        )
        return 0
    except (OSError, RuntimeError, subprocess.CalledProcessError, json.JSONDecodeError) as exc:
        print(f"AI-runtime deterministic export bundle FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
