import asyncio

import pytest

from app.services.barrier import CLOSED, OPEN, BarrierSimulator


@pytest.mark.asyncio
async def test_open_then_auto_closes():
    barrier = BarrierSimulator(auto_close_seconds=0)  # BR-006-2 timer fires ~immediately
    await barrier.handle_command({"gateId": "GATE_ENTRY_01", "command": "OPEN", "sessionId": "s1"})
    assert barrier.state_of("GATE_ENTRY_01") == OPEN

    await asyncio.sleep(0.05)  # let the auto-close task run
    assert barrier.state_of("GATE_ENTRY_01") == CLOSED


@pytest.mark.asyncio
async def test_explicit_close_command():
    barrier = BarrierSimulator(auto_close_seconds=100)
    await barrier.handle_command({"gateId": "G1", "command": "OPEN"})
    await barrier.handle_command({"gateId": "G1", "command": "CLOSE"})
    assert barrier.state_of("G1") == CLOSED


@pytest.mark.asyncio
async def test_malformed_command_ignored():
    barrier = BarrierSimulator(auto_close_seconds=100)
    await barrier.handle_command({"command": "OPEN"})           # no gateId
    await barrier.handle_command({"gateId": "G1", "command": "X"})  # bad command
    assert barrier.snapshot() == {}
    await barrier.shutdown()


@pytest.mark.asyncio
async def test_emits_gate_state_on_open_and_auto_close():
    events: list[tuple[str, str, str]] = []

    async def on_state_change(gate_id: str, status: str, reason: str) -> None:
        events.append((gate_id, status, reason))

    barrier = BarrierSimulator(auto_close_seconds=0, on_state_change=on_state_change)
    await barrier.handle_command({"gateId": "GATE_ENTRY_01", "command": "OPEN"})
    await asyncio.sleep(0.05)  # let the auto-close task run

    # parking-service relies on this CLOSED event to clear the stale OPEN (BR-006-2).
    assert events == [
        ("GATE_ENTRY_01", OPEN, "command"),
        ("GATE_ENTRY_01", CLOSED, "auto"),
    ]


@pytest.mark.asyncio
async def test_announce_initial_emits_closed_on_startup():
    events: list[tuple[str, str, str]] = []

    async def on_state_change(gate_id: str, status: str, reason: str) -> None:
        events.append((gate_id, status, reason))

    barrier = BarrierSimulator(auto_close_seconds=100, on_state_change=on_state_change)
    await barrier.announce_initial(["GATE_ENTRY_01", "GATE_EXIT_01"])

    assert events == [
        ("GATE_ENTRY_01", CLOSED, "startup"),
        ("GATE_EXIT_01", CLOSED, "startup"),
    ]
