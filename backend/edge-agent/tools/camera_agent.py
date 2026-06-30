#!/usr/bin/env python3
"""Automated camera agent — feeds a webcam into the ALPR pipeline with NO human in the loop.

Simulates a real entry/exit camera: capture frame -> POST /api/v1/detect -> (edge publishes
parking.plate.detected -> parking opens the gate) -> measure the camera→gate latency by polling
the barrier state. This is the "AI automates everything" loop; results are logged to CSV for the
research KPIs (BR: latency camera→barie ≤ 3s; recognition accuracy if --expect is given).

Multi-frame: each trigger captures a short BURST of frames and lets them vote (see
``app.services.aggregate``) instead of trusting one noisy frame — a plate seen across several
frames is far more trustworthy than a lucky single read, which is how we push the recognition KPI.

Setup:  pip install opencv-python requests
Run:    python tools/camera_agent.py --api-key <EDGE_API_KEY> --gate GATE_ENTRY_01 --direction IN
Keys:   SPACE = capture & send now, q = quit.  --interval N = auto-capture every N seconds.
        --frames N = frames voted per trigger; --min-votes / --min-conf = consensus thresholds.
"""
from __future__ import annotations

import argparse
import csv
import pathlib
import sys
import time

import cv2
import requests

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))

from app.services.aggregate import decide, tally  # noqa: E402


def poll_gate_open(edge: str, gate: str, timeout_s: float) -> float | None:
    """Poll GET /health until the gate reads OPEN; return the perf-counter timestamp (or None)."""
    deadline = time.perf_counter() + timeout_s
    while time.perf_counter() < deadline:
        try:
            gates = requests.get(f"{edge}/health", timeout=2).json().get("gates", {})
            if gates.get(gate) == "OPEN":
                return time.perf_counter()
        except requests.RequestException:
            pass
        time.sleep(0.05)
    return None


def send_frame(edge, api_key, gate, direction, frame):
    ok, buf = cv2.imencode(".jpg", frame)
    if not ok:
        return None
    return requests.post(
        f"{edge}/api/v1/detect",
        files={"image": ("frame.jpg", buf.tobytes(), "image/jpeg")},
        data={"gate_id": gate, "direction": direction},
        headers={"X-API-Key": api_key},
        timeout=15,
    )


def run_burst(cap, args):
    """Capture up to ``args.frames`` frames, POST each, collect the (plate, confidence) reads.

    Stops early the moment the reads-so-far reach consensus (``decide`` returns non-None), so a
    clean plate opens the gate without waiting for the whole burst — keeps camera→gate latency low.
    Returns ``(reads, decisive, last_json)`` where ``reads`` is every readable frame, ``decisive``
    is the accepted :class:`Consensus` (or None), and ``last_json`` is the last detect response.
    """
    reads, decisive, last = [], None, None
    for i in range(args.frames):
        ok, frame = cap.read()
        if not ok:
            break
        cv2.imshow("camera-agent", frame)
        cv2.waitKey(1)
        resp = send_frame(args.edge, args.api_key, args.gate, args.direction, frame)
        if resp is not None and resp.status_code == 200:
            last = resp.json()
            reads.append((last["plateNumber"], float(last["confidence"])))
            decisive = decide(reads, min_votes=args.min_votes, min_mean_confidence=args.min_conf)
            if decisive is not None:
                break
        if args.frame_gap > 0 and i < args.frames - 1:
            time.sleep(args.frame_gap)
    return reads, decisive, last


