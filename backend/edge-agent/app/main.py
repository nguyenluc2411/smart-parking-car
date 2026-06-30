"""edge-agent — FastAPI app (port 8000).

Stateless ALPR + barrier simulator. Publishes parking.plate.detected, consumes
parking.gate.command. See README.md for the end-to-end flow.
"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI

from app.config import RuntimeConfig, get_settings
from app.kafka.consumer import GateCommandConsumer
from app.kafka.producer import GateStateProducer, PlateEventProducer
from app.routers import config as config_router
from app.routers import detect as detect_router
from app.routers import health as health_router
from app.routers import simulate as simulate_router
from app.security import require_api_key
from app.services.alpr import AlprService
from app.services.barrier import BarrierSimulator
from app.services.storage import FrameStorage

logger = logging.getLogger("edge-agent")


@asynccontextmanager
async def lifespan(app: FastAPI):
    logging.basicConfig(level=logging.INFO,
                        format="%(asctime)s %(levelname)s %(name)s — %(message)s")
    settings = get_settings()
    app.state.settings = settings
    app.state.runtime = RuntimeConfig(settings.confidence_threshold, settings.retry_attempts)
    app.state.alpr = AlprService(
        settings.alpr_mode, settings.alpr_model_path, settings.model_version,
        detector=settings.alpr_detector, ocr_languages=settings.alpr_ocr_languages,
        ocr_gpu=settings.alpr_ocr_gpu, preprocess=settings.alpr_preprocess,
        grammar_fix=settings.alpr_grammar_fix, char_model_path=settings.alpr_char_model_path,
        ocr_engine=settings.alpr_ocr_engine, multi_candidate=settings.alpr_multi_candidate)

    # Warm up the ALPR engine so a paddle/model failure surfaces NOW (boot + /health), not on the
    # first car. Blocking (loads torch/paddle) -> run off the event loop. Soft-readiness like Kafka:
    # log loud + report via /health instead of crashing the dev tool.
    app.state.alpr_ready = settings.alpr_mode != "real"
    app.state.alpr_error = None
    import asyncio
    try:
        await asyncio.to_thread(app.state.alpr.warmup)
        app.state.alpr_ready = True
    except Exception as exc:  # noqa: BLE001 — surfaced via /health
        app.state.alpr_error = f"{type(exc).__name__}: {exc}"
        logger.exception(
            "ALPR warmup FAILED (engine=%s) — /detect will return 422 for every frame until fixed. "
            "If engine=paddle, verify the image has libgomp1 + pre-downloaded paddle models.",
            settings.alpr_ocr_engine)

    app.state.storage = FrameStorage(
        settings.minio_endpoint, settings.minio_access_key, settings.minio_secret_key,
        settings.minio_bucket, settings.minio_secure)
    logger.info("Frame storage %s", "enabled" if app.state.storage.enabled else "disabled (no MinIO config)")

    gate_state_producer = GateStateProducer(settings.kafka_bootstrap_servers, settings.topic_gate_state)
    barrier = BarrierSimulator(settings.gate_auto_close_seconds,
                               on_state_change=gate_state_producer.publish)
    producer = PlateEventProducer(settings.kafka_bootstrap_servers, settings.topic_plate_detected)
    consumer = GateCommandConsumer(settings.kafka_bootstrap_servers, settings.topic_gate_command,
                                   settings.consumer_group, barrier)
    app.state.barrier = barrier
    app.state.producer = producer
    app.state.gate_state_producer = gate_state_producer
    app.state.consumer = consumer
    app.state.kafka_connected = False

    try:
        await producer.start()
        await gate_state_producer.start()
        await consumer.start()
        # Restart => relay de-energized => all gates CLOSED; sync parking-service (BR-006-2).
        await barrier.announce_initial(settings.gate_mapping.keys())
        app.state.kafka_connected = True
    except Exception:  # readiness is reported via /health rather than crashing the dev tool
        logger.exception("Kafka unavailable at startup; /detect & /simulate will fail until it recovers")

    try:
        yield
    finally:
        await consumer.stop()
        await producer.stop()
        await gate_state_producer.stop()
        await barrier.shutdown()


app = FastAPI(title="edge-agent", version="1.0.0", lifespan=lifespan)

_secured = [Depends(require_api_key)]
app.include_router(simulate_router.router, prefix="/api/v1", dependencies=_secured)
app.include_router(detect_router.router, prefix="/api/v1", dependencies=_secured)
app.include_router(config_router.router, prefix="/api/v1", dependencies=_secured)
app.include_router(health_router.router)
