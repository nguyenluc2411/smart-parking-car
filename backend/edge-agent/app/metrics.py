"""Prometheus metrics (exposed at GET /metrics)."""
from __future__ import annotations

from prometheus_client import Counter, Gauge, Histogram

plate_detected_total = Counter(
    "edge_plate_detected_total", "plate.detected events published", ["direction", "source"]
)
detect_requests_total = Counter(
    "edge_detect_requests_total", "Detect requests handled", ["result"]
)
burst_detect_total = Counter(
    "edge_burst_detect_total", "Burst (multi-frame) detect requests handled", ["result"]
)
gate_commands_total = Counter(
    "edge_gate_commands_total", "gate.command messages consumed", ["command"]
)
detection_processing_ms = Histogram(
    "edge_detection_processing_ms", "ALPR processing time (ms)",
    buckets=(50, 100, 200, 400, 800, 1600, 3000),
)
# 1 = OPEN, 0 = CLOSED — the simulated barrier state per gate.
gate_state = Gauge("edge_gate_state", "Simulated barrier state (1=OPEN, 0=CLOSED)", ["gate_id"])
