# Standalone Marstek LAN watchdog (experimental)

Run a **read-only** connectivity monitor on Windows, Linux or macOS without
Home Assistant or an internet connection. This is an initial diagnostic tool,
not yet a battery-control application.

## Setup

- Enable **Local API / Open API** in the official Marstek mobile app over Bluetooth.
- Connect the device and computer to the same LAN. Reserve a stable IP in DHCP.
- Install Python 3.11+ and the client library used by this integration:

```bash
python -m pip install aiomarstek==0.2.2
```

## Commands

One-shot connection test (replace the example IP):

```bash
python tools/marstek_watch.py --host 192.168.1.50 --once
```

Continuous monitoring, with a warning after three missed polls:

```bash
python tools/marstek_watch.py --host 192.168.1.50 --interval 30 --offline-after 3
```

The monitor prints JSON lines and appends them to
`~/.marstek/events.jsonl` (on Windows, under your user profile). Use
`--log` to change the file. Events include `probe_ok`, `probe_failed`,
`online`, `offline`, `recovered` and `transient_recovery`.
A `recovered` duration is estimated from the first missed probe, not
measured device reboot time. Device identity is pinned in memory for each run
to avoid silently monitoring another device if DHCP reassigns the IP.

## Limits and safety

- This tool only requests device information; it cannot change power modes,
  reset the inverter, or read battery data yet.
- **An unanswered UDP request does not prove a physical reboot**. Losses can
  result from Wi-Fi, the router, the Local API, PC sleep, or the device itself.
  Correlate the event timestamps with router wireless association / DHCP logs
  and observed behavior of the station.
- The UDP client uses a fixed local listening port (typically UDP 30000):
  don't run this monitor on the same machine as Home Assistant's Marstek
  integration at the same time. Allow only trusted LAN traffic in the firewall.
- The official app may behave differently without cloud internet access;
  local API availability is a separate question.
- Don't expose the device's unauthenticated UDP port to the internet.
- No real hardware validation has been performed yet.
