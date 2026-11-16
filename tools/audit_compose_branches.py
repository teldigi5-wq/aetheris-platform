#!/usr/bin/env python3
"""Read-only repository branch sweep of Docker exposure/credential defaults.

Visits ALL *fetched* origin branch refs; never checks out/untrustedly executes
branch code. Reports risk rather than pretending divergent historic branches
were modified. GitHub Actions checkout MUST fetch-depth:0 for completeness.
"""
from __future__ import annotations
import json
from pathlib import Path
import subprocess
import sys

FILES=("docker-compose.yml","docker-compose.core.yml",
       "docker-compose.integration-external.yml","docker-compose.override.yml")
ROOT=Path(__file__).resolve().parents[1]


def git(*args):
    p=subprocess.run(("git",)+args,cwd=ROOT,text=True,
                     capture_output=True,timeout=100,check=False)
    if p.returncode:return None
    return p.stdout


def scan_ref(ref):
    risks=[]
    inspected=[]
    for filename in FILES:
        contents=git("show",ref+":"+filename)
        if contents is None:continue
        inspected.append(filename)
        for marker in ("POSTGRES_PASSWORD: aetheris",
                       "SPRING_DATASOURCE_PASSWORD: aetheris",
                       "RABBITMQ_DEFAULT_PASS: aetheris",
                       "SPRING_RABBITMQ_PASSWORD: aetheris",
                       "change-me-local-development-secret-32chars",
                       "GF_SECURITY_ADMIN_PASSWORD: aetheris"):
            if marker in contents:risks.append({"file":filename,"class":"predictable-secret-fallback"})
        for port in ("5432:5432","6379:6379","5672:5672","15672:15672",
                     "8081:8081","8082:8082","8083:8083","8090:8090",
                     "8080:8080"):
            if '"'+port+'"' in contents:
                risks.append({"file":filename,"class":"nonloopback-published-port","port":port})
        if "/var/run/docker.sock:/var/run/docker.sock" in contents:
            risks.append({"file":filename,"class":"docker-socket-in-monitoring-container"})
    return {"ref":ref,"fileCount":len(inspected),"riskCount":len(risks),
            "risks":risks[:120]}


def main():
    refs=git("for-each-ref","--format=%(refname)","refs/remotes/origin")
    if not refs:raise SystemExit("BRANCH_AUDIT_NO_FETCHED_REMOTE_REFS")
    names=sorted(set(x.strip() for x in refs.splitlines()
                     if x.strip().startswith("refs/remotes/origin/")))
    if len(names)<2:raise SystemExit("BRANCH_AUDIT_INCOMPLETE_REMOTE_FETCH")
    results=[scan_ref(x) for x in names]
    summary={"evidenceClass":"AETHERIS_PLATFORM_READ_ONLY_DOCKER_BRANCH_STATIC_SWEEP",
        "repository":"teldigi5-wq/aetheris-platform",
        "fetchedRemoteBranches":len(names),
        "riskyBranches":sum(x["riskCount"]>0 for x in results),
        "inspectedBranchComposeFiles":sum(x["fileCount"] for x in results),
        "method":"git show origin ref:four fixed Compose paths, no code executed",
        "notProductionExposureConfirmation":True,
        "branchFixesNotAutomaticallyBackported":True,
        "historicalBranchesMustBeIndividuallyRebasedReviewed":True,
        "entries":results}
    output=ROOT/"build-evidence/runtime/compose-branch-security-audit.json"
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(summary,sort_keys=True,indent=2)+"\n")
    print("AETHERIS_PLATFORM_ALL_FETCHED_BRANCH_COMPOSE_AUDIT",json.dumps({
        "remoteBranches":summary["fetchedRemoteBranches"],
        "riskyBranches":summary["riskyBranches"],
        "composeFiles":summary["inspectedBranchComposeFiles"]}))
    if "refs/remotes/origin/main" not in names:
        raise SystemExit("BRANCH_AUDIT_MAIN_MISSING")
    if summary["inspectedBranchComposeFiles"]<len(names):
        raise SystemExit("BRANCH_AUDIT_NO_COMPOSE_FILE_COVERAGE")


if __name__=="__main__":main()
