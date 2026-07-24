# Auxiliary gate and offline PWA

## Supported incidents

1. Main power is unavailable but the booth device has Internet: the operator records entry/exit at
   the barrierless auxiliary gate and the event is written directly to the cloud.
2. Main power and Internet are unavailable but the booth device still has battery: the same screen
   stores events in IndexedDB and replays them in occurrence-time order after connectivity returns.

If the booth device has no power, software has no input channel. Site handover must therefore include
a charged phone/tablet or power bank and a human emergency procedure.

## Data guarantees

- The device generates a UUID for every entry/exit. Retrying a replay does not create a second event.
- `occurredAt` is the actual gate time, so synchronization delay does not shorten the parking stay.
- Entry events are replayed before exit events.
- A failed business validation remains visible in the queue for operator review.
- Auxiliary gates have `has_barrier = false`; no physical barrier command is emitted.

## Production requirements

- Deploy the admin dashboard over HTTPS. Service workers are not available on ordinary HTTP origins
  except `localhost`.
- The operator must open `/outage` once while online so the app shell and gate list are cached.
- Keep the browser profile and its IndexedDB data; clearing site data deletes unsynchronized events.
- Ensure access/refresh tokens remain valid for the maximum supported outage period.

## Demo

1. Build and start the production admin dashboard.
2. Log in, open `/outage`, and confirm the cloud indicator is green.
3. In browser DevTools, switch Network to Offline.
4. Record an entry and an exit; both appear under `Chờ đồng bộ`.
5. Restore Network. The page automatically replays the queue, or the operator can press `Đồng bộ`.
6. Re-send the same client event UUID to demonstrate backend idempotency.
