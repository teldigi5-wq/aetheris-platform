#!/usr/bin/env python3
"""B50: fail-closed Jackson 2/3 resolved Maven dependency evidence verifier.

Never substitutes checked-in dependency snapshots for real Maven output.
Does not contact a network, execute Maven, print private evidence, or claim a
deploy is approved. Run from repository root:
  python3 -m scripts.verify_jackson_resolved <module> <maven-tree-file>
"""
import re
import sys
from pathlib import Path
from xml.etree import ElementTree

MODULES = ("gateway", "identity-service", "user-service", "audit-service")
EXPECTED = {
    "jackson-2-bom.version": "2.21.8",
    "jackson-bom.version": "3.1.8",
}
# Maven's tree text uses groupId:artifactId:type:version:scope,
# optionally with an extra classifier before version.
COORD = re.compile(
    r"(?P<group>(?:com\.fasterxml|tools)\.jackson\.[\w.-]+):"
    r"(?P<artifact>jackson-[\w.-]+):"
    r"(?P<packaging>[\w.-]+):"
    r"(?P<version>\d+(?:\.\d+){1,3})(?=:)"
)
MAX_TREE = 2_000_000


def _is_expected_family(group, artifact, version):
    # Jackson 2.21 annotations intentionally use 2.21 (not 2.21.8).
    if group == "com.fasterxml.jackson.core" and artifact == "jackson-annotations":
        return version == "2.21"
    if group.startswith("com.fasterxml.jackson."):
        return version == EXPECTED["jackson-2-bom.version"]
    if group.startswith("tools.jackson."):
        return version == EXPECTED["jackson-bom.version"]
    return False


def check_pom(path):
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    root = ElementTree.parse(path).getroot()
    props = root.find("m:properties", ns)
    if props is None:
        raise ValueError("missing Maven properties")
    for name, expected in EXPECTED.items():
        actual = props.findtext("m:" + name, default=None, namespaces=ns)
        if actual != expected:
            raise ValueError("Maven Jackson BOM version differs from approved patch")


def verify(module, tree_text):
    if module not in MODULES:
        raise ValueError("unrecognized Maven module")
    if not isinstance(tree_text, str) or not tree_text or len(tree_text.encode("utf-8")) > MAX_TREE:
        raise ValueError("empty or oversized Maven dependency tree")
    seen = {}
    for line in tree_text.splitlines():
        m = COORD.search(line)
        if not m:
            # Jackson coordinates in Maven's ordinary dependency tree
            # must not be silently ignored by a parser format change.
            if ("com.fasterxml.jackson." in line or "tools.jackson." in line):
                raise ValueError("unrecognized Jackson dependency line")
            continue
        group, artifact, version = m.group("group", "artifact", "version")
        if not _is_expected_family(group, artifact, version):
            raise ValueError("Jackson dependency outside approved aligned BOM")
        key = (group, artifact)
        if key in seen and seen[key] != version:
            raise ValueError("conflicting resolved Jackson versions")
        seen[key] = version
    if not seen:
        raise ValueError("no resolved Jackson dependencies")
    # User OpenAPI/Swagger still contains legacy Jackson 2 alongside Boot 4
    # Jackson 3. A Java 3-only scan would miss its vulnerable transitive copy.
    if module == "user-service":
        for key in (("com.fasterxml.jackson.core", "jackson-databind"),
                    ("com.fasterxml.jackson.core", "jackson-core"),
                    ("tools.jackson.core", "jackson-databind")):
            if key not in seen:
                raise ValueError("missing expected User Jackson dependency")
    return len(seen)


def main(args=None):
    argv = sys.argv[1:] if args is None else args
    if len(argv) != 2 or argv[0] not in MODULES:
        print("JACKSON_RESOLUTION_INVALID_ARGUMENTS")
        return 2
    try:
        module, filename = argv
        path = Path(filename)
        if not path.is_file() or path.stat().st_size > MAX_TREE:
            raise ValueError("missing or oversized dependency tree")
        check_pom(Path(module) / "pom.xml")
        count = verify(module, path.read_text(encoding="utf-8"))
    except (ValueError, OSError, UnicodeError, ElementTree.ParseError):
        print("JACKSON_RESOLUTION_NO_GO")
        return 1
    print("JACKSON_RESOLUTION_PASS_" + module.upper().replace("-", "_") + "_" + str(count))
    return 0  # CI policy check only. Never a production release approval.


if __name__ == "__main__":
    raise SystemExit(main())
