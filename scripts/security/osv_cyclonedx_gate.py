#!/usr/bin/env python3
"""Fail-closed CycloneDX Maven SBOM to public OSV API check (review-only).

No source code or secrets sent; only public Maven coordinates. Advisory matching
does not prove exploitability; no listed advisories does not prove safety.
"""
import json
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ENDPOINT = "https://api.osv.dev/v1/querybatch"

def collect(paths):
    if len(paths) != 4:
        raise ValueError("four service SBOMs required")
    found = set()
    for name in paths:
        bom = json.loads(Path(name).read_text(encoding="utf-8"))
        components = bom.get("components")
        if bom.get("bomFormat") != "CycloneDX" or not isinstance(components, list) or len(components) < 20:
            raise ValueError("missing/incomplete CycloneDX inventory: " + name)
        for component in components:
            if not isinstance(component, dict):
                raise ValueError("malformed component")
            item = tuple(component.get(k) for k in ("group", "name", "version"))
            if not all(isinstance(x, str) and x.strip() for x in item):
                raise ValueError("missing dependency identity")
            found.add(item)
        print("XERJ_SBOM_COMPLETE service=" + Path(name).parts[0] +
              " components=" + str(len(components)))
    if len(found) < 35:
        raise ValueError("implausibly small dependency set")
    return sorted(found)

def parse_hits(answers, coords):
    if not isinstance(answers, list) or len(answers) != len(coords):
        raise ValueError("incomplete OSV batch response")
    result = set()
    for item, answer in zip(coords, answers):
        if not isinstance(answer, dict) or answer.get("next_page_token"):
            raise ValueError("malformed or paginated OSV response")
        vulnerabilities = answer.get("vulns", [])
        if not isinstance(vulnerabilities, list):
            raise ValueError("malformed OSV vulnerability list")
        for obj in vulnerabilities:
            if not isinstance(obj, dict) or not isinstance(obj.get("id"), str):
                raise ValueError("invalid OSV advisory")
            result.add((item, obj["id"]))
    return result

def query_batch(coords):
    payload = {"queries": [
        {"package":{"ecosystem":"Maven","name":group + ":" + artifact}, "version":version}
        for group, artifact, version in coords]}
    raw = json.dumps(payload, separators=(",", ":")).encode()
    last = None
    for attempt in range(2):
        try:
            req = Request(ENDPOINT, data=raw, method="POST",
                headers={"Content-Type":"application/json","User-Agent":"aetheris-review/1"})
            with urlopen(req, timeout=20) as response:
                body = response.read(8000001)
                if len(body) > 8000000:
                    raise ValueError("OSV response exceeds cap")
                data = json.loads(body)
                return parse_hits(data.get("results"), coords)
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            last = exc
            if isinstance(exc, HTTPError) and exc.code not in (429, 500, 502, 503, 504):
                break
            if attempt == 0:
                time.sleep(1)
    raise RuntimeError("OSV API unavailable — blocked") from last

def main():
    all_deps = collect(sys.argv[1:])
    hits = set()
    print("XERJ_OSV_SCAN_START unique_maven_dependencies=" + str(len(all_deps)))
    for offset in range(0,len(all_deps),100):
        hits.update(query_batch(all_deps[offset:offset+100]))
    for (group, name, ver), advisory in sorted(hits):
        print(f"XERJ_OSV_FINDING {group}:{name}:{ver} {advisory}")
    if hits:
        print("XERJ_OSV_HOLD advisory_count=" + str(len(hits)))
        return 1
    print("XERJ_OSV_NO_REPORTED_ADVISORIES coverage=OSV_ONLY production_status=NO_GO")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, RuntimeError, OSError, json.JSONDecodeError) as exc:
        print("XERJ_OSV_VERIFICATION_FAILED " + type(exc).__name__, file=sys.stderr)
        raise SystemExit(2)
