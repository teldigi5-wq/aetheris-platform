#!/usr/bin/env python3
"""B51: offline Java 21 October-2026 JDK/JRE OCI evidence gate.

The separate CI runner collects immutable Docker Hub index digests, runs
java -version in a network-disabled disposable container and feeds this
checker its bounded JSON metadata. This script never fetches images or
authorizes a production deployment.
"""
import json
import re
import sys
from pathlib import Path

DIGEST = re.compile(r"^sha256:[a-f0-9]{64}$")
PATCH = re.compile(r"\b(?:openjdk|java) version \"?21\.0\.13(?:[+_.-]|\"|\b)")
TAGS = {
    "jdk": ("21.0.13_9-jdk-noble", "21-jdk-noble"),
    "jre": ("21.0.13_9-jre-noble", "21-jre-noble"),
}
PLATFORMS = {"linux/amd64", "linux/arm64"}
MAX_BYTES = 8192


def check_image(role, data):
    """Reject fake/mutable OCI evidence. Return a reviewed immutable reference."""
    if role not in TAGS or not isinstance(data, dict):
        raise ValueError("unknown role or image report")
    if set(data) != {"tag", "digest", "java_version", "platforms", "publisher"}:
        raise ValueError("invalid image metadata fields")
    if data["tag"] not in TAGS[role]:
        raise ValueError("unsupported patched image tag")
    if data["publisher"] != "docker.io/library/eclipse-temurin":
        raise ValueError("untrusted OCI publisher")
    if not isinstance(data["digest"], str) or not DIGEST.fullmatch(data["digest"]):
        raise ValueError("not an immutable SHA256 OCI digest")
    if not isinstance(data["java_version"], str) or not PATCH.search(data["java_version"]):
        raise ValueError("running image is not OpenJDK 21.0.13")
    if (
        not isinstance(data["platforms"], list)
        or len(data["platforms"]) != len(PLATFORMS)
        or set(data["platforms"]) != PLATFORMS
        or any(type(p) is not str for p in data["platforms"])
    ):
        raise ValueError("target multiarchitecture platforms unverified")
    return f'eclipse-temurin:{data["tag"]}@{data["digest"]}'


def verify(report):
    if not isinstance(report, dict) or set(report) != {"jdk", "jre"}:
        raise ValueError("JDK and JRE must both be attested")
    jdk = check_image("jdk", report["jdk"])
    jre = check_image("jre", report["jre"])
    if jdk == jre:
        raise ValueError("builder and runtime images must remain distinct")
    return {"jdk": jdk, "jre": jre}


def read_report(filename):
    file = Path(filename)
    if not file.is_file() or file.stat().st_size > MAX_BYTES:
        raise ValueError("missing or oversized attestation")
    with file.open("r", encoding="utf-8") as stream:
        def strict_pairs(pairs):
            result = {}
            for key, value in pairs:
                if key in result:
                    raise ValueError("duplicate evidence key")
                result[key] = value
            return result
        return json.load(stream, object_pairs_hook=strict_pairs)


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 1:
        print("JAVA21_OCI_INVALID_EVIDENCE")
        return 2
    try:
        image_refs = verify(read_report(args[0]))
    except (OSError, ValueError, UnicodeError, TypeError):
        print("JAVA21_OCI_INVALID_EVIDENCE")
        return 2
    print("JAVA21_OCI_READY_FOR_DOCKERFILE_REVIEW")
    print("JDK=" + image_refs["jdk"])
    print("JRE=" + image_refs["jre"])
    return 0  # CI evidence parsing only; NEVER a release GO or production deploy.


if __name__ == "__main__":
    raise SystemExit(main())
