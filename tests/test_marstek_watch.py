"""Tests for the standalone read-only LAN diagnostic tracker."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from tools.marstek_watch import ConnectivityTracker, event_writer, parse_args


def test_first_success_and_transient_timeout() -> None:
    tracker = ConnectivityTracker(offline_after=3)
    assert tracker.observe(True, 0) == [{"event": "online"}]
    assert tracker.observe(False, 30) == []
    assert tracker.state == "online"
    assert tracker.observe(True, 60) == [
        {"event": "transient_recovery", "missed_probes": 1}
    ]
    assert tracker.consecutive_failures == 0


def test_confirmed_outage_and_recovery() -> None:
    tracker = ConnectivityTracker(offline_after=3)
    tracker.observe(True, 0)
    assert tracker.observe(False, 10) == []
    assert tracker.observe(False, 20) == []
    assert tracker.observe(False, 30) == [{"event": "offline", "missed_probes": 3}]
    assert tracker.observe(False, 40) == []
    assert tracker.observe(True, 50) == [
        {"event": "recovered", "estimated_outage_seconds": 40.0, "missed_probes": 4}
    ]
    assert tracker.state == "online"


def test_starting_offline() -> None:
    tracker = ConnectivityTracker(offline_after=2)
    assert tracker.observe(False, 3) == []
    assert tracker.observe(False, 8) == [{"event": "offline", "missed_probes": 2}]
    assert tracker.observe(True, 12) == [
        {"event": "recovered", "estimated_outage_seconds": 9.0, "missed_probes": 2}
    ]


def test_jsonl_events(tmp_path: Path) -> None:
    log = tmp_path / "nested" / "events.jsonl"
    event_writer(log, {"event": "online"})
    event_writer(log, {"event": "probe_failed", "latency_ms": 100})
    rows = [json.loads(line) for line in log.read_text(encoding="utf-8").splitlines()]
    assert [x["event"] for x in rows] == ["online", "probe_failed"]
    assert "timestamp_utc" in rows[0]


def test_argument_validation() -> None:
    assert parse_args(["--host", "192.168.1.50"]).port == 30000
    with pytest.raises(SystemExit):
        parse_args(["--host", "192.168.1.50", "--interval", "1"])
    with pytest.raises(SystemExit):
        parse_args(["--host", "192.168.1.50", "--port", "0"])
