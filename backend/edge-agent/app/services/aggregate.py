"""Multi-frame consensus for ALPR.

A single frame is noisy: motion blur, glare, a tilted plate or one OCR'd character can flip the
read. A real camera sees a car for a few seconds, so we read a BURST of frames and let them vote.
Because :func:`app.plate.canonicalize` already forces every read to the same ``51F-12345`` shape,
correct frames collapse onto the *same* string while noisy frames scatter — so a plate that shows
up across several frames is far more trustworthy than a lucky high-confidence single read.

This module is pure (no cv2 / ML / network) so it is cheap to unit-test and is reused by both
``tools/camera_agent.py`` (live burst) and ``tools/alpr_eval.py`` (offline grouped frames).
"""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass


@dataclass
class Consensus:
    plate_number: str
    votes: int               # how many frames read this exact (canonical) plate
    frames: int              # total frames that produced ANY readable plate
    mean_confidence: float   # mean OCR confidence across this plate's votes
    score: float             # votes * mean_confidence — the ranking key


def tally(reads) -> list[Consensus]:
    """Group per-frame reads by canonical plate and rank them best-first.

    ``reads`` is an iterable of ``(plate_number, confidence)`` from frames that produced a plate
    (drop a frame's entry when ALPR returned nothing). Plates are assumed already canonicalised, so
    grouping is an exact string match. Ranking key is ``votes * mean_confidence`` so a plate seen on
    several frames beats a single noisy frame that happened to score high. Ties break by votes, then
    mean confidence, then plate string (deterministic).
    """
    confs: dict[str, list[float]] = defaultdict(list)
    for plate, conf in reads:
        confs[plate].append(float(conf))

    frames = sum(len(v) for v in confs.values())
    out = []
    for plate, cs in confs.items():
        mean = sum(cs) / len(cs)
        out.append(Consensus(plate, len(cs), frames, mean, len(cs) * mean))
    out.sort(key=lambda c: (c.score, c.votes, c.mean_confidence, c.plate_number), reverse=True)
    return out


def decide(reads, *, min_votes: int = 2, min_mean_confidence: float = 0.80) -> Consensus | None:
    """Return the winning plate only if it is trustworthy enough, else ``None``.

    Accepts the top-ranked plate when it was seen on at least ``min_votes`` frames AND its mean
    OCR confidence clears ``min_mean_confidence``. Callers can invoke this after each new frame and
    stop the burst early the moment it returns non-``None`` (keeps camera→gate latency bounded).
    """
    ranked = tally(reads)
    if not ranked:
        return None
    best = ranked[0]
    if best.votes >= min_votes and best.mean_confidence >= min_mean_confidence:
        return best
    return None
