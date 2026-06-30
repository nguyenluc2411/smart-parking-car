# edge-agent

ALPR + barrier simulator for the Smart Parking System (Python 3.11 + FastAPI, port 8000).
Stateless. Publishes `parking.plate.detected`, consumes `parking.gate.command`.

- **Plate scanning** is simulated via `POST /api/v1/simulate/trigger` (you supply the plate) or
  `POST /api/v1/detect` (ALPR on an uploaded frame; `ALPR_MODE=simulate` returns a deterministic
  plate so no ML model is needed).
- **Barrier** is simulated by consuming `parking.gate.command`: the gate opens on `OPEN` and
  auto-closes after `GATE_AUTO_CLOSE_SECONDS` (BR-006-2). State is visible in the logs and the
  `edge_gate_state` Prometheus metric, and in `GET /health`.

## Run locally

```bash
cd backend/edge-agent
python -m venv venv && source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env                              # set EDGE_API_KEY, KAFKA_BOOTSTRAP_SERVERS
uvicorn app.main:app --reload --port 8000
```

Requires Kafka + parking-service running (see root `CLAUDE.md` §11). `ALPR_MODE=real` additionally
needs `pip install -r requirements-alpr.txt` and a model at `ALPR_MODEL_PATH`.

> The pinned dependency versions target **Python 3.11** (the project stack, matching the Docker
> image). On newer interpreters (3.13+) some wheels won't build — use the Docker image, or bump the
> pins. The simulator/barrier logic itself is interpreter-agnostic.

## End-to-end smoke test (entry → barrier opens)

```bash
API=http://localhost:8000
KEY=dev-edge-key

# 1. Car arrives at the entry camera (simulated scan)
curl -s -X POST $API/api/v1/simulate/trigger \
  -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"gate_id":"GATE_ENTRY_01","plate_number":"51F-123.45","direction":"IN","simulate_confidence":0.95}'

# -> edge publishes parking.plate.detected
# -> parking-service creates a session, publishes parking.gate.command{OPEN} + session.created
# -> edge consumes the command: log "🚧 GATE GATE_ENTRY_01 OPEN ..." then auto-CLOSED after 10s
# -> billing (on exit) + admin audit follow the rest of the pipeline

# 2. Watch the barrier state
curl -s $API/health           # {"gates": {"GATE_ENTRY_01": "OPEN"}, ...}
curl -s $API/metrics | grep edge_gate_state

# 3. Car leaves (exit scan) -> session closed -> invoice calculated
curl -s -X POST $API/api/v1/simulate/trigger \
  -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"gate_id":"GATE_EXIT_01","plate_number":"51F-123.45","direction":"OUT","simulate_confidence":0.95}'
```

## Tests

```bash
pip install -r requirements-dev.txt
pytest
```
