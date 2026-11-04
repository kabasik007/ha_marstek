# Venus E 3.0 / V150 — experimental read-only Modbus TCP

The Zilla Energy Android client now includes a separate **BMS** screen.
This is **not** the same interface as Marstek's UDP Local API.

## Connection

- Keep the Android phone connected to the trusted home Wi-Fi.
- Connect the **native LAN/Ethernet port** of the Venus E 3.0 to the same
  router/switch using an Ethernet cable. Do **not** insert Ethernet into
  a labeled RS485 port.
- Confirm the *wired* IP in the router's LAN/DHCP clients. It may differ
  from the existing Wi-Fi API endpoint such as 192.168.0.61.
- The community-documented Modbus port is **TCP 502**, Unit ID **1**.
  It normally does **not** require enabling the Open API UDP toggle.
- On Windows, a basic port check is:
  `Test-NetConnection <WIRED_IP> -Port 502`
- A failed port test does not mean the battery is defective; check the
  LAN port, DHCP lease, subnet, client isolation and any device setting.

## Safety

- This code sends **only FC03 (read holding registers)** and has no
  write-register or reset implementation.
- The device is **never scanned in the background** over Modbus. Tap
  **Test LAN connection** to read two basic battery registers once.
- Only after a plausible ~40–65 V pack voltage read does the **Read cells
  once** button activate, reading 16 consecutive cell voltages.
- A cell result outside 2.0–4.0 V is rejected as potentially the wrong
  model/register map. Do not make cell-health decisions without verifying
  the firmware's mapping and operating state.
- No Modbus values are saved to Room or shared externally in this phase.
- If you observe inverter restarts or output interruption, stop the
  checks and contact the manufacturer. Avoid testing with critical loads.

## Allowlisted register reads

Community Venus E 3.0 map: FC03 / Unit ID 1.

| Start | Count | Purpose | Status |
|---|---:|---|---|
| 30100 | 2 | pack voltage / pack current | One-shot button |
| 34018 | 16 | pack 1 cell voltage 1–16 | Separate one-shot button |
| 34003 | 1 | battery cycle count | Reserved for later |
| 35000 | 3 | internal and MOSFET temperatures | Reserved for later |
| 35010 | 1 | max cell temperature | Reserved for later |

Only the first two ranges are reachable from the current UI. Kotlin
allowlists all permitted FC03 register blocks and rejects every other
range, including mode and charging setpoint registers.

## Sources

- https://github.com/ArthurTimmermans/marstek-maestro/blob/main/docs/modbus.md
- https://github.com/ViperRNMC/marstek_venus_modbus/blob/main/README.md
- https://github.com/sphings79/marstek-modbus-suite

These are community documents, **not an official safety certification**,
and some revisions differ in Modbus register scaling/addressing.
