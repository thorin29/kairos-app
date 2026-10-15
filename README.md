# Kairos (Android)

Native Kotlin / Jetpack Compose client for a self-hosted Kairos household
server. Sideloaded as an APK/AAB — no Play Store. Talks to the versioned
`/api/v1` REST surface documented in the server repo's `docs/API.md`.

This app ships pointing at **no server**. On first launch you enter your own
server's address; it's stored on-device and can be changed any time. Nothing
about a specific deployment is baked into the source.

## Requirements

- Android Studio (recent stable)
- JDK 17 (bundled with Android Studio)
- A reachable Kairos server exposing `/api/v1`

## Build & run

Open the project in Android Studio and let it sync, or from the command line:

```
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease      # release APK (configure signing first)
```

Install the debug APK on a device with USB debugging, or sideload the APK.

## Identity model

Per-person **device tokens** (see the server's `DECISIONS.md` and `docs/API.md`).
A parent invites a person from the admin area, which issues a single-use
**invitation code**. On the phone the person enters that code and sets a
password (new account) or confirms their existing one — one `POST /auth/join`
both checks the invite and enrolls the device, returning a long-lived bearer
token. The token is the identity for every later request; it's stored
encrypted with an AES-256-GCM key held in the Android Keystore and sent as
`Authorization: Bearer <token>`. The password is for joining, unlocking a locked
phone (`/auth/login`) and re-confirming after a credential change
(`/auth/reauth`); `/auth/forgot` and `/auth/recover` cover a lost one.

## What the app does

Mirrors each person's personal view of the household: Home dashboard, Calendar,
Chores, Bible reading, Reading, School, Workouts, Groceries, Money, and
Characters. Beyond parity with the web personal view it adds:

- **Offline-first.** Screens render from an on-device cache and work without a
  signal; writes (completing chores/tasks, logging workouts, marking reading,
  adding groceries/events, etc.) queue and replay on reconnect.
- **Settings** (gear by your name): **Appearance** (dark mode + eight color
  themes + 24-hour clock, per device), **Profile** (photo + framing + your color,
  which syncs to the web/calendar), and **Notifications**.
- **Reminders.** Set reminders per calendar event; they fire locally at the
  chosen lead time (no push service). Off by default.
- **Calendar invites.** Tap an `.ics` attachment in your email (or Share it to
  Kairos) and New event opens filled in from it, on your own calendar. The file
  is read on the phone.
- **Workouts** logged movement by movement — weight and reps, or a hold time —
  with your best weight, best reps and latest session beside the fields, and a
  body map to browse progress by muscle group.
- **Companions** to raise through growth stages, deepen, and swap back in from
  the gallery.

## First-launch flow (this build)

1. **Setup** — enter your server address; the app runs a `/meta` handshake.
2. **Join** — type the invitation code a parent sent you; the app checks it
   (`/auth/join/check`), asks you to create or confirm your password, then calls
   `/auth/join`, stores the token, and fetches `/me`.
3. **Home** — the personal dashboard for whoever you enrolled as, with the full
   feature set above; sign-out (gear by your name) revokes the token on the server.

## Tech

Jetpack Compose (Material 3) · Navigation-Compose (type-safe routes) ·
Retrofit + OkHttp · kotlinx.serialization · DataStore · Android Keystore ·
manual DI. Versions are pinned in `gradle/libs.versions.toml`.

## Network / server notes

- The base URL is configurable, so you can point at a LAN address for setup and
  testing, then switch to your public domain.
- Over the public domain, requests reach `/api/v1` only if that path is exempt
  from your reverse-proxy auth (e.g. a scoped Authelia bypass). `/api/v1`
  authenticates every request itself, which is what makes that bypass safe.
