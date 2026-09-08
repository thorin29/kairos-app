# Decisions & lessons — Kairos app

Hard-won guardrails from building the app. Read alongside ARCHITECTURE.md and the
web repo's `docs/API.md` (the contract) and `DECISIONS.md`.

## Mirror the web from source — don't build subsets
The web components ARE the spec. Before building a screen, read the full web
page/component and replicate its sections, labels, and styling. Building a
minimal subset and waiting for the user to notice what's missing wastes their
time (they should not have to send screenshots). When something is genuinely
deferred, name it explicitly (from the source) so it's a known gap, not a
surprise.

## Every response DTO field must be optional (defaulted)
Mutation routes often return `{ "status": "ok" }` with no `id`. If a DTO has a
required field, kotlinx-serialization **throws** on the missing key, the call's
`try` catches it, and the post-mutation reload is silently skipped — the change
looks like it didn't happen until you re-open the screen. Give `TaskStatusDto`
and every ack/response DTO all-default fields. (This was the "add-workout doesn't
refresh" bug.)

## Compose scope members cannot be imported
`ExposedDropdownMenu`, `menuAnchor`, `SubcomposeAsyncImageContent`, etc. are
members of their enclosing scope (e.g. `ExposedDropdownMenuBoxScope`). Importing
them fails to resolve; call them unqualified inside the scope lambda. This is the
inverse of the missing-import problem, and the CI catches it — but check for it
before packaging. (`menuAnchor` also needs `MenuAnchorType.PrimaryNotEditable`.)

## Verify escaping in generated code
When writing Kotlin via scripts, string/regex escaping doubles easily. A Kotlin
`"\\d"` (two backslashes) is the regex `\d`; four backslashes make it a literal
backslash and the regex silently never matches. Always read back the actual byte
count for regexes and other escaped literals.

## Pre-package verification ritual (every change)
1. package/path check — each file's `package` matches its directory.
2. missing-import scan — modifiers (`.padding`, `.clip`, `.graphicsLayer`, …) and
   material3 symbols are imported; and no scope-member imports (above).
3. brace balance per changed file.
4. For web/server changes: hand-audit Prisma calls against `schema.prisma` (the
   sandbox can't `prisma generate`), and check for stale/renamed DTOs.

## Delivery
Diff-only zips, versioned filename. If a prior release might not be deployed yet,
re-include its files in the next zip so uploading one gives a complete, consistent
state. Deploy the **web first** (it carries the API the app calls), then the app.
Bump `versionCode` + `versionName` + `CLIENT_BUILD` together.

## Device-authed API routes use `requireDevice` + shared cores
The app can't call the web's `"use server"` actions (they use
`requireInteractive`/`requireCanActFor`, i.e. an Authelia session). So every
`/api/v1` route calls `requireDevice(req)` and delegates to a **shared core**
extracted into a lib (e.g. `lib/workouts/mark.ts`, `plan-edit.ts`,
`rotation-edit.ts`, `queries/workout-log.ts`). Cores skip the session gate but
re-run side effects like `generateWorkoutTasks()`.

## Coil avatars reuse the API auth
The avatar `ImageLoader` in `AppContainer` reuses `AuthInterceptor { tokenStore.current() }`
so images behind `/api/v1/avatars` load with the bearer token. Apply the web's
`avatarPosition` transform in a `graphicsLayer` over a cover-fit image.

## Confirm destructive actions
Revoking an active phone and hard-deleting a revoked one are both confirmation-
gated (inline Cancel/Confirm or an AlertDialog). Deleting a logged workout from
the Recent page is edit-gated + confirmed. Default to a confirm step for anything
irreversible.

## Security posture (for reference)
`/api/v1` bypasses Authelia but each route self-authenticates the bearer device
token; tokens are stored as a SHA-256 `tokenHash`, passwords/PINs are hashed,
traffic is HTTPS. The Postgres data at rest is not encrypted — acceptable for a
self-hosted home server; encrypting backups is the reasonable enhancement.

## Collect screen state with collectAsState(), NOT collectAsStateWithLifecycle() (v0.68.4+)
Hard-won. Symptom: after navigating away from a screen and back (especially via the
drawer, which navigates with `popUpTo(Route.Home)` + `launchSingleTop`), tapping
things did nothing — the screen rendered but ignored ViewModel updates — until the
app was force-restarted. It was NOT the pop-up and NOT the tap handler.
Root cause: `collectAsStateWithLifecycle()` only collects while the destination's
lifecycle is ≥ STARTED, and this nav can leave a `NavBackStackEntry` stuck below
RESUMED after returning, so collection stops and never restarts — the composable's
`ui` freezes on its last value. Fix / standing rule: use `collectAsState()` for all
screen/ViewModel state (applied app-wide). Add new screens with `collectAsState()`.
Corollary: `ON_RESUME`-based "reload on return" (a `DisposableEffect` +
`LifecycleEventObserver`) is ALSO unreliable under this nav — it doesn't fire. To
reload a screen when it's shown again, use a nav signal instead: in AppRoot, watch
`navController.currentBackStackEntryAsState()` and bump a `refreshKey` Int passed to
the screen when it becomes the current destination; the screen does
`LaunchedEffect(refreshKey) { vm.load() }`. (Home uses exactly this.)

## Pop-ups use AnimatedDialog; selects use RollPicker (hard UI convention)
Every pop-up must use `AnimatedDialog` (`ui/common/AnimatedDialog.kt`), never
`AlertDialog`; every dropdown must use `RollPicker` (`ui/common/RollPicker.kt`),
never `ExposedDropdownMenu`. AnimatedDialog eases in via a single
`animateFloatAsState` through `graphicsLayer` — do NOT use `AnimatedVisibility +
scaleIn` (stutters on the first frame). Signature:
`AnimatedDialog(onDismissRequest, title?, confirmButton?, dismissButton?, content)`.
Prefer AnimatedDialog over `ModalBottomSheet` even for action menus: a state-driven
ModalBottomSheet leaked its window across navigation (popped up on the wrong screen
and froze) and its auto-show didn't re-run reliably after the host was paused — the
home workout menu was rebuilt as an AnimatedDialog to fix this (v0.68.3). Some
dialogs added late (calendar colour picker, manage-custom-exercises, log-wizard
confirm) still use AlertDialog/Dialog and are on the ROADMAP to convert — use
AnimatedDialog/RollPicker for anything new.

## Experimental Material3 APIs need @OptIn or the build FAILS
The release build treats the "this material API is experimental" warning as an
ERROR. Any composable using `TopAppBar`, `ModalBottomSheet`, `ExposedDropdownMenu*`,
`MenuAnchorType`, etc. must be annotated `@OptIn(ExperimentalMaterial3Api::class)`.
This bit us repeatedly (0.61.2, 0.62.1) — the sandbox can't run the Android build,
so it only surfaces in CI. Include it in the missing-import scan.

## Extension members can't be fully-qualified through their receiver
Beyond scope members (above): Kotlin extension functions/properties can't be written
fully-qualified through the receiver type — you must import the extension and call it
short. Examples that failed in CI: `Icons.Filled.Close` / `Icons.AutoMirrored.Filled.
ArrowBack` (import `androidx.compose.material.icons.filled.Close` etc.), `WindowInsets
.statusBars` (import `androidx.compose.foundation.layout.statusBars`),
`ExposedDropdownMenu` (call unqualified in the box scope), `alignByBaseline()`
(RowScope, call unqualified), `NavDestination.hasRoute`. Writing
`androidx.compose.material3.ExposedDropdownMenu(...)` does NOT resolve.

## Check for an existing symbol before declaring one
Before adding a DTO/data class/helper, grep the target file for the name — the
codebase already had `MuscleGroupDto`, and a second identical declaration failed the
build with a redeclaration error (0.68.1). Fold "grep for an existing declaration"
into the pre-package ritual.

## Custom icons: filled()/filledEvenOdd() helpers; Material core is limited
Icons live in `ui/nav/KairosIcons.kt`. `stroked(name, *paths)` for line glyphs;
`filled(name, path)` for a solid glyph; `filledEvenOdd(name, path)` for a solid glyph
with a punched-out cut (person + check/X/? attendance markers). Material Icons *core*
only ships a small set (`Check`, `Close`, `ArrowBack` are available; `Person`,
`HowToReg`, etc. are in material-icons-*extended*, which we don't depend on) — draw
custom glyphs rather than adding the dependency. To baseline-align an icon to text
(bottom of icon on the text baseline), report the icon's baseline as its bottom via a
`layout{}` modifier (`FirstBaseline`/`LastBaseline` → height) and `alignByBaseline()`
on both — see `ui/common/Attendance.kt`.

## Money screen: reward approvals are admin-gated by the device role (v0.72.0)
The Money section mirrors the web read-only `/money` page (per-person ledger +
add), plus one thing the web keeps behind the PIN: **Bible-reading reward
approval**, surfaced here only when the enrolled person's `role == "ADMIN"`
(`GET /money` sends `canApproveRewards` + `rewardMonths`, empty for non-admins).
No PIN — the device token already proves the parent. Each approval goes through
an `AnimatedDialog` confirm (per person or "Approve all [+ bonus]"). Generic
transaction approve/edit/delete, starting funds, and CSV import stay web-admin.

Layout note: the people-selector row **collapses when there's a single
participant** (a lone child sees just their ledger), and the add-form "For"
picker hides when the roster has one person. Amounts are whole **cents** on the
wire (`amountCents`), parsed from the dollar field on-device (no regex — manual
split to dodge the Kotlin backslash-escaping trap). Reused patterns: RollPicker
for every select, `DatePickerDialog` for the date (as in the calendar editor),
`collectAsState()` for screen state, and act-then-reload in the ViewModel.

## Money: full admin parity minus import; pop-up selectors; child auto-fills self (v0.73.0)
Admins manage money entirely from the phone now: the ledger's "Awaiting approval"
banner (household-wide pending) approves per-row or all at once; tapping any ledger
row (admin only) opens an AnimatedDialog to approve/unapprove, edit, or delete; a
"Set starting funds" action and the Bible-reward approvals round out parity with the
web Money admin — everything except CSV import. Gated on `data.isAdmin` from
`GET /money` (device role ADMIN); the shared web tablet keeps the PIN. The home
dashboard shows an amber `MoneyReminder` when `dashboard.money` reports pending
work; Review navigates to Money (no inline approve on Home).

Add/Edit forms: selects (Type, Category, For, frequent-payment) are now compact
rows that open a small `OptionsDialog` (AnimatedDialog) instead of inline
`RollPicker` dropdowns — the inline expansion made the form scroll and feel clunky.
Nested dialogs (OptionsDialog / DatePickerDialog opened from within the form
AnimatedDialog) work fine, same as the calendar editor's date picker. A child's
roster is just themselves, so the "For" picker is hidden and the entry auto-files
for them; a parent/admin picks anyone they can see. Reward buttons + the home
banner use the web's amber/orange palette.

## Reading (v0.74)
Self-only leisure book-tracker mirroring the web. One screen holds the reading
queue, an "Add a book" dialog (title, optional author, pages and/or chapters —
at least one), and an inline **Bookshelf** toggle grouping To read / Bookmarked /
Read (rather than a separate route, to avoid the drawer-nav reload pitfalls).
Bookmark and Shelve are two mutually-exclusive ways to move a book off the queue
onto the shelf (Bookmarked = keep your place and resume; To read = save for
later); the server clears the other flag, so "Move to reading" just clears the
one flag for that bucket (`bookmark(false)`/`shelf(false)`/`finish(false)`) —
one call, one reload, no busy-guard collision. There is no favourite/star.
State via `collectAsState()`; delete confirmed through `AnimatedDialog`.
