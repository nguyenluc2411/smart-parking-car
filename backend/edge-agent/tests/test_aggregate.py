from pytest import approx

from app.services.aggregate import Consensus, decide, tally


def test_tally_groups_and_ranks_by_votes_times_confidence():
    ranked = tally([
        ("51F-12345", 0.90),
        ("51F-12345", 0.80),   # same plate twice -> 2 votes
        ("51F-12340", 0.95),   # one noisy high-conf read
    ])
    assert ranked[0].plate_number == "51F-12345"
    assert ranked[0].votes == 2
    assert ranked[0].mean_confidence == approx(0.85)
    assert ranked[0].frames == 3
    assert ranked[0].score == approx(1.70)
    # the lucky single read loses despite higher confidence
    assert ranked[1].plate_number == "51F-12340"


def test_tally_empty_returns_empty_list():
    assert tally([]) == []


def test_decide_accepts_when_votes_and_confidence_clear_thresholds():
    result = decide([("51F-12345", 0.90), ("51F-12345", 0.86)], min_votes=2, min_mean_confidence=0.80)
    assert isinstance(result, Consensus)
    assert result.plate_number == "51F-12345"


def test_decide_rejects_single_vote_below_min_votes():
    assert decide([("51F-12345", 0.99)], min_votes=2, min_mean_confidence=0.80) is None


def test_decide_rejects_when_mean_confidence_too_low():
    reads = [("51F-12345", 0.70), ("51F-12345", 0.72)]
    assert decide(reads, min_votes=2, min_mean_confidence=0.80) is None


def test_decide_empty_returns_none():
    assert decide([], min_votes=1, min_mean_confidence=0.0) is None


def test_decide_tie_break_is_deterministic():
    # two plates, identical score -> higher votes wins, fully deterministic
    reads = [("51F-12345", 0.80), ("51F-12345", 0.80), ("30A-11122", 0.80)]
    assert decide(reads, min_votes=2, min_mean_confidence=0.50).plate_number == "51F-12345"
