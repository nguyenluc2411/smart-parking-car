"""BR-009 driver reservations + BR-003-6 zone map, against a live stack.

Covers what unit tests cannot: that Flyway applies parking V6/V7 on a real Postgres, and that a
driver who booked a slot actually gets that slot when their car arrives.
"""
from common import (PARKING, admin_token, arrival_time, call, check, finish, fresh_plate,
                    verified_plate, wait_for_stack)

PLATE = fresh_plate()
ZONE_SLOTS = 30      # headroom: other people's testing leaves cars parked in this lot

wait_for_stack()
admin = admin_token()

print("\n== BR-003-6: slots carry grid coordinates ==")
call("POST", f"{PARKING}/api/v1/slots/provision", admin,
     {"zone": "A", "count": ZONE_SLOTS}, expect=200)
_, r = call("GET", f"{PARKING}/api/v1/slots", admin, expect=200)
zone_a = [s for s in r["data"] if s["zone"] == "A"]
check("every zone-A slot has grid coordinates",
      all(s.get("gridRow") is not None and s.get("gridCol") is not None for s in zone_a),
      str([(s["slotCode"], s.get("gridRow"), s.get("gridCol")) for s in zone_a[:3]]))
cells = [(s["gridRow"], s["gridCol"]) for s in zone_a]
check("no two slots share a cell", len(cells) == len(set(cells)))
first = sorted(zone_a, key=lambda s: s["slotCode"])[0]
check("A01 sits at the top-left", (first["gridRow"], first["gridCol"]) == (0, 0),
      f"A01 at {(first['gridRow'], first['gridCol'])}")
row_two = sorted([s for s in zone_a if s["gridRow"] == 1], key=lambda s: s["gridCol"])
check("the row wraps after 10 slots", bool(row_two) and row_two[0]["slotCode"] == "A11",
      f"first of row 1 = {row_two[0]['slotCode'] if row_two else 'none'}")

print("\n== availability exposes reservedSlots ==")
_, r = call("GET", f"{PARKING}/api/v1/slots/availability", admin, expect=200)
check("reservedSlots present", "reservedSlots" in r["data"], str(r["data"]))
base_reserved = r["data"].get("reservedSlots", 0)

print("\n== driver signs in and gets a plate verified ==")
driver, was_pending = verified_plate(admin, PLATE)
check("the claim went through operator approval", was_pending,
      "plate was already verified — not a fresh claim")

print("\n== BR-009: create a reservation ==")
start = arrival_time()
status, r = call("POST", f"{PARKING}/api/v1/driver/reservations", driver,
                 {"plateNumber": PLATE, "startTime": start})
check("reservation created", status == 200, f"status={status} {str(r)[:300]}")
if status != 200:
    finish("reservations")
res = r["data"]
print(f"  held slot {res['slotCode']} until {res['holdUntil']}")
check("status is HELD", res["status"] == "HELD", res["status"])
check("response carries map coordinates",
      res.get("gridRow") is not None and res.get("gridCol") is not None, str(res))

print("\n== BR-009-3/4: the held slot leaves the walk-in pool ==")
_, r = call("GET", f"{PARKING}/api/v1/slots", admin, expect=200)
held = [s for s in r["data"] if s["slotCode"] == res["slotCode"]][0]
check("slot is RESERVED", held["status"] == "RESERVED", held["status"])
_, r = call("GET", f"{PARKING}/api/v1/slots/availability", admin, expect=200)
check("reservedSlots incremented", r["data"]["reservedSlots"] == base_reserved + 1, str(r["data"]))

print("\n== BR-003-4b: resync must NOT free a held slot ==")
_, r = call("POST", f"{PARKING}/api/v1/slots/resync", admin, expect=200)
print(f"  resync corrected {r['data']['correctedSlots']} slot(s)")
_, r = call("GET", f"{PARKING}/api/v1/slots", admin, expect=200)
after = [s for s in r["data"] if s["slotCode"] == res["slotCode"]][0]
check("slot is still RESERVED after resync", after["status"] == "RESERVED", after["status"])

print("\n== BR-009-5: a second booking for the same plate is refused ==")
status, r = call("POST", f"{PARKING}/api/v1/driver/reservations", driver,
                 {"plateNumber": PLATE, "startTime": start})
check("duplicate hold rejected with 409", status == 409, f"status={status} {str(r)[:200]}")

print("\n== BR-009-6: the booked car arrives and gets ITS slot ==")
status, r = call("POST", f"{PARKING}/api/v1/sessions/manual-entry", admin,
                 {"plateNumber": PLATE, "gateId": "GATE_ENTRY_01", "note": "e2e reservation"})
check("entry accepted", status in (200, 201), f"status={status} {str(r)[:300]}")
if status in (200, 201):
    session = r["data"]
    _, slots = call("GET", f"{PARKING}/api/v1/slots", admin, expect=200)
    taken = [s for s in slots["data"] if s["slotCode"] == res["slotCode"]][0]
    check("the held slot is the one the car took",
          taken["status"] == "OCCUPIED" and taken["currentSessionId"] == session["id"],
          f"{taken['status']} session={taken['currentSessionId']} vs {session['id']}")
    _, mine = call("GET", f"{PARKING}/api/v1/driver/reservations", driver, expect=200)
    latest = mine["data"]["content"][0]
    check("reservation is FULFILLED and linked to the session",
          latest["status"] == "FULFILLED" and latest["sessionId"] == session["id"], str(latest))

print("\n== BR-009-7: cancelling returns the slot to the pool ==")
plate2 = fresh_plate()
driver2, _ = verified_plate(admin, plate2)
status, r = call("POST", f"{PARKING}/api/v1/driver/reservations", driver2,
                 {"plateNumber": plate2, "startTime": start})
check("second reservation created", status == 200, f"status={status} {str(r)[:300]}")
if status == 200:
    res2 = r["data"]
    _, r = call("DELETE", f"{PARKING}/api/v1/driver/reservations/{res2['id']}", driver2, expect=200)
    check("status is CANCELLED", r["data"]["status"] == "CANCELLED", r["data"]["status"])
    _, r = call("GET", f"{PARKING}/api/v1/slots", admin, expect=200)
    freed = [s for s in r["data"] if s["slotCode"] == res2["slotCode"]][0]
    check("slot went back to EMPTY", freed["status"] == "EMPTY", freed["status"])

# Drive the car back out, or every run leaves one more car parked forever.
print("\n== cleanup ==")
call("POST", f"{PARKING}/api/v1/sessions/manual-exit", admin,
     {"plateNumber": PLATE, "gateId": "GATE_EXIT_01", "note": "e2e cleanup"})
print("  car exited")

finish("reservations")
