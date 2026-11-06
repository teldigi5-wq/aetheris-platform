#!/usr/bin/env python3
"""Real hosted four-JVM RSA/mTLS + PostgreSQL outbox and outage recovery proof.

Prior 84 checks run first. Then the isolated RabbitMQ is stopped *on purpose*
while a real HTTP mutation commits. The User API must still succeed and the
outbox must retain the event; restarting the broker must eventually reach Audit.
All synthetic credentials remain in disposable CI.
"""
from __future__ import annotations
import json
import os
import subprocess
import sys
import time
from pathlib import Path
import xerj_tenant_http_proof as tenant

baseline=tenant.baseline

def compose(*args):
    project=os.environ.get("XERJ_COMPOSE_PROJECT","")
    if not project.startswith("xerj-mtls-ci-"):
        raise AssertionError("refuse operations outside unique hosted test Compose project")
    command=["docker","compose","-p",project,
             "-f","tests/security/xerj-disposable-deps.compose.yml",*args]
    return subprocess.run(command,check=True,stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL,timeout=120)

def postgres(statement):
    project=os.environ.get("XERJ_COMPOSE_PROJECT","")
    if not project.startswith("xerj-mtls-ci-"):
        raise AssertionError("not disposable PostgreSQL")
    command=["docker","compose","-p",project,
             "-f","tests/security/xerj-disposable-deps.compose.yml",
             "exec","-T","postgres","psql","-X","-t","-A","-v",
             "ON_ERROR_STOP=1","-U","xerj_ci","-d","xerj_ci","-c",statement]
    result=subprocess.run(command,check=True,capture_output=True,
                          text=True,timeout=12)
    return result.stdout.strip()

def main():
    # This parses the same --report and --compose arguments and writes its
    # sanitized initial report without any JWT/payload logging.
    tenant.main()
    import argparse
    parser=argparse.ArgumentParser()
    parser.add_argument("--report",type=Path,required=True)
    parser.add_argument("--compose",required=True)
    args=parser.parse_args()
    code,login=tenant.gateway("POST","/api/auth/login",payload={
        "email":"e2e-admin@aetheris.test",
        "password":"SyntheticPass123!Unique"})
    baseline.verify(code==200 and isinstance(login,dict)
                    and isinstance(login.get("accessToken"),str),
                    "outbox_real_admin_login_before_broker_outage")
    bearer=login["accessToken"]
    # Must verify committed records are delivered, not merely accepted by API.
    complete=postgres("SELECT count(*) FROM user_event_outbox WHERE state='delivered';")
    baseline.verify(int(complete)>=6,"outbox_live_mutations_confirmed_and_delivered")

    compose("stop","rabbitmq")
    try:
        code,created=tenant.gateway("POST","/api/users",bearer,payload={
            "name":"Synthetic broker-outage recovery",
            "email":"outbox-outage@aetheris.test"})
        baseline.verify(code==201 and isinstance(created,dict)
                        and isinstance(created.get("id"),int),
                        "outbox_user_commit_while_broker_unavailable")
        uid=created["id"]
        count=postgres("SELECT count(*) FROM user_event_outbox "
            "WHERE user_id="+str(uid)+" AND event_type='user.created' "
            "AND state<>'delivered';")
        baseline.verify(count=="1","outbox_committed_event_survives_broker_outage")
        # A true non-atomic direct publish would have lost this event.
    finally:
        compose("start","rabbitmq")

    # The broker may reconnect after several seconds. Both User relay and Audit
    # listener must recover and preserve the stable outbox eventId.
    recovered=False
    for attempt in range(55):
        try:
            rows=postgres("SELECT count(*) FROM user_event_outbox "
                "WHERE user_id="+str(uid)+" AND event_type='user.created' "
                "AND state='delivered';")
            code,events=tenant.gateway("GET","/api/events",bearer)
            seen=[e for e in events if isinstance(e,dict)
                  and e.get("userId")==uid and e.get("eventType")=="user.created"] \
                 if code==200 and isinstance(events,list) else []
            if rows=="1" and len(seen)==1:
                recovered=True
                break
        except (subprocess.CalledProcessError,subprocess.TimeoutExpired,
                OSError,TypeError,ValueError):
            pass
        time.sleep(1)
    baseline.verify(recovered,
        "outbox_broker_restart_confirms_then_audit_persists_once")
    count=postgres("SELECT count(*) FROM user_event_outbox "
        "WHERE user_id="+str(uid)+" AND event_type='user.created';")
    baseline.verify(count=="1",
        "outbox_retry_keeps_one_stable_postgres_event_identifier")

    report=json.loads(args.report.read_text())
    report["checks"]=dict(baseline.CHECKS)
    report["outbox_disposable_rollback_replay_verified"]=True
    report["outbox_disposable_broker_outage_recovery_verified"]=True
    report["outbox_production_verified"]=False
    report["outbox_exactly_once_guaranteed"]=False
    report["production_verified"]=False
    report["status"]="PASS"
    args.report.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n")
    print("XERJ_REAL_OUTBOX_BROKER_OUTAGE_RESUME_PASS checks="+str(len(report["checks"])))

if __name__=="__main__":
    try:
        main()
    except Exception as err:
        print("XERJ_OUTBOX_PROOF_HOLD class="+type(err).__name__,file=sys.stderr)
        raise SystemExit(1)
