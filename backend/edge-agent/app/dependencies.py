"""FastAPI dependency accessors for the per-app singletons (set in the lifespan)."""
from __future__ import annotations

from fastapi import Request

from app.config import RuntimeConfig
from app.kafka.producer import PlateEventProducer
from app.services.alpr import AlprService
from app.services.barrier import BarrierSimulator
from app.services.storage import FrameStorage


def get_producer(request: Request) -> PlateEventProducer:
    return request.app.state.producer


def get_storage(request: Request) -> FrameStorage:
    return request.app.state.storage


def get_barrier(request: Request) -> BarrierSimulator:
    return request.app.state.barrier


def get_runtime(request: Request) -> RuntimeConfig:
    return request.app.state.runtime


def get_alpr(request: Request) -> AlprService:
    return request.app.state.alpr