def norm(p: str) -> str:
    return p.upper().replace(" ", "")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--edge", default="http://localhost:8000")
    ap.add_argument("--api-key", required=True)
    ap.add_argument("--gate", default="GATE_ENTRY_01")
    ap.add_argument("--direction", default="IN", choices=["IN", "OUT"])
    ap.add_argument("--source", default="0", help="webcam index (0) or video file path")
    ap.add_argument("--interval", type=float, default=0.0, help=">0 = auto-capture every N seconds")
    ap.add_argument("--frames", type=int, default=5, help="frames captured & voted per trigger")
    ap.add_argument("--frame-gap", type=float, default=0.12, help="seconds between burst frames")
    ap.add_argument("--min-votes", type=int, default=2, help="frames that must agree to accept a plate")
    ap.add_argument("--min-conf", type=float, default=0.80, help="min mean OCR confidence to accept")
    ap.add_argument("--cooldown", type=float, default=8.0, help="min seconds between triggers")
    ap.add_argument("--gate-timeout", type=float, default=5.0)
    ap.add_argument("--expect", default=None, help="ground-truth plate, to score accuracy")
    ap.add_argument("--out", default="camera_agent_log.csv")
    args = ap.parse_args()
    args.min_votes = max(1, min(args.min_votes, args.frames))  # never need more votes than frames

    source = int(args.source) if args.source.isdigit() else args.source
    cap = cv2.VideoCapture(source)
    if not cap.isOpened():
        print("Cannot open camera/source", file=sys.stderr)
        sys.exit(1)

    out = open(args.out, "a", newline="", encoding="utf-8")
    writer = csv.writer(out)
    if out.tell() == 0:
        writer.writerow(["ts", "plate", "confidence", "votes", "frames", "accepted",
                         "alpr_ms", "cam_to_gate_s", "expected", "correct"])

    total = correct = 0
    last_trigger = -1e9
    auto = args.interval > 0
    print(f"camera-agent → {args.edge}  gate={args.gate} dir={args.direction}  "
          f"vote {args.frames} frames (≥{args.min_votes} agree, conf≥{args.min_conf})"
          + (f"  auto every {args.interval}s" if auto else "  (SPACE=capture, q=quit)"))

    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            cv2.imshow("camera-agent", frame)
            key = cv2.waitKey(1) & 0xFF
            if key == ord("q"):
                break

            now = time.perf_counter()
            triggered = key == ord(" ") or (auto and now - last_trigger >= args.interval)
            if not (triggered and now - last_trigger >= args.cooldown):
                continue
            last_trigger = now

            t0 = time.perf_counter()
            reads, decisive, last = run_burst(cap, args)
            if last is None:  # whole burst was unreadable / all rejected by the edge
                print(f"  no readable plate across {args.frames} frames")
                continue

            # decisive = accepted consensus; else fall back to the best-ranked plate (logged,
            # but flagged accepted=False) so a near-miss is still recorded for the KPI report.
            winner = decisive or tally(reads)[0]
            plate, votes, frames_read = winner.plate_number, winner.votes, winner.frames

            t_open = poll_gate_open(args.edge, args.gate, args.gate_timeout)
            cam_to_gate = (t_open - t0) if t_open else None
            is_correct = ""
            if args.expect is not None:
                is_correct = norm(plate) == norm(args.expect)
                total += 1
                correct += int(is_correct)

            latency = f"{cam_to_gate:.2f}s" if cam_to_gate is not None else "n/a (gate not observed)"
            tag = "ACCEPT" if decisive else "weak"
            print(f"  [{tag}] plate={plate} votes={votes}/{frames_read} "
                  f"conf={winner.mean_confidence:.2f} alpr={last['processingMs']}ms  "
                  f"camera→gate={latency}")
            writer.writerow([f"{time.time():.0f}", plate, f"{winner.mean_confidence:.3f}",
                             votes, frames_read, bool(decisive), last["processingMs"],
                             f"{cam_to_gate:.3f}" if cam_to_gate is not None else "",
                             args.expect or "", is_correct])
            out.flush()
    finally:
        cap.release()
        cv2.destroyAllWindows()
        out.close()
        if total:
            print(f"\nAccuracy: {correct}/{total} = {correct / total * 100:.1f}%")


if __name__ == "__main__":
    main()
