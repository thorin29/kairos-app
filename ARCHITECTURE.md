# Kairos Android app — architecture

Native Kotlin / Jetpack Compose client for the Kairos household dashboard. Talks
only to the versioned REST API (`/api/v1`) in the web repo's `docs/API.md`. The
web app is the source of truth for behaviour and look; the app **mirrors the web
from source** rather than inventing its own UX.

## Stack / build

- AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, Compose BOM 2025.06.01.
- compileSdk / targetSdk 36, minSdk 26, JDK 17. `applicationId com.kairos.app`.
- Retrofit + OkHttp + `retrofit2-kotlinx-serialization-converter`, kotlinx.serialization.
- DataStore (settings + token blob). Coil (`io.coil-kt:coil-compose:2.7.0`) for avatars.
- Navigation-Compose type-safe routes. Manual DI (no Hilt).
- Release APK is signed in CI (`.github/workflows/android.yml`) with secrets
  `KEYSTORE_BASE64`, `KEY_ALIAS`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`
  (KEY_PASSWORD == KEYSTORE_PASSWORD; PKCS12 requires it). R8 minify OFF for now.

## Versioning — bump all three every release

- `app/build.gradle.kts`: `versionCode` (integer, +1) and `versionName` (semver).
- `SessionRepository`: `const val CLIENT_BUILD` — keep equal to `versionCode`.
- `CHANGELOG.md`: user-facing entry at the top.

## Networking (`data/remote`)

- `ApiClient.create(baseUrl, tokenProvider)` builds the Retrofit `ApiService`.
  `normalizeBase` turns the user's bare origin into `<origin>/api/v1/`.
- `AuthInterceptor(tokenProvider)` attaches `Authorization: Bearer <token>` to
  every request when a token is held (no per-method header). `tokenProvider` is
  `{ tokenStore.current() }` — an in-memory read, never a blocking decrypt on the
  network thread.
- JSON is lenient: `ignoreUnknownKeys = true`, `explicitNulls = false`,
  `coerceInputValues = true`. **Every DTO field should have a default** so a
  status-only response (`{ "status": "ok" }`) still deserialises. (See DECISIONS
  — a required `id` once silently broke a refresh.)
- `Response<T>.bodyOrThrow()` maps the `{ error }` envelope to a typed
  `ApiException(ApiError.*)`. `apiCall {}` turns transport failures into
  `ApiError.Network`.
- `ApiClient.resolveUrl(base, path)` resolves a relative URL (e.g. an avatar
  `/api/v1/avatars/x.png`) against the configured origin.

## Auth / session

- `SessionState` sealed interface: `Loading → NeedsSetup → NeedsEnroll → Ready`.
  It drives all top-level UI, which eliminates soft-navigation bypass as a class.
- Device token stored encrypted (Android Keystore AES-256-GCM) via `TokenStore`;
  `tokenStore.current()` is the in-memory copy used by the interceptor and Coil.
- Layered login+code enrollment; passwordless kids use a code alone.
  Credential-version gate: a password change server-side returns
  `reauth_required` (401) → the app shows the reauth screen (device stays enrolled).

## Dependency container (`di/AppContainer`)

Manual container built once in `KairosApp`. Holds `settingsStore`, `tokenStore`,
`sessionRepository`, `navExpanded` (rail state), and `imageLoader` (a Coil
`ImageLoader` whose OkHttp client reuses `AuthInterceptor { tokenStore.current() }`
so avatar requests carry the device token). Reach it in composables with
`rememberContainer()`.

## Navigation

- Routes are `@Serializable` objects/classes in `ui/nav/Routes.kt`.
- `AppRoot` hosts the `NavHost`; add a screen with `composable<Route.X> { ... }`
  and navigate with `navController.navigate(Route.X)` / `popBackStack()`.
- The nav shell is a custom overlay rail (`ui/nav/KairosRail.kt`), not a Material
  drawer: teal panel `#86A0A3`, collapsed 76dp (icons) / expanded 224dp (labels),
  expand state in `AppContainer.navExpanded`. Icons in `KairosIcons.kt` (SVG paths
  ported from the web). Section metadata in `AppSections.kt`.

## Screen pattern (used everywhere — follow it for new sections)

1. **DTOs** in `data/remote/dto/Dtos.kt` (`@Serializable`, all fields defaulted).
2. **ApiService** method (Retrofit) + **SessionRepository** wrapper using
   `runAuthed { requireService().call() }` (handles reauth/401).
3. **ViewModel** with a `StateFlow<XUiState>`; an `init { load() }`, and an
   `act { ... }` helper that runs a mutation then reloads (so the UI refreshes
   without the user re-navigating). Guard reentrancy with a `busy` flag.
4. **Screen** composable: `rememberContainer()` → build the VM via
   `viewModelFactory`; render loading / error / content; pass navigation as
   lambdas from `AppRoot`.

## Images / avatars

- Uploaded avatars are served by the device-authed `GET /api/v1/avatars/[file]`
  (the web's `/api/avatars/*` sits behind Authelia and the app can't reach it).
- `/me` sends `avatarUrl` (relative, `/api/v1/...`), `avatarIcon` (emoji), and
  `avatarPosition` (`"tx ty scale"`). Render with `SubcomposeAsyncImage`
  (`imageLoader = container.imageLoader`, `ContentScale.Crop`) inside a circle,
  falling back to emoji/initials on null/error. Apply `avatarPosition` with a
  `graphicsLayer { scaleX/Y = scale; translationX = tx/100f*size.width; ... }` —
  this mirrors the web's `translate(tx%, ty%) scale(s)` on an `object-cover` image.

## Theme

Light only (`darkTheme = false` always) — the web has one design; dark mode is
deliberately off. Status-bar icons forced dark via `WindowCompat` in MainActivity.

## Where things live

- `ui/<section>/` — screens + view models per feature (e.g. `ui/workout/`).
- `ui/common/` — `rememberContainer`, shared composables.
- `data/remote/` — ApiClient, ApiService, AuthInterceptor, dto/Dtos.kt, ApiError.
- `data/session/` — SessionRepository, SessionState.
- `data/secure/` — TokenStore. `data/settings/` — SettingsStore.
