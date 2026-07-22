"""GET /health and GET /metrics (public — no API key)."""
from __future__ import annotations

from fastapi import APIRouter, Request, Response
from fastapi.responses import JSONResponse
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

router = APIRouter()


@router.get("/health")
async def health(request: Request):
    connected = getattr(request.app.state, "kafka_connected", False)
    alpr_ready = getattr(request.app.state, "alpr_ready", True)
    alpr_error = getattr(request.app.state, "alpr_error", None)
    settings = request.app.state.settings
    auto = getattr(request.app.state, "auto_capture", None)
    auto_body = None
    if auto is not None:
        st = auto.status()
        auto_body = {
            "enabled": st.enabled,
            "running": st.running,
            "cooldownSeconds": settings.auto_capture_cooldown,
            "lastError": st.last_error,
            "lastPlate": st.last_plate,
            "lastPublished": st.last_published,
            "gates": [
                {
                    "gateId": g.gate_id,
                    "direction": g.direction,
                    "state": g.state,
                    "cooldownRemainingSec": round(g.cooldown_until, 1) if g.cooldown_until else 0,
                    "lastPlate": g.last_plate,
                }
                for g in st.gates
            ],
        }
    body = {
        "status": "UP" if connected and alpr_ready else "DOWN",
        "kafka": connected,
        "alprMode": settings.alpr_mode,
        "alprEngine": settings.alpr_ocr_engine,
        "alprReady": alpr_ready,
        "gates": request.app.state.barrier.snapshot(),
        "autoCapture": auto_body,
    }
    if alpr_error:  # surface the boot-time engine failure so it's diagnosable without log access
        body["alprError"] = alpr_error
    return JSONResponse(status_code=200 if connected and alpr_ready else 503, content=body)


@router.get("/metrics")
async def metrics() -> Response:
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)
