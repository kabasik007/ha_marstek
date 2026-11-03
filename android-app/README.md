# Zilla Energy — Android source prototype

**Status: source-only Android application; not built, signed or tested on a
physical device.** This lives next to the existing Home Assistant integration,
which remains unchanged. It is a separate Gradle project under `android-app/`.

## Included

- Kotlin + Jetpack Compose + Material 3, pastel-mint design system.
- Dashboard: battery, grid/solar/off-grid metrics, battery gauge and smooth
  Canvas energy chart.
- Diagnostics: persisted event timeline, outage/recovery classification,
  probe response time, export to Android share sheet.
- Cameras: local credential-free RTSP streams with Media3 ExoPlayer. One active
  decoder at a time, camera list persisted in DataStore.
- Control: documented read-only capability placeholders. **No write commands**
  are sent to the battery (no mode changes, reset, discharge limits).
- Settings: local IP/port, Demo / Live switch, interval selection.
- A tiny deterministic sample dataset so the UI can be developed without
  access to the physical inverter.
- Unit tests for private LAN address validation and outage state machine.

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
    ui/
      EnergyApp.kt
      theme/ZillaTheme.kt
      components/PowerChart.kt, UiParts.kt
      screens/DashboardScreen.kt, DiagnosticsScreen.kt,
              CamerasScreen.kt, ControlScreen.kt, SettingsScreen.kt
```

## Connection contract and safety

The app starts in **Demo** mode. All readings in Demo are simulated.
Enable the Marstek Local API first using the official app over Bluetooth,
then set a *private IPv4 LAN* endpoint (e.g. `192.168.0.61:30000`) and turn
off Demo mode to begin live queries.

The UDP client:
- Issues only `ES.GetStatus`, serialized, at a configurable 15–300 second
  interval; default 30 seconds.
- Opens a client receive socket on the same port as the device request,
  to support devices that respond only when UDP source/destination ports
  match. This may fail if another app binds the same local port.
- Binds to Android's **Wi-Fi network**, including a Wi-Fi network without
  internet, rather than silently routing through mobile data.
- Verifies source device IP and request ID; rejects public IPs.
- Uses bounded receive timeouts, logs event transitions, and requires three
  consecutive misses to label connectivity **offline**.
- Avoids `Bat.GetStatus` by default, because some firmware reports implicate
  that command in device instability; this app doesn't claim to diagnose
  a hardware reboot from network reachability alone.

**Important:** Offline/Recovered events are NOT evidence that the inverter
physically rebooted. Cross-check router DHCP / Wi-Fi association logs and
device status LEDs. Local API is unauthenticated UDP; keep it off untrusted
networks and never port-forward it.

## Cameras

Use `rtsp://<private-LAN-IP>:554/<stream>`, WITHOUT inline credentials,
passwords or access tokens. Username/password-protected cameras need a
future encrypted credential store and explicit threat review; this initial
prototype deliberately rejects RTSP URLs containing userinfo or queries.
RTSP previews run on the phone only; the app does not relay camera streams.

## Persistence and current limitations

- `Preferences DataStore` saves settings, camera entries and the latest 250
  incident events. Charts use only recent *in-memory* readings, so a process
  restart clears the chart while preserving the event timeline.
- Monitoring runs only while the app process/ViewModel is alive; there is
  **no foreground service or guaranteed background monitoring yet**.
- Firmware versions, model-specific capabilities, BLE pairing, cloud fallback,
  firmware updates and physical reboot counters are **not implemented**.
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
   stable identity pinning and long-term chart storage (Room).
3. Add an optional, user-approved background health monitor.
4. Integrate secure RTSP authentication and optional ONVIF discovery.
5. Add localization (Ukrainian / English), backup/import of settings,
   and carefully permissioned per-model write controls.
