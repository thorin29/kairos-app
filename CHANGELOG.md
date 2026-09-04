# Changelog

## 0.18.2 — graph scale fix
- Fixed the y-axis labels bunching in the corner; they now span the chart height
  and align with the gridlines and points.

## 0.18.1 — Workouts graph: fixes to match the web
- Graph now shows one tracked movement at a time, defaulting to today's weights
  (or the next day that has some). Tap the movement name below the chart to
  switch between your tracked movements.
- Fixed the left scale to round to nice steps (nearest 10 lb / 5 kg).
- Bigger points; tap one to see its date and weight. Removed the caption line.

## 0.18.0 — Workouts: progress graph
- The Workouts page now shows the weight-progress line chart at the top (max
  weight per day, per movement) with a tap-to-toggle legend, mirroring the web.

## 0.17.0 — Recent workouts: safer delete
- Removed the "Logged a mistake?" line. Deletes are now hidden behind an Edit
  toggle, and deleting asks to confirm (Cancel / Delete) — no accidental
  removals. Same change applied to the web.

## 0.16.0 — Log workout: "Log a different workout"
- The Log workout page now has the ad-hoc form below Today's plan: Type (Weights,
  Running, Rowing, Rucking, Sport, Stretching, Isometric) -> Record (metric) ->
  Exercise (from the shared pool for pool types) -> Result + unit -> optional Load
  and Notes -> Log workout. Backed by new `GET /workouts/pool` and
  `POST /workouts/log-custom`. (HIIT's dedicated builder comes later.)

## 0.15.0 — Log workout page matches the web
- The Log workout page now shows the "Today's plan" card: plan name, movement
  list, a "today's max" input per movement with its unit, and a Log <metric>
  button, plus the "Logging for <date>" line.
- Next: the "Log a different workout" section (ad-hoc type/exercise/result/notes),
  which needs the exercise-pool + custom-log endpoints.

## 0.14.0 — Workouts page restructured to match the web
- The Workouts page is now the launcher: TODAY (your plan) with Edit plan / Log
  workout / Rest·skip, then Browse workouts and Weight calculator, then a
  "Recent workouts →" link. Action buttons share one card style.
- Recent workouts moved to its own page with per-entry delete ("remove a mistake").
- Edit plan / Browse / Weight calculator are styled stubs for now; the graph and
  This Week (sports) land next.

## 0.13.0 — Workouts: recent history
- The Workouts page now shows a "Recent workouts" list below today's log, with
  each session's name, result, and date. Backed by a new workout history +
  weight-progress read; the progress graph uses the same data and lands next.

## 0.12.1 — Workouts: log planned workouts
- Fixed: the Workouts page now loads your planned workout (e.g. "Legs") and logs
  one value per movement, matching how workouts are actually scheduled — instead
  of only the (unused) per-exercise weekday model, which showed "nothing today".
- Empty state now reads "No scheduled workouts today."

## 0.12.0 — logout confirm + Workouts page (step 1)
- Signing out now asks to confirm (Cancel / Sign out) instead of logging out on
  the first tap.
- The Workouts section is now a real page (step 1 of full parity): today's
  scheduled exercises with inline weight/reps logging, plus mark done / rest.
  History, progress graph, weight calculator, browse, and plan/rotation are next.

## 0.11.1 — menu sits below the top bar
- The menu now starts right at the bottom edge of the status bar, with its
  rounded top-right corner in line just below it, instead of running up behind
  the top bar.

## 0.11.0 — rail slides, rounds, blurs
- The menu now slides straight in/out from the left (no more drop-down feel),
  with a rounded top-right corner.
- It runs full-height under the status bar, with a subtle darker shade over the
  status-bar strip so time/battery/wifi stay readable.
- Everything behind the menu blurs and dims while it's open.

## 0.10.2 — rail top/bottom polish
- The teal panel now starts just below the status bar (flat top above the logo)
  and runs to the bottom, which also cleans up the open animation.
- The version moved to the far-right of the bottom and content is inset from the
  navigation bar, so nothing is clipped by the rounded corner.

## 0.10.1 — nav polish + app icon
- The logo now stays put when the rail opens (status-bar-aligned header), so the
  menu unfurls out of the logo instead of the logo jumping up.
- Sign out is now the switch-arrows icon to the right of your name (expanded).
- Devices removed from the rail (it'll live in a future Settings menu); the
  collapse/expand control keeps a consistent spot in both states.
- The Kairos logo is now the app's launcher icon on the home screen.

## 0.10.0 — nav rail: width, collapse/expand, roll-out, light theme
- The rail now has two widths like the web: a narrow icon-only collapsed rail and
  a wider expanded rail with labels, with a collapse/expand toggle.
- Tapping the logo rolls the rail out from the top-left corner. It opens collapsed
  by default; expanding sticks across navigation and reopen until you collapse it
  or relaunch the app.
- The app now uses the single light Kairos design and no longer follows the
  phone's dark mode (the web has one design).
- Your emoji/initials avatar shows in the rail footer. Real uploaded photos need
  a token-authed avatar endpoint (they stay behind Authelia) — a later piece.

## 0.9.0 — Kairos look for the nav
- The nav rail now matches the web: the real Kairos logo (top-left, not a
  hamburger), your section icons ported from the web icon set, the sage sidebar
  colour, and an active row that turns white with the section's brand colour.
  Person name and app version sit at the bottom.

## 0.8.0 — navigation shell
- The top-left menu now opens a drawer with all your sections (Home, Calendar,
  Chores, Bible reading, Reading, School, Game time, Workouts, Groceries, Money,
  Characters), each in its section colour. Devices and Sign out moved into it.
- Home is the built page; other sections show a placeholder and get filled in
  one at a time — Workouts to full parity is next.

## 0.7.0 — log your workout
- Workout prompts now offer "Log workout": a screen to enter weight and reps for
  each of the day's scheduled exercises (prefilled with anything already logged),
  then save — which also completes the workout. Mark done / Rest day are still
  there for days with nothing to log in detail.
- Home refreshes when you return from logging.
- New endpoints: `GET /api/v1/workouts` and `POST /api/v1/workouts/log`.
- Non-weight metrics, multiple sets, and HIIT logging come in a later phase.

## 0.6.0 — your devices
- New Devices screen (top-right menu → Devices): see every device enrolled to
  your account, when it enrolled and last activity, which one is this phone, and
  revoke any you don't recognise. Backed by `GET /api/v1/devices` and
  `POST /api/v1/devices/{id}/revoke`.
- Home's sign-out moved into that overflow menu.

## 0.5.0 — re-login on password change
- If your account password changes, the app now asks you to sign in again while
  the device stays enrolled — no re-pairing. Backed by `POST /api/v1/auth/reauth`
  and a `reauth_required` response the app handles distinctly from a full sign-out.
- Passwordless child devices are unaffected.

## 0.4.0 — sign-in (layered login + code)
- People with a password now sign in (username/email + password) and then pair
  the device with a code — both factors, for the same person.
- Passwordless children still enroll by code alone (a parent provisions it from
  the admin panel); the "child device" link skips the sign-in step.
- Enroll now tells you when an account needs a password sign-in first.
- New endpoint: `POST /api/v1/auth/login`.

## 0.3.1 — stable release signing (install over the top)
- CI now builds a *signed release* APK with one stable key (stored as GitHub
  secrets), so future updates install over the existing app and keep enrollment.
- One-time transition: this build's package id is `com.kairos.app` (the debug
  builds were `com.kairos.app.debug`), so uninstall the current app and enroll
  once more. Every update after this installs cleanly on top.

## 0.3.0 — workouts (day-level)
- Workout prompts on Home now open an action sheet: Mark as done, Rest day, or
  undo. Backed by `POST /api/v1/workouts/complete` / `/uncomplete` / `/rest`.
- Set-by-set logging (exercises, weights, reps) is the next workout increment.

## 0.2.0 — home dashboard + tap-to-complete
- Home now shows the personal day from `GET /api/v1/dashboard`: an overall
  completion percent, per-category progress bars, an overdue section, and the
  day grouped by category (chores, reading, exercise, school, …).
- Tap a task to complete/uncomplete it (`POST /api/v1/tasks/{id}/complete` /
  `/uncomplete`); bars and percent refresh from the server so derived values
  stay authoritative.
- Pull to refresh; loading, error (with retry), and empty-day states.
- Workout prompts are shown but not yet tappable (they need the workout logger,
  a later increment).

## 0.1.0 — scaffold + enrollment/sign-in
- New native Kotlin / Jetpack Compose project (Material 3, single-activity).
- Configurable server base URL (no host baked into the repo); `/meta` handshake.
- Enrollment by one-time code: `/auth/enroll` -> device token -> `/me`.
- Device token stored encrypted via an AES-256-GCM key in the Android Keystore.
- Session-driven auth gate: Setup -> Enroll -> Home, with sign-out (`/auth/revoke`).
