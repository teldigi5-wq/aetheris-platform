#!/usr/bin/env python3
"""Verify actual XERJ autoindex + catalog measurements, without publishing source.

The emitted numbers are a disposable local-run proof, NOT a human-reviewed
Lane A manifest, cloned_at, consent receipt, or external publication approval.
No source text, search passages, JWT values or raw CLI JSON are printed.
"""
from __future__ import annotations

import json
from pathlib import Path
import sys

PREFIX = "aetheris-xerj-review"
MAX_JSON_BYTES = 3 * 1024 * 1024
MAX_SOURCE_BYTES = 10 * 1024 * 1024


def check(ok: bool, message: str) -> None:
    if not ok:
        raise ValueError(message)


def read_json(path: Path) -> dict:
    check(path.is_file() and path.stat().st_size <= MAX_JSON_BYTES,
          "Index/catalog JSON missing or oversized")
    doc = json.loads(path.read_text(encoding="utf-8"))
    check(isinstance(doc, dict), "XERJ output is not a JSON object")
    return doc


def uint(value: object, key: str, maximum: int = 10_000_000) -> int:
    check(type(value) is int and 0 <= value <= maximum,
          "Invalid XERJ numeric field: " + key)
    return value


def measure(source: Path, index: dict, catalog: dict, plan: dict) -> dict:
    check(plan["publication"]["hub_submission_authorized"] is False,
          "Cannot measure a manifest marked as authorized")
    selected_tree = plan.get("filtered_tree", plan["expected_tree"])
    expected_count = uint(selected_tree["files"], "planned files")
    expected_bytes = uint(selected_tree["bytes"], "planned blob bytes",
                          MAX_SOURCE_BYTES)
    check(source.is_dir() and not source.is_symlink(), "Invalid source directory")
    source = source.resolve(strict=True)
    files = list(source.rglob("*"))
    check(files and len(files) < 2000, "Unexpected staged file inventory")
    regular = []
    for path in files:
        check(not path.is_symlink(), "Staged source unexpectedly contains symlink")
        if path.is_file():
            check(path.resolve().is_relative_to(source), "Staged file escapes root")
            regular.append(path)
    actual_bytes = sum(p.stat().st_size for p in regular)
    check(len(regular) == expected_count and actual_bytes == expected_bytes,
          "Staged source differs from exact selected Git-tree measurement")
    check(index.get("doc_kind") == "run", "Missing XERJ autoindex run document")
    check(index.get("prefix") == PREFIX, "Incorrect XERJ index namespace")
    indexed_files = uint(index.get("files_indexed"), "files_indexed")
    indexed_records = uint(index.get("records_total"), "records_total")
    check(0 < indexed_files <= expected_count, "Incomplete/invalid indexed file count")
    check(indexed_records > 0, "Local XERJ corpus has no indexed records")
    check(uint(index.get("junk_records_total", 0), "junk_records_total") == 0,
          "XERJ reported junk records; separate review required")
    check(uint(index.get("files_junk", 0), "files_junk") == 0,
          "XERJ discarded source files; separate review required")
    check("unextracted_archives" in index, "Missing extraction coverage")
    check(not index["unextracted_archives"], "Unextracted archives need review")

    run = catalog.get("run")
    datasets = catalog.get("datasets")
    check(isinstance(run, dict) and run.get("prefix") == PREFIX,
          "XERJ catalog is not for the reviewed local index")
    check(run.get("run_id") == index.get("run_id"),
          "Autoindex and catalog refer to different runs")
    check(isinstance(datasets, list) and 0 < len(datasets) <= 100,
          "Empty or truncated XERJ dataset catalog")
    check("datasets_summary" not in catalog,
          "Paged dataset catalog cannot prove full counts")

    catalog_records = 0
    catalog_bytes = 0
    for data in datasets:
        check(isinstance(data, dict), "Invalid XERJ dataset")
        catalog_records += uint(data.get("record_count"), "dataset.record_count")
        catalog_bytes += uint(data.get("bytes"), "dataset.bytes",
                              100 * 1024 * 1024)
        check(isinstance(data.get("index_name"), str)
              and data["index_name"].startswith(PREFIX),
              "Dataset index namespace differs from corpus")
    check(catalog_records == indexed_records,
          "Live dataset record totals disagree with XERJ run summary")
    check(catalog_bytes > 0, "Dataset catalog returned no content bytes")
    check(catalog_bytes <= 100 * 1024 * 1024,
          "Catalog byte total is unreasonable for this review source")
    # If the repo records measurements after a green run, CI must independently
    # reproduce them. A previous Git blob count is NOT an index record count.
    preview = plan.get("local_preview", {})
    check(isinstance(preview, dict), "Invalid local preview measurement fields")
    recorded = {
        "indexed_records": indexed_records,
        "measured_indexed_files": indexed_files,
        "measured_catalog_content_bytes": catalog_bytes,
    }
    for key, measured in recorded.items():
        claimed = preview.get(key)
        if claimed is not None:
            check(type(claimed) is int and claimed == measured,
                  "Recorded local XERJ measurement no longer matches live output")
    return {
        "source_pin": plan["source"]["sha"],
        "selected_git_files": expected_count,
        "selected_git_blob_bytes": expected_bytes,
        "xerj_files_indexed": indexed_files,
        "xerj_records_live": indexed_records,
        "xerj_catalog_dataset_bytes": catalog_bytes,
        "xerj_datasets": len(datasets),
        "measurement": "XERJ disposable autoindex run + live dataset catalog",
        "bytes_meaning": "catalog dataset input/content bytes; NOT index disk size",
        "publication": "NOT_AUTHORIZED",
        "human_review": "PENDING",
    }


