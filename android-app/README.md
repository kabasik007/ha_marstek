# Zilla Energy — Android source prototype

**Status: source-only Android application; not built, signed or tested on a
physical device.** This lives next to the existing Home Assistant integration,
which remains unchanged. It is a separate Gradle project under `android-app/`.

## Included

- Kotlin + Jetpack Compose + Material 3, pastel-mint design system.
- Dashboard: battery, grid/solar/off-grid metrics, battery gauge and smooth
  Canvas energy chart with 1H/24H/7D/30D ranges.
- Diagnostics: persisted event timeline, outage/recovery classification,
  probe response time, 24h observed API success ratio (excluding phone Wi-Fi
  losses), export through Android share sheet, and optional device-reported
  Wi-Fi RSSI/SSID and ES operating mode.
- Cameras (parked): local credential-free RTSP streams with Media3 ExoPlayer. One active
  decoder at a time, camera list persisted in DataStore.
- Control: documented read-only capability placeholders. **No write commands**
  are sent to the battery (no mode changes, reset, discharge limits).
- Settings: local IP/port, Demo / Live switch, interval selection.
- A tiny deterministic sample dataset so the UI can be developed without
  access to the physical inverter.
- JVM unit tests for IP/protocol parsing, chart windows and outage logic;
  source-only instrumented Room DAO tests (not executed yet).

## Architecture

```text
app/
  src/main/java/ua/zilla/energy/
    MainActivity.kt
    EnergyViewModel.kt
    model/Models.kt
    data/
      LocalAddress.kt
      ConnectionTracker.kt
      MarstekUdpClient.kt
      PreferencesStore.kt
      DemoEnergy.kt
      PhoneNetworkInspector.kt
      history/EnergyDatabase.kt, EnergyHistoryRepository.kt
    ui/
      EnergyApp.kt
      theme/ZillaTheme.kt
      components/PowerChart.kt, UiParts.kt
      screens/DashboardScreen.kt, DiagnosticsScreen.kt,
              CamerasScreen.kt, ControlScreen.kt, SettingsScreen.kt
```

## Raw status diagnostics (0.3.0)

Diagnostics now includes a **Raw API / ES.GetStatus** card with the fields
from the *same* successful polling reply used by the dashboard. This adds no
requests and cannot write to the inverter. Tap **Show JSON**, **Copy** or
**Share** to inspect a redacted status response. The source MAC/UDP envelope
is never exposed. Only documented electrical numeric fields are shown
verbatim; unknown fields and unexpected strings are masked. This temporary
snapshot stays in memory and is cleared upon switching to Demo/another
endpoint; it is **not** stored in Room/DataStore, telemetry logs, or cloud.

The list distinguishes `bat_power` being absent, present as JSON `null`,
and present as a valid watt value. The Rev 3.1.2 documentation explicitly
allows the field to be `null` and the official example omits it, so the
UI correctly displays `—` rather than inventing zero watts.

## Automatic LAN discovery (0.2.0)

Settings runs a short, bounded discovery automatically when there is no
configured device IP. Use **Find Marstek automatically** to scan again.

- First, set up the Marstek home Wi-Fi connection and enable its Local API
  in the official Marstek app over Bluetooth.
- The phone and station must be on the same LAN. Internet is not required.
- Uses the official read-only UDP broadcast Marstek.GetDevice with
  params ble_mac = "0" to the Wi-Fi subnet broadcast address.
- Lists the model, firmware version and discovered IP. You must tap
  the device to save its address and switch from Demo into Live mode.
- The scan never changes battery settings and never auto-selects an
  unknown device. The app serializes it with regular UDP status polling.
- Client-isolated Wi-Fi, blocked UDP broadcast, a different VLAN or a
  nondefault port can prevent discovery. Manual IP entry remains available.
- No hardware compatibility test has been performed yet.

## Connection contract and safety

For a practical troubleshooting matrix see [Marstek LAN diagnostics](docs/MARSTEK_DIAGNOSTICS.md).


The app starts in **Demo** mode. All readings in Demo are simulated.
Enable the Marstek Local API first using the official app over Bluetooth,
then set a *private IPv4 LAN* endpoint (e.g. `192.168.0.61:30000`) and turn
off Demo mode to begin live queries.

The UDP client:
- Issues only `ES.GetStatus` by default, serialized, at a configurable
  15–300 second interval; default 30 seconds. Explicitly opting into advanced
  diagnostics additionally requests `wifi.GetStatus` (as written in the public PDF request
  example) and `ES.GetMode` at most
  once every 5 minutes. Both are read-only and can fail independently.
