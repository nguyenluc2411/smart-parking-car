"""BR-009-3b: an admin edit must not silently break a hold already promised to a driver.

Without these guards the slot is freed while the booking stays live: a walk-in takes it, and the
booked driver's car later lands on a slot that already has a car on it.
"""
from common import (PARKING, admin_token, arrival_time, call, check, finish, fresh_plate,
                    verified_plate, wait_for_stack)

PLATE = fresh_plate()

wait_for_stack()
admin = admin_token()
driver, _ = verified_plate(admin, PLATE)

status, r = call("POST", f"{PARKING}/api/v1/driver/reservations", driver,
                 {"plateNumber": PLATE, "startTime": arrival_time()})
if status != 200:
    check("could create a hold to test against", False, f"status={status} {str(r)[:250]}")
    finish("slot guards")
res = r["data"]
print(f"held slot {res['slotCode']} (zone {res['zone']})\n")

status, r = call("PATCH", f"{PARKING}/api/v1/slots/{res['slotId']}/status", admin,
                 {"status": "MAINTENANCE"})
check("PATCH status on a booked slot -> 409", status == 409, f"got {status}: {str(r)[:200]}")

status, r = call("DELETE", f"{PARKING}/api/v1/slots/{res['slotId']}", admin)
check("DELETE a booked slot -> 409", status == 409, f"got {status}: {str(r)[:200]}")

# Shrink the zone so the booked slot falls in the surplus range that provision would delete.
_, r = call("GET", f"{PARKING}/api/v1/slots", admin, expect=200)
zone_slots = sorted([s for s in r["data"] if s["zone"] == res["zone"]],
                    key=lambda s: s["slotCode"])
rank = [s["slotCode"] for s in zone_slots].index(res["slotCode"]) + 1
status, r = call("POST", f"{PARKING}/api/v1/slots/provision", admin,
                 {"zone": res["zone"], "count": rank - 1})
check("provision shrinking past a booked slot -> 409", status == 409,
      f"got {status}: {str(r)[:250]}")

_, r = call("GET", f"{PARKING}/api/v1/slots", admin, expect=200)
still = [s for s in r["data"] if s["slotCode"] == res["slotCode"]]
check("the booked slot still exists and is RESERVED",
      bool(still) and still[0]["status"] == "RESERVED", str(still))

call("DELETE", f"{PARKING}/api/v1/driver/reservations/{res['id']}", driver, expect=200)
print("\ncleaned up the hold")

finish("slot guards")