def self_test() -> None:
    import tempfile
    with tempfile.TemporaryDirectory() as work:
        root = Path(work) / "source"
        root.mkdir()
        (root / "file.java").write_bytes(b"public class X {}\n")
        size = len(b"public class X {}\n")
        plan = {
            "source": {"sha": "a" * 40},
            "publication": {"hub_submission_authorized": False},
            "expected_tree": {"files": 1, "bytes": size},
        }
        run = {
            "doc_kind": "run", "prefix": PREFIX, "run_id": "fixture",
            "files_indexed": 1, "records_total": 2,
            "junk_records_total": 0, "files_junk": 0,
            "unextracted_archives": [],
        }
        map_doc = {"run": {"prefix": PREFIX, "run_id": "fixture"},
                   "datasets": [{"index_name": PREFIX + "-src",
                                 "record_count": 2, "bytes": size}]}
        result = measure(root, run, map_doc, plan)
        check(result["xerj_records_live"] == 2, "Metric parser self-test failed")
        altered = {**map_doc, "datasets": [{**map_doc["datasets"][0],
                                            "record_count": 1}]}
        try:
            measure(root, run, altered, plan)
        except ValueError:
            pass
        else:
            raise ValueError("Miscounting live catalog was incorrectly accepted")
        try:
            measure(root, run, map_doc, {
                **plan, "publication": {"hub_submission_authorized": True}})
        except ValueError:
            pass
        else:
            raise ValueError("Publication gate bypassed in receipt validator")
    print("XERJ_LOCAL_INDEX_METRICS_NEGATIVE_SELF_TEST_PASS")


def main() -> None:
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        self_test()
        return
    check(len(sys.argv) == 5, "Expected source-dir, run.json, catalog.json, plan.json")
    self_test()
    result = measure(
        Path(sys.argv[1]), read_json(Path(sys.argv[2])),
        read_json(Path(sys.argv[3])), read_json(Path(sys.argv[4]))
    )
    # Intentionally only non-sensitive verified counts and review state.
    print("XERJ_LOCAL_INDEX_MEASURED "
          f"git_files={result['selected_git_files']} "
          f"git_blob_bytes={result['selected_git_blob_bytes']} "
          f"indexed_files={result['xerj_files_indexed']} "
          f"live_records={result['xerj_records_live']} "
          f"catalog_content_bytes={result['xerj_catalog_dataset_bytes']} "
          f"datasets={result['xerj_datasets']} "
          "manifest_review=UNREVIEWED publication=NOT_AUTHORIZED")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
        # Source JSON, path contents and raw exceptions must never be logged.
        print("XERJ_LOCAL_INDEX_METRICS_BLOCKED: " + type(exc).__name__,
              file=sys.stderr)
        raise SystemExit(1)
