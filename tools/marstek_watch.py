"""Read-only LAN reachability monitor for Marstek energy storage devices.

Runs independently of Home Assistant. It never sends configuration or reset
commands. A missing UDP response is *not* proof that the inverter rebooted.
"""

from __future__ import annotations

import argparse
import asyncio
from dataclasses import dataclass
from datetime import datetime, timezone
import json
from pathlib import Path
import time
from typing import Any


@dataclass
class ConnectivityTracker:
    """Classify consecutive failed probes without mislabeling one timeout."""

    offline_after: int = 3
    state: str = "unknown"
    consecutive_failures: int = 0
    first_failure_at: float | None = None

    def __post_init__(self) -> None:
        if self.offline_after < 1:
            raise ValueError("offline_after must be at least 1")

    def observe(self, success: bool, now: float) -> list[dict[str, Any]]:
        """Return transition events; times are monotonic seconds."""
        events: list[dict[str, Any]] = []
        if success:
            if self.state == "offline":
                events.append(
                    {
                        "event": "recovered",
                        "estimated_outage_seconds": round(
                            max(
                                0.0,
                                now
                                - (
                                    self.first_failure_at
                                    if self.first_failure_at is not None
                                    else now
                                ),
                            ),
                            1,
                        ),
                        "missed_probes": self.consecutive_failures,
                    }
                )
            elif self.state == "unknown":
                events.append({"event": "online"})
            elif self.consecutive_failures:
                events.append(
                    {
                        "event": "transient_recovery",
                        "missed_probes": self.consecutive_failures,
                    }
                )
            self.state = "online"
            self.consecutive_failures = 0
            self.first_failure_at = None
        else:
            if self.consecutive_failures == 0:
                self.first_failure_at = now
            self.consecutive_failures += 1
            if self.state != "offline" and self.consecutive_failures >= self.offline_after:
                self.state = "offline"
                events.append(
                    {
                        "event": "offline",
                        "missed_probes": self.consecutive_failures,
                    }
                )
        return events


def event_writer(path: Path, event: dict[str, Any]) -> None:
    """Append one timestamped JSON record; avoid logging device IDs/SSID."""
    entry = {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        **event,
    }
    line = json.dumps(entry, ensure_ascii=False, separators=(",", ":"))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as log_file:
        log_file.write(line + "\n")
    print(line, flush=True)


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", required=True, help="Marstek LAN IP (not the router IP)")
    parser.add_argument("--port", type=int, default=30000, help="Device UDP API port")
    parser.add_argument("--interval", type=float, default=30.0, help="Seconds between probes (>= 5)")
    parser.add_argument("--offline-after", type=int, default=3, help="Consecutive failures before offline")
    parser.add_argument(
        "--log", type=Path, default=Path.home() / ".marstek" / "events.jsonl",
        help="JSONL event log (default: ~/.marstek/events.jsonl)",
    )
    parser.add_argument("--once", action="store_true", help="Make one probe then exit")
    args = parser.parse_args(argv)
    if not (1 <= args.port <= 65535):
        parser.error("--port must be between 1 and 65535")
    if args.interval < 5:
        parser.error("--interval must be >= 5 seconds to avoid excessive device polling")
    if args.offline_after < 1:
        parser.error("--offline-after must be >= 1")
    return args


async def watch(args: argparse.Namespace) -> int:
    """Check Device.GetInfo on the local network, logging health events."""
    # Import at runtime so tracker unit tests do not need aiomarstek or HA.
    from aiomarstek import REQUEST_QUIET_GAP, MarstekNoResponse, MarstekUDPClient

    client = MarstekUDPClient(min_request_gap=REQUEST_QUIET_GAP)
    tracker = ConnectivityTracker(offline_after=args.offline_after)
    pinned_id: str | None = None
    await client.async_setup()
    try:
        event_writer(
            args.log,
            {"event": "monitor_started", "interval_seconds": args.interval, "port": args.port},
        )
        while True:
            started = time.monotonic()
            error_type: str | None = None
            try:
                device_info = await client.get_device_info(args.host, port=args.port)
                stable_id = device_info.stable_id
                if not stable_id:
                    raise ValueError("missing device identity")
                if pinned_id is None:
                    pinned_id = stable_id
                    event_writer(
                        args.log,
                        {"event": "device_identified", "device_type": device_info.device_type},
                    )
                elif stable_id != pinned_id:
                    # DHCP address was possibly reassigned; don't call it a recovery.
                    raise ValueError("device identity changed")
                success = True
            except (MarstekNoResponse, TimeoutError, OSError, TypeError, ValueError) as err:
                # Never persist exception messages (may contain IP/MAC).
                success = False
                error_type = type(err).__name__
            now = time.monotonic()
            elapsed_ms = round((now - started) * 1000)
            event_writer(
                args.log,
                {
                    "event": "probe_ok" if success else "probe_failed",
                    "latency_ms": elapsed_ms,
                    **({"error_type": error_type} if error_type else {}),
                },
            )
            for transition in tracker.observe(success, now):
                event_writer(args.log, transition)
            if args.once:
                return 0 if success else 2
            await asyncio.sleep(max(0.0, args.interval - (time.monotonic() - started)))
    finally:
        await client.async_cleanup()


def main() -> int:
    try:
        return asyncio.run(watch(parse_args()))
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
