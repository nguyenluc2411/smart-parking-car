"""Billing over HTTP: collected (BR-005-8), invoice breakdown (BR-004), outage cash (BR-005-7).

The money paths that unit tests can only assert in isolation — here they run through Kafka, a real
invoice, and the payment guard.
"""
import time

from common import (BILLING, PARKING, admin_token, call, check, finish, fresh_plate,
                    wait_for_stack)

PLATE = fresh_plate()

wait_for_stack()
admin = admin_token()
today = time.strftime("%Y-%m-%d")

print("\n== BR-005-8: reports expose `collected` ==")
_, r = call("GET", f"{BILLING}/api/v1/billing/report/daily?date={today}", admin, expect=200)
daily = r["data"]
check("daily has `collected`", "collected" in daily, str(daily)[:200])
check("daily dropped `revenueByMethod`", "revenueByMethod" not in daily)
before = daily.get("collected") or {}
check("collected carries cash/gateway/total/byMethod",
      all(k in before for k in ("cashTotal", "gatewayTotal", "total", "byMethod")),
      str(before)[:200])
check("total == cashTotal + gatewayTotal",
      abs(before.get("total", 0) - (before.get("cashTotal", 0) + before.get("gatewayTotal", 0)))
      < 0.01, str(before))

_, r = call("GET", f"{BILLING}/api/v1/billing/report/monthly", admin, expect=200)
check("monthly has `collected` too", "collected" in r["data"], str(r["data"])[:160])
check("monthly dropped `revenueByMethod`", "revenueByMethod" not in r["data"])

print("\n== park a car and take it out, so billing issues an invoice ==")
call("POST", f"{PARKING}/api/v1/sessions/manual-entry", admin,
     {"plateNumber": PLATE, "gateId": "GATE_ENTRY_01", "note": "e2e billing"})
status, r = call("POST", f"{PARKING}/api/v1/sessions/manual-exit", admin,
                 {"plateNumber": PLATE, "gateId": "GATE_EXIT_01", "note": "e2e billing"})
check("manual exit accepted", status in (200, 201), f"{status}: {str(r)[:200]}")
session_id = (r.get("data") or {}).get("id")

invoice = None
for _ in range(20):          # the invoice arrives over Kafka, so poll rather than assume
    st, ir = call("GET", f"{BILLING}/api/v1/billing/sessions/{session_id}", admin)
    if st == 200:
        invoice = ir["data"]
        break
    time.sleep(2)
check("invoice was issued for the session", invoice is not None, "no invoice after 40s")
if invoice is None:
    finish("billing")
print(f"  invoice {invoice['invoiceId']} amount={invoice['amount']} status={invoice['status']}")

print("\n== BR-004: the invoice explains itself ==")
breakdown = invoice.get("breakdown")
check("breakdown present on a fresh invoice", breakdown is not None, str(invoice)[:250])
if breakdown:
    check("breakdown has the three lines + block size",
          all(k in breakdown
              for k in ("blockMinutes", "normal", "peak", "overnight", "minChargeApplied")),
          str(breakdown))
    lines = sum((breakdown[k] or {}).get("amount", 0) for k in ("normal", "peak", "overnight"))
    check("lines reconcile to amount (or min-charge floored it)",
          abs(lines - invoice["amount"]) < 0.01 or breakdown["minChargeApplied"],
          f"lines={lines} amount={invoice['amount']} floored={breakdown['minChargeApplied']}")
check("tariff constants present",
      all(invoice.get(k) is not None for k in ("peakMultiplier", "overnightFlat", "minCharge")),
      f"peak={invoice.get('peakMultiplier')} night={invoice.get('overnightFlat')}"
      f" min={invoice.get('minCharge')}")

print("\n== BR-005-7: outage cash keyed in afterwards ==")
pay_url = f"{BILLING}/api/v1/billing/sessions/{session_id}/pay"

status, r = call("POST", pay_url, admin,
                 {"method": "CASH", "amountPaid": invoice["amount"], "note": "x",
                  "paidAt": "2026-07-22T21:40:00+07:00"})
check("paidAt on a live CASH payment is refused", status in (400, 409),
      f"got {status}: {str(r)[:200]}")

status, r = call("POST", pay_url, admin,
                 {"method": "CASH_OFFLINE", "amountPaid": invoice["amount"], "note": "no voucher"})
check("CASH_OFFLINE without a voucher serial is refused", status in (400, 409),
      f"got {status}: {str(r)[:200]}")

status, r = call("POST", pay_url, admin,
                 {"method": "CASH_OFFLINE", "amountPaid": invoice["amount"],
                  "offlineVoucherNo": "PV-E2E-" + time.strftime("%H%M%S"),
                  "paidAt": time.strftime("%Y-%m-%dT%H:%M:%S+07:00"),
                  "note": "thu tay luc mat dien"})
check("CASH_OFFLINE with voucher + paidAt settles the invoice", status == 200,
      f"got {status}: {str(r)[:250]}")
if status == 200:
    check("invoice is PAID", (r.get("data") or {}).get("status") == "PAID", str(r)[:200])

print("\n== BR-005-2: the same invoice cannot be paid twice ==")
status, r = call("POST", pay_url, admin,
                 {"method": "CASH", "amountPaid": invoice["amount"], "note": "second attempt"})
check("second payment refused with 409", status == 409, f"got {status}: {str(r)[:200]}")

print("\n== the outage cash lands in today's collected ==")
_, r = call("GET", f"{BILLING}/api/v1/billing/report/daily?date={today}", admin, expect=200)
after = r["data"]["collected"]
check("CASH_OFFLINE appears in byMethod",
      any(m["method"] == "CASH_OFFLINE" for m in after["byMethod"]), str(after["byMethod"]))
check("cashTotal grew by the invoice amount",
      abs(after["cashTotal"] - (before["cashTotal"] + invoice["amount"])) < 0.01,
      f"before={before['cashTotal']} after={after['cashTotal']} invoice={invoice['amount']}")

finish("billing")