- Opens a client receive socket on the same port as the device request,
  to support devices that respond only when UDP source/destination ports
  match. This may fail if another app binds the same local port.
- Binds to Android's **Wi-Fi network**, including a Wi-Fi network without
  internet, rather than silently routing through mobile data.
- Verifies source device IP/UDP port and request ID; rejects public IPs.
- Pins the firmware-reported response `src` per endpoint per session when
  present. If the reported device changes at the same IP, stops monitoring
  until the DHCP binding is verified. Raw `src` is never saved in logs.
- Uses bounded receive timeouts, logs event transitions, and requires three
  consecutive misses to label connectivity **offline**.
- Avoids `Bat.GetStatus` by default, because some firmware reports implicate
  that command in device instability; this app doesn't claim to diagnose
  a hardware reboot from network reachability alone.

**Important:** Offline/Recovered events are NOT evidence that the inverter
physically rebooted. Cross-check router DHCP / Wi-Fi association logs and
device status LEDs. Local API is unauthenticated UDP; keep it off untrusted
networks and never port-forward it.

## Cameras (parked)

Use `rtsp://<private-LAN-IP>:554/<stream>`, WITHOUT inline credentials,
passwords or access tokens. Username/password-protected cameras need a
future encrypted credential store and explicit threat review; this initial
prototype deliberately rejects RTSP URLs containing userinfo or queries.
RTSP previews run on the phone only; the app does not relay camera streams.

## Persistence and current limitations

- `Preferences DataStore` saves settings, camera entries and the latest 250
  incident events. Room 2.8.5 + KSP persists real Marstek telemetry and
  individual read-only probe outcomes for 30 days (retention is pruned during
  active monitoring). Demo mode never writes to the Room database.
- Chart history is bucket-averaged from the local database by device endpoint;
  missing intervals remain blank. Selected bucket series can be explicitly
  shared as UTC CSV without IP, MAC or SSID identifiers.
- Read-only API response rate is an **observed probe success rate**, not actual
  device uptime. Phone-side Wi-Fi outages are counted separately and excluded
  from the rate. Event timeline is still limited to the last 250 entries.
- Monitoring runs only while the app process/ViewModel is alive; there is
  **no foreground service or guaranteed background monitoring yet**.
- Firmware versions, model-specific capabilities, BLE pairing, cloud fallback,
  firmware updates and physical reboot counters are **not implemented**.
- Missing Wi-Fi or mode fields must be shown as unknown, not as zeros.
  Battery power sign/direction is firmware-dependent and never inferred.
  Readings are marked as last-known when connectivity is degraded/offline.
- There is no pairing flow and no real-device compatibility validation.
- Controls are informational (not wired to the device).
- Camera player has only a basic RTSP view, not ONVIF discovery or PTZ.
- The application theme and screen copy are in English; localization is next.

## Project setup (later, when you are ready to build)

The source Gradle settings are pinned to **AGP 8.13.2, Kotlin 2.2.21,
Compose BOM 2025.10.01, Java 17, compileSdk 36, Gradle 8.13**.

Open `android-app/` as a standalone project in Android Studio. Only
`gradle-wrapper.properties` is checked in; generate wrapper scripts/JAR
from an installed Gradle distribution before using `./gradlew`.
When source validation is authorized, run unit tests / compile tasks
without creating an APK first. **No APK has been assembled.**

## Development roadmap

1. Verify UDP socket behavior on the actual Marstek model and firmware.
2. Add a device-aware `ES.GetMode` read, validated power sign semantics,
   stable identity pinning and device model/firmware capability reporting.
3. Add an optional, user-approved foreground health-monitor service to
   survive UI closure where Android policy permits, with explicit controls.
4. Integrate secure RTSP authentication and optional ONVIF discovery.
5. Add localization (Ukrainian / English), backup/import of settings,
   and carefully permissioned per-model write controls.

## Marstek-only development focus (November 2026)

Current navigation hides the unfinished Cameras screen without deleting its
source. Marstek LAN connectivity, reliable read-only telemetry and diagnosing
Wi-Fi dropouts take priority. The backend does **not** identify physical inverter
reboots; it only tracks API responses. All advanced requests are optional to
minimize potential load on problematic firmware.

API protocol: the [public Marstek Device Open API PDF](https://static-eu.marstekcloud.com/ems/resource/agreement/MarstekDeviceOpenApi.pdf)
currently identifies itself as **Rev 2.0**, while this repository also bundles
a **Rev 3.1.2** PDF under `docs/`. The public PDF's Wi-Fi request example
uses lowercase `wifi.GetStatus` while the section title says
`Wifi.GetStatus`; firmware method case handling remains unverified.
Supported methods vary by model and firmware.
