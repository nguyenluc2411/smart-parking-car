"""GET/PUT /api/v1/config — view and tune runtime ALPR config."""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.config import RuntimeConfig, Settings, get_settings
from app.dependencies import get_runtime
from app.schemas import ConfigResponse, ConfigUpdateRequest

router = APIRouter()


def _to_response(runtime: RuntimeConfig, settings: Settings) -> ConfigResponse:
    return ConfigResponse(
        confidenceThreshold=runtime.confidence_threshold,
        retryAttempts=runtime.retry_attempts,
        gateMapping=settings.gate_mapping,
        modelVersion=settings.model_version,
    )


@router.get("/config", response_model=ConfigResponse)
async def get_config(
    runtime: RuntimeConfig = Depends(get_runtime),
    settings: Settings = Depends(get_settings),
) -> ConfigResponse:
    return _to_response(runtime, settings)


@router.put("/config", response_model=ConfigResponse)
async def update_config(
    request: ConfigUpdateRequest,
    runtime: RuntimeConfig = Depends(get_runtime),
    settings: Settings = Depends(get_settings),
) -> ConfigResponse:
    if request.confidenceThreshold is not None:
        runtime.confidence_threshold = request.confidenceThreshold
    if request.retryAttempts is not None:
        runtime.retry_attempts = request.retryAttempts
    return _to_response(runtime, settings)
