"""Shared plumbing for the end-to-end scripts.

Deliberately stdlib-only: these run against a live `docker compose up` stack, and a test harness
that needs its own install step is one more reason not to run it.
"""
import json
import os
import random
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

ADMIN = os.getenv("E2E_ADMIN_URL", "http://localhost:8083")
PARKING = os.getenv("E2E_PARKING_URL", "http://localhost:8081")
BILLING = os.getenv("E2E_BILLING_URL", "http://localhost:8082")

ADMIN_USER = os.getenv("E2E_ADMIN_USER", "admin")
ADMIN_PASSWORD = os.getenv("E2E_ADMIN_PASSWORD", "ChangeMe123!")
DRIVER_PHONE = os.getenv("E2E_DRIVER_PHONE", "0901234567")

_failures = []


def call(method, url, token=None, body=None, expect=None):
    """One HTTP call. Returns (status, parsed body); raises only when `expect` is not met."""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            status, payload = response.status, response.read().decode()
    except urllib.error.HTTPError as exc:
        status, payload = exc.code, exc.read().decode()
    try:
        parsed = json.loads(payload) if payload else {}
    except json.JSONDecodeError:
        parsed = {"raw": payload}
    if expect is not None and status != expect:
        raise AssertionError(f"{method} {url} -> {status} (expected {expect}): {payload[:400]}")
    return status, parsed


def check(label, condition, detail=""):
    if not condition:
        _failures.append(f"{label} — {detail}")
    print(f"  [{'PASS' if condition else 'FAIL'}] {label}"
          f"{('  ' + detail) if detail and not condition else ''}")


def finish(name):
    """Print the verdict and exit non-zero if anything failed, so CI can gate on it."""
    print("\n" + "=" * 60)
    if _failures:
        print(f"{name}: FAILED ({len(_failures)})")
        for failure in _failures:
            print("  ! " + failure)
        sys.exit(1)
    print(f"{name}: ALL CHECKS PASSED")


def wait_up(name, url, timeout=180):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=5) as response:
                if response.status == 200:
                    print(f"  {name} up")
                    return
        except Exception:            # not up yet — connection refused, 503 during boot, ...
            pass
        time.sleep(3)
    raise SystemExit(f"timeout waiting for {name} at {url}")


def wait_for_stack():
    print("== waiting for services ==")
    for name, base in (("admin", ADMIN), ("parking", PARKING), ("billing", BILLING)):
        wait_up(name, f"{base}/actuator/health")


def admin_token():
    _, r = call("POST", f"{ADMIN}/api/v1/auth/login",
                body={"username": ADMIN_USER, "password": ADMIN_PASSWORD}, expect=200)
    return r["data"]["accessToken"]


def fresh_plate():
    """A new plate per run.

    A fixed plate inherits whatever the previous run left behind — a car still parked, a plate
    already verified — and the script then fails on setup rather than on what it meant to test.
    """
    return "51F-%05d" % random.randint(10000, 99999)


def otp_from_logs(phone=DRIVER_PHONE, container="admin-service"):
    """Read the OTP out of admin-service's log.

    The stub sender (LoggingOtpSender) logs the code instead of sending an SMS, and that log line
    is the only place it exists — nothing else can read it back, by design.
    """
    for _ in range(15):
        out = subprocess.run(["docker", "logs", "--tail", "300", container],
                             capture_output=True, text=True).stdout
        hits = re.findall(rf"\[OTP\] phone={re.escape(phone)} code=(\d+)", out)
        if hits:
            return hits[-1]
        time.sleep(2)
    raise SystemExit(f"no OTP for {phone} in {container} logs — is the stub sender still in use?")


def driver_token(phone=DRIVER_PHONE, full_name="Tai Xe E2E"):
    """Sign a driver in by OTP. Also used to re-issue a token after a plate is verified —
    verified plates are baked into the token at issue time, not read live."""
    call("POST", f"{ADMIN}/api/v1/driver/auth/request-otp", body={"phone": phone}, expect=200)
    _, r = call("POST", f"{ADMIN}/api/v1/driver/auth/verify-otp",
                body={"phone": phone, "code": otp_from_logs(phone), "fullName": full_name},
                expect=200)
    return r["data"]["accessToken"]


def verified_plate(admin, plate, phone=DRIVER_PHONE):
    """Claim `plate` for the driver and have the operator approve it. Returns a fresh token."""
    token = driver_token(phone)
    call("POST", f"{ADMIN}/api/v1/driver/me/vehicles", token, {"plateNumber": plate})

    _, r = call("GET", f"{ADMIN}/api/v1/driver-vehicles?verified=false", admin, expect=200)
    data = r["data"]
    items = data.get("content", data) if isinstance(data, dict) else data
    pending = [v for v in items if v["plateNumber"].replace(".", "") == plate.replace(".", "")]
    if pending:
        call("POST", f"{ADMIN}/api/v1/driver-vehicles/{pending[0]['id']}/verify", admin,
             {"approved": True}, expect=200)
    return driver_token(phone), bool(pending)


def arrival_time(seconds_ahead=300):
    return time.strftime("%Y-%m-%dT%H:%M:%S+07:00",
                         time.localtime(time.time() + seconds_ahead))
