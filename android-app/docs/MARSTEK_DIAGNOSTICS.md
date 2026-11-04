# Marstek LAN diagnostics playbook (source-only)

Purpose: distinguish LAN failures from Android's internet-validation changes
without guessing that the inverter rebooted. No high-voltage tests,
disconnection of powered loads or forced restarts are required.

## Device and LAN

- Reserve the observed Marstek IPv4 address in router DHCP. Example:
  192.168.0.61. Use your **actual** local IP, never an internet IP.
- Connect the Android phone and the Marstek device to the same trusted Wi-Fi.
  The official Marstek app must enable Local API via Bluetooth first.
- The default port is 30000. Do not forward the port to the internet.
- Start in Demo mode to review the UI, then configure the LAN endpoint.
- Switch Demo off. The first ES.GetStatus reading should change status to
  Local API online and populate the chart. A blank reading is not zero.

## How to interpret readings

| Android Wi-Fi LAN | Internet validated by Android | Marstek UDP replies | Interpretation |
| --- | --- | --- | --- |
| yes | yes | yes | LAN API reachable, internet also validated |
| yes | no | yes | LAN API works without validated internet; expected offline-capable behavior |
| yes | no | no | API unreachable; could be device Wi-Fi, router isolation or firmware |
| yes | yes | no | API unreachable despite internet check; investigate Local API and firewall |
| no | unknown | no | Phone lacks Wi-Fi LAN; station state cannot be inferred |

Android's \`NET_CAPABILITY_VALIDATED\` describes OS validation, **not a
definitive test of the ISP or the Marstek cloud**. Device Wi-Fi RSSI is queried
separately and only when advanced diagnostics are enabled.

## Timeline and data

- Telemetry and probe history are saved locally in Room (up to 30 days).
- Select 1H/24H/7D/30D chart period. Missing readings remain gaps.
- The 24h API success ratio excludes phone-side Wi-Fi loss but counts UDP
  timeouts and API errors. It is **not uptime** and is not a reboot counter.
- Export selected chart buckets as UTC CSV from Dashboard, or share the
  incident summary from Diagnostics. Do not share screenshots containing
  IP/SSID if privacy matters. Chart CSV never contains these identifiers.
- Process lifetime: the application currently polls only while running.
  There is **no guaranteed background logging** when Android suspends it.

## If an outage is suspected

1. Write down local time, whether 220 V output actually disappeared, and
   whether device LEDs/screen changed. Do not induce a restart under load.
2. Export the incident timeline. The app reports a failed reply, not a
   proven device reboot.
3. On the router, inspect wireless association/disassociation, DHCP lease,
   SSID, Wi-Fi channel and 2.4 GHz radio events around the same timestamp.
4. Confirm whether only WAN/internet went away while local Wi-Fi continued.
5. Record device model and firmware from the official Marstek app over BLE.
6. If repeated actual power interruptions occur, avoid critical loads and
   contact the manufacturer with timestamps and device/firmware details.

## Safety boundaries

- Only read-only \`ES.GetStatus\` by default, at 30 s intervals.
- Optional \`wifi.GetStatus\` and \`ES.GetMode\` at most once every five minutes.
- No write commands, factory reset, firmware flash or BLE pairing.
- Public API PDF at https://static-eu.marstekcloud.com/ems/resource/agreement/MarstekDeviceOpenApi.pdf
  currently says Rev 2.0 and shows \`wifi.GetStatus\` with a lowercase w
  in its example. The repository includes a Rev 3.1.2 PDF. Model/firmware
  compatibility must be verified on a real Marstek device before any claims
  of complete support.
