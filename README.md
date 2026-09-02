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

Per-person **device tokens** (see the server's `DECISIONS.md`). A parent
generates a one-time enrollment code in the admin area; the phone redeems it at
`/auth/enroll` for a long-lived bearer token. The token is the identity — there
is no password on the phone. It's stored encrypted with an AES-256-GCM key held
in the Android Keystore, and sent as `Authorization: Bearer <token>` on every
request except enrollment.

## First-launch flow (this build)

1. **Setup** — enter your server address; the app runs a `/meta` handshake.
2. **Enroll** — type the one-time code (QR scan lands next); the app calls
   `/auth/enroll`, stores the token, and fetches `/me`.
3. **Home** — a placeholder that shows who you're enrolled as, plus sign-out
   (which revokes the token on the server). The real dashboard lands next.

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
