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
Reading tracks the **page/chapter you're up to** (`position` on the book). How
far you've read and the Scholar XP derive from it at read time, so paging back and
forth never banks extra credit — each page counts once. Mark finished sets the
position to the full length (100%). **Bookmark was removed** as redundant with
Shelve (both just moved a book to the shelf keeping your place); the shelf is now
To read / Read, and a shelved book shows where you left off. Correct a mis-typed
page via Edit. Dialogs use `AnimatedDialog` with **no tonal tint** (`tonalElevation
= 0`, `shadowElevation = 6`) so every pop-up is a uniform white — Material's
tonal elevation was tinting elevated surfaces with the teal primary. State via
`collectAsState()`; delete confirmed through `AnimatedDialog`.

## Groceries section (v0.75.0)
Mirrors the existing web feature against the new device API. **Shared family
data**, not self-only: one GET returns the whole board (stores, saved list,
active trips, catalog) and any enrolled device can add/shop/tick. Store
membership follows the **catalog**: in the add dialog, a typed name matching a
catalog item pre-selects that item's remembered store (chips hidden when there's
one store) — the same rule the web uses. Flow is hub + inline cart: **I'm going
shopping** → pick a store → a trip starts (shopper defaults to me) and that
store's card becomes a checkable cart with a got/total bar; **Done shopping**
completes it. App v1 deliberately skips the who's-shopping picker, assignees,
drag-reorder, and admin (store/catalog editing stays web-only). New dialogs use
AnimatedDialog; the store picker is chips, not ExposedDropdownMenu.

## Groceries: full-screen add wizard shares the hub's ViewModel (0.76.0)
The add flow is a full-screen route (`Route.AddGrocery`), not a dialog, so more
catalog items show at once. Its `GroceriesViewModel` is scoped to the groceries
hub's back-stack entry (`viewModel(viewModelStoreOwner = previousBackStackEntry)`),
which (a) shares state so the hub reflects the add on return without an
ON_RESUME reload, and (b) keeps the add coroutine alive in the parent scope when
we `popBackStack()` immediately after firing it — a child-screen VM would be
cleared mid-request. Item delete is gated behind a per-row edit (pencil): the
editor offers Change store (a `groceries/move` call, hidden mid-trip) and Delete
(confirmed). Change-store icon is `KairosIcons.Swap` (circular arrows).

## Groceries: drawn glyphs + page-level edit mode (0.77.0)
Some common items have no emoji (napkins, bottled water). The server guesser now
emits "ic:*" tokens for those; both web and app render them through a small
`GroceryGlyph` helper (drawn `KairosIcons`/`icons.tsx` glyph for a token, emoji
otherwise, box fallback for unknown tokens). Re-syncing the catalog upgrades
existing items. Item editing moved from a per-row pencil to a single page-level
edit toggle in the TopAppBar (pencil → "Done"): in edit mode each row exposes
change-store (saved items) and delete inline; row-tap toggles are disabled so
edits aren't mistaken for ticks. Delete in edit mode is immediate (entering edit
mode is the deliberate gate), matching the RecentWorkouts edit-mode pattern.

## Groceries: picture icons for no-emoji items; alphabetical lists (0.78.0 / web 0.254.0)
Napkins, paper towels, bottled water, and protein have no good emoji, so the
guesser emits "ic:*" tokens and both platforms render bundled artwork via the
shared GroceryGlyph (web `<img src="/grocery-icons/*.png">` from /public; app
`Image(painterResource(R.drawable.grocery_*))` from res/drawable-nodpi). Source
PNGs are trimmed onto a white rounded tile with a light border so white subjects
(napkin, paper towel) stay visible. Lists are alphabetical: saved lines, trip
lines, and cart lines order by name server-side (loadGroceries/loadCart), the
admin catalog orders by name, and the app add wizard sorts client-side. The web
add-box "Common" chips stay most-used (by design). Note: web drag-to-reorder no
longer changes saved-list display order (alphabetical wins).

## Groceries: confirm-delete + adder-only delete (app 0.79.0 / web 0.255.0)
Deleting a shopping line in the app now goes through a named confirm dialog
("Remove "<name>"?") instead of deleting on tap. Delete is also permission-
gated: the trash icon shows only when the current person added the item
(line.assignee.id == me.id) or is privileged (kind PARENT or role ADMIN). This
is enforced server-side too, on the device remove endpoint (fetches the item's
assignedToId and 403s otherwise) — the shared removeItemCore is unchanged so the
web board keeps its existing behaviour. Change-store is intentionally NOT gated.
Item "adder" is carried as assignedToId (addItemCore stamps the requester there).

## App: avatar ring parity, branded loading screen, reading save feedback (0.80.0)
PersonAvatar and the KairosRail drawer avatar now render the person's colour as a
2–2.5dp ring over a 12%-tint disc with colour-tinted initials (matching the web
Avatar's boxShadow ring), instead of a solid colour fill. LoadingScreen shows the
logo with "καιρός" (Koine Greek) in Gentium Plus (res/font/gentium_plus.ttf, SIL
OFL). Reading save clears field focus (clearFocus) and flips the Save button to a
lighter "Saved ✓" state, reset when the page value is edited.

## App: workouts restyle + avatar ring scaling (0.81.0)
Workout screens were overriding the Scaffold containerColor to surface (white),
which flattened the page (white content on white). Removed those overrides so
they use the default background (PageGrey), and moved content onto white
OutlinedCards / bordered surfaces (WorkoutsScreen progress/week/today/recent,
BrowseWorkouts rows, WeightCalculator barbell, RecentWorkouts list, EditPlan day
cards). PersonAvatar's ring width now scales with size (size/22, clamped
1.2–2.5dp) so small chores avatars get a lighter ring.
NOTE: CreatePersonalWorkoutScreen had its white-bg override removed too but its
full redesign into a multi-step wizard (name → exercises → instructions → review,
dropping "time cap") is still pending.

## App: Create/Edit personal workout is a 4-step wizard (0.82.0)
CreatePersonalWorkoutScreen was rebuilt as a wizard over the same ViewModel:
step 1 name+type (new/pick-existing, rename, delete), step 2 exercises (pool
picker, add/edit custom), step 3 instructions (placeholder mirrors the web admin
hint "example: 100 thrusters…"), step 4 a read-only review (name, type,
exercises, instructions). Header reads "Create / Edit" over "HIIT/CrossFit".
Time cap was dropped from the UI — submit sends capSec=null (cap logic stays
dormant in the VM). The existing view-locked-until-Edit model is preserved: an
existing workout opens read-only across steps; the top-bar Edit unlocks it.

## App: Characters section + branded splash hold (0.84.0 / web 0.259.0)
Characters mirrors the web /summary personal view via GET /api/v1/characters
(loadPersonProgress for the enrolled device's own person only). Companion sprites
are served device-authed from GET /api/v1/companions/* (mirror of /public/companions,
same pattern as avatars — /public is behind Authelia). The screen renders the
companion card (creature/egg with the skill-blend glow), level/XP, season tier,
stat bars, streak+badge chips, and mastery. Startup now holds LoadingScreen for
~2s (min-splash gate in AppRoot) so the logo + καιρός is actually seen.
PENDING: the collection page (6 eras, mystery-until-unlocked slots, tap-to-enlarge)
is the next Characters increment.

## App Characters: companion render fix + family goal + hatch (0.85.0 / web 0.260.0)
The companion sprite wasn't showing because the AsyncImage had only a height
constraint (+ empty loading/error) — it now uses fillMaxWidth().height(120) with
ContentScale.Fit, a loading spinner, and an egg-emoji error fallback. Added a
level-progress bar under the creature, the Family goal card (from the endpoint's
new familyGoal, derived from loadCoop), and hatch controls that call
POST /api/v1/characters/hatch ("new"/"deepen") via a shared hatchEggCore. Hatch
reloads the character after. Note the deep endpoint reads /public via
process.cwd() (Next standalone's documented path; Dockerfile copies public to
/app/public).

## App Characters: gallery + segmented XP bar + action cards (0.86.0 / web 0.261.0)
Season name replaces the "Characters" title. The XP bar renders server-computed
`companion.xpCells` (20 domain-coloured cells, mirroring web XpBar). Hatch/Deepen/
Gallery are OutlinedCard actions below the companion card (workout ActionCard
style), conditional (Hatch when eggReady, Deepen when eggReady && active, Gallery
always). Gallery (Route.Gallery) loads GET /api/v1/characters/collection: eras
with owned (art, tap-to-enlarge) vs mystery (rarity-ringed "?" slot). Companion
image endpoint now tries process.cwd()/public and /app/public to survive cwd
differences (the earlier "egg fallback" was this endpoint 404ing).

## App: Family goal / co-op screen (0.92.0 / web 0.268.0)
The Family goal card (CharacterScreen) is now clickable → Route.Coop → CoopScreen,
backed by GET /api/v1/coop and POST /api/v1/coop/{propose,vote,select,grant,remove}.
Shared cores live in src/lib/coop-core.ts; the web actions (actions/coop.ts) and
device routes both delegate, so the web /coop page is unchanged. propose/vote are
open to any device (proposer/voter = self); select/grant/remove require the device
person be privileged (role ADMIN or kind PARENT), enforced in the routes.

## App: School section (0.93.0 / web 0.269.0)
Mirrors the web personal School page. GET /api/v1/school scopes by the device
person the same way personalVisibleIds does — CHILD sees only self, PARENT (kind)
sees self + all active children — while add/delete are gated to admins-or-self
(matching requireCanActFor) and returned as `canActFor`. Completing an item reuses
the existing tasks/{id}/complete endpoint (the item is a SCHOOL Task). Shared
add/delete cores in src/lib/school-core.ts; the web School page + actions are
unchanged. Due-date picker uses java.time (already used elsewhere in the app).

## App School: add wizard, view-only for others, edit toggle (0.94.0 / web 0.270.0)
Add moved to a full-screen wizard (Route.AddSchool, Material3 DatePicker), sharing
the section's SchoolViewModel via previousBackStackEntry so the add reflects on
return. Interaction is owner-scoped: tick-off (complete) and the top-right Edit
toggle (rename own items inline / delete with a confirm dialog) show only on the
card where person.id == meId; a parent viewing a child's card is read-only.
Rename batches via vm.applyRenames (single act/reload; the per-call busy guard
would otherwise drop concurrent renames). Web adds meId to /api/v1/school and a
/school/rename endpoint (owner-or-admin), delegating to school-core.

## App Tasks section + assign wizard (0.96.0 / web 0.271.0)
New "tasks" section (Route.Section) + Route.AssignTask wizard, launched from a +
on Home and from the Tasks page. General tasks are Category.OTHER; GET
/api/v1/tasks groups them by person (CHILD self, PARENT self+kids), open+complete;
POST /api/v1/tasks/add (admin-or-self) defaults a blank due date to today so it
lands on Home. Complete/uncomplete reuse tasks/{id}. Interaction is owner-scoped:
the tick circle shows only on your own group (userId == meId); parents view kids'
read-only. The wizard scopes TasksViewModel to previousBackStackEntry (add
coroutine survives the pop); onClose bumps AppRoot.homeRefresh so Home reloads and
shows the new task. NOTE: new DTO named TaskUserGroupDto to avoid colliding with
the dashboard's existing TaskGroupDto.

## App: AlertDialog -> AnimatedDialog cleanup (0.98.0)
Converted all remaining stock Material3 AlertDialogs to the house AnimatedDialog
(Home event detail, AppRoot sign-out, Bible PlanWizard/PersonalPlanSection,
workout wizard x3, RecentWorkouts, Calendar x4, CalendarAddEvent x2). AnimatedDialog
takes title as a String; where a dialog had a styled/composable title (Home event
detail) it moved into the content lambda. AlertDialog's `text` maps to AnimatedDialog's
`content` (passed as a named arg, so no reordering). Game time hidden from nav/drawer
on both web and app (code kept for later).

## App: offline support, phase 1 — read-through HTTP cache (0.99.0)
Offline reads for every GET at once, done in the network layer rather than per-DTO
Room tables: an OkHttp disk Cache (15 MB) plus two interceptors in ApiClient.create.
A network interceptor rewrites GET responses to "Cache-Control: public, max-age=0"
so they're STORED but always revalidated online (the server sends no-cache) — max-age=0
(not >0) so a reload right after a write still fetches fresh, never a stale copy.
An application OfflineInterceptor, when NetworkMonitor.isOnline() is false, rewrites
GETs to only-if-cached + 30-day max-stale (serves the stored copy), turns a cache
miss (504) into a clean "offline, no saved copy" IOException, and fails writes fast
with "reconnect to make changes". NetworkMonitor uses ConnectivityManager and
requires NET_CAPABILITY_VALIDATED (correct for the Cloudflare-tunneled server, which
needs WAN; revisit if a LAN endpoint is added). httpCache.evictAll() on enroll /
signOut / changeServer prevents one user's cache leaking to the next on a shared
device. OfflineBanner (bottom, slides up) is driven by NetworkMonitor.online in
AppRoot. Manifest gains ACCESS_NETWORK_STATE. PHASE 2 (later): optimistic write
queue so changes made offline replay on reconnect.

## App: offline support, phase 2 — write queue + replay (0.100.0)
Generic persisted request queue at the network layer, so every write is covered
with no per-endpoint code. When offline, OfflineInterceptor buffers the request
body and enqueues a PendingWrite (method + full url + body) into WriteQueue (a
JSON blob in DataStore, survives restart), then returns a synthetic 200 "{}" so
the action isn't lost or shown as an error (it deserializes to a default DTO;
the write is enqueued regardless of how the body deserializes). Auth calls
(/auth/*, *revoke) are never queued. SyncManager watches NetworkMonitor.online and
on reconnect replays the queue in order via a plain OkHttp client (auth only, no
cache/offline interceptors) on Dispatchers.IO under a Mutex: 2xx or 4xx removes the
item (4xx = stale/invalid, dropped rather than retried forever), 5xx/network stops
the pass to retry later. After a pass it evicts the read cache and bumps
revision; AppRoot bumps homeRefresh on revision so Home reloads the true state.
OfflineBanner now shows offline/pending-count/syncing. Added defaults to
WorkoutAckDto so offline workout writes deserialize cleanly (the other simple ack
DTOs already had them). LIMITATION: offline writes aren't fully optimistic on
reload-based screens (a reload reads the stale cache), but the change is captured,
the pending count reflects it, and it syncs on reconnect. Rich-response writes
(hatch, trip start, plan preview) still queue but their offline synthetic result
is empty. Phase 3 (optional): per-screen optimistic state for instant offline UI.

## App: connectivity monitor fix (0.101.0)
The offline banner could get stuck / not appear until an app relaunch. Cause:
NetworkMonitor listened on registerDefaultNetworkCallback and re-queried
cm.activeNetwork + its capabilities *inside* each callback — that read lags right
after a network transition, so the flow could latch a wrong value (esp. the
NET_CAPABILITY_VALIDATED check on reconnect). Rewritten to track the set of
connected networks directly from the callback events (registerNetworkCallback with
a NET_CAPABILITY_INTERNET request; add on onAvailable, remove on onLost), so
online = the set is non-empty and updates the instant Android reports a change.
Dropped the VALIDATED requirement to guarantee the bar never wedges (tradeoff: a
no-internet Wi-Fi briefly reads online and a request just fails rather than serving
cache). isOnline() now returns online.value so the interceptor and UI share one
source of truth. Signature unchanged; no other files affected.

## App: offline phase 3 — optimistic completion on Home + Tasks (0.102.0)
Per-screen optimistic UI for the most common offline action (checking things off),
scoped to Home dashboard + Tasks page as agreed. Pattern: flip local state
immediately (Home: mutateTaskStatus toggles a TaskDto.status in groups+overdue;
Tasks: moveTask shifts a task between open/done), fire the write, then
session.isOnline() ? reload (refresh server-derived %/bars) : keep optimistic
(a reload would read the stale offline cache and undo it). Revert to the captured
`before` state on a real ApiException. SessionRepository.isOnline() added
(delegates to networkMonitor) as the single source. Home TaskRow no longer shows a
per-row spinner while busy — the optimistic status IS the feedback; busyIds still
guards double-tap of the same row. KNOWN edge: rapidly toggling two DIFFERENT tasks
online can briefly flicker (one row's reload lands before the other's write), self-
correcting; offline has no reload so no flicker. add/claim/reading/workout on Home
and add on Tasks are NOT yet optimistic (out of scope). Extend the same pattern to
other screens/actions later if wanted.

## App: offline robustness + optimistic add (0.102.1)
Two fixes from a field repro (open app, airplane-on, open a never-loaded screen ->
spinner hung; add-a-task offline didn't appear):
1) NetworkMonitor.isOnline() now returns a FRESH synchronous snapshot() (activeNetwork
   + INTERNET) instead of online.value. The reactive flow value can lag the radio by a
   beat, so a request fired right at airplane-on slipped past the offline check to the
   network and hung; a fresh read decides on current state and takes the offline path
   (only-if-cached -> 504 -> clean "offline, no saved copy" error) immediately. The
   reactive `online` flow still drives the banner.
2) Added OkHttp callTimeout(20s) (+ connectTimeout 12s) as a hard backstop so no
   request can spin indefinitely.
3) TasksViewModel.add is now optimistic: insertTask() drops a temp-id TaskOpenDto into
   the target person's group and closes the wizard immediately; online it reloads to
   swap in the real row, offline it keeps the temp until sync (a 4xx on a temp-id
   completion would just be dropped). Removed the now-dead act() helper.
   NOTE: add from the Home + still won't show on Home optimistically (wizard shares the
   Tasks-section VM, not HomeViewModel) — shows after sync; extend later if wanted.

## App: durable offline optimism — apply queue on load + refresh on sync (0.103.0)
Fixes two field bugs: (1) an offline change vanished when navigating away from a
section screen (its VM, holding the optimistic state, is destroyed by popUpTo(Home));
(2) after reconnect the screen didn't refresh until a manual nav. Foundation:
SessionRepository.pendingWrites() exposes WriteQueue.snapshot(). Each screen's load
now re-applies the still-unsynced writes on top of the (possibly cached) response, so
optimistic state is reconstructed from the DURABLE queue rather than living only in
the VM: TasksViewModel.freshData() = applyPending(loadTasksList(), pendingWrites())
handles tasks/add (parse AddTaskRequest -> insert temp row), tasks/{id}/complete|
uncomplete (moveTask); HomeViewModel.freshDashboard() applies complete/uncomplete
(mutateTaskStatus). Once a write syncs it leaves the queue, so a later load shows the
real row. Auto-refresh: TasksScreen now takes refreshKey = syncManager.revision (Home
already bumps homeRefresh on revision), so an open screen reloads the instant a sync
lands. This is the REFERENCE pattern for extending optimistic UI to the other screens
(calendar, workouts+create, chores, bible, reading, school, groceries, money): each
needs (a) optimistic local mutation on its actions, (b) applyPending in its load, and
(c) refreshKey = revision. NOTE: add-from-Home-+ still not optimistic on Home's
dashboard (would need a synthetic categorized TaskDto); shows after sync.

## App: dashboard optimistic add (0.104.0)
HomeViewModel.freshDashboard now also applies queued tasks/add writes: for a pending
add whose userId == session.currentPersonId() (added SessionRepository.currentPersonId()
from SessionState.Ready.person.id), insertDashTask drops a temp-id completable TaskDto
into the dashboard's OTHER group (labelled "Tasks", created if absent). Combined with
the existing complete/uncomplete apply + homeRefresh-on-revision, an offline-added
self task now shows on Home and the Tasks page, survives nav + app restart, and
reconciles on sync. Batch 1 (chores/bible/reading/school optimistic) still to do,
one screen at a time per the established pattern (optimistic mutation + applyPending
in load + refreshKey = revision).

## App: School optimistic offline (0.105.0) — batch 1, screen 1
Applied the durable-optimistic pattern to School. SchoolViewModel: optimistic()
helper (mutate now, write, online? freshData() : keep, revert on error); add ->
insertItem, complete -> removeItem, delete -> removeItem, rename/applyRenames ->
renameItem. freshData() = applyPending(loadSchool(term), pendingWrites()). applyPending
parses school/add (AddSchoolRequest -> insertItem, resolves typeLabel from data.types
and className/color from data.classOptionsByUser), school/delete (SchoolTaskIdRequest),
school/rename (SchoolRenameRequest), and tasks/{id}/complete (removeItem — school
completions ride the shared task endpoint). removeItem/insertItem keep the person's
pending/overdue counts in sync; temp-id used for optimistic inserts. SchoolScreen gains
refreshKey = syncManager.revision (wired in AppRoot) so it reloads when a sync lands.
Batch 1 remaining: reading, bible, chores.

## App: Reading + Chores optimistic offline (0.106.0) — batch 1, screens 2-3
Reading (ReadingViewModel): rewrote to the durable pattern. One mutate() helper
(keeps the saving/busy flag split the screen relies on) applies the change, closes
the sheet, writes, then online? freshData(): keep, revert on error. Transforms:
insertBook/updateBook/setRead/setFinished/setShelved/removeBook. applyPending parses
books/{add,update,log,finish,shelf,delete}. ReadingScreen gains refreshKey =
revision. Chores: the Chores SECTION is display-only, so the actions live on Home.
Made HomeViewModel.claimChore (remove from upForGrabs) and completeAlwaysOpen
(bumpAlwaysOpen -> myCount+1) optimistic, and freshDashboard now also applies
chores/claim (ClaimChoreRequest) and chores/always-open (AlwaysOpenRequest).
Batch 1 remaining: Bible (mark day / save book / bulk / plans — its chapter-grid
ReadingDto needs its own careful transforms, next).

## App: offline reliability net + more Home population (0.107.0)
Root fix for "queued but not shown" (e.g. an add wizard whose VM wasn't shared with
the screen behind it): AppRoot now derives a dataRevision that bumps on any change to
syncManager.revision OR pendingCount, and passes it as refreshKey to Tasks/School/
Reading (and bumps homeRefresh). So whenever the offline queue changes, the visible
screen reloads and re-derives via applyPending from the durable queue — the change is
guaranteed to appear regardless of which VM made it. This is a structural guarantee,
not a per-scenario patch. Also: HomeViewModel.freshDashboard now applies school/add
(insertDashSchool -> SCHOOL group, self only) and reading/mark (personalReading.read);
togglePersonalReading made optimistic. Reading add verified safe (flat list append).
Bible section + calendar->home come with their batches. Batch 1 remaining: Bible.

## App: Bible optimistic offline (0.108.0) — batch 1 complete
BibleViewModel: optimistic markDay (toggleDay flips the matching plan day's read),
saveBook (setBookChapters rewrites that book's "Book|Chapter" readKeys), bulkBooks
(bulkSetBooks adds/removes all chapter keys using BIBLE_BOOKS chapter counts),
deletePlan (clearPlan). freshData = applyPending(loadReading, pendingWrites) parses
reading/mark, reading/books, reading/books/bulk, reading/plan/delete. Coverage grids
are client-derived from readKeys so they update immediately; the server-computed stat
bars lag until the next sync. createPlan stays non-optimistic (server generates the
day schedule) — do it online. BibleScreen refreshKey = dataRevision. Bible->Home:
the day's passage already flows via reading/mark applied in freshDashboard (0.107.0).
Batch 1 DONE (school, reading, chores, bible). Also fixed the offline-no-cache message
wording in ApiClient. Next: batch 2 (groceries, money), then batch 3 (calendar,
workouts+create), then settings.

## App: Groceries optimistic offline (0.109.0) — batch 2, screen 1
GroceriesViewModel: optimistic add (insertLine temp row), addFromCatalog
(insertFromCatalog resolves catalog id -> name/icon/defaultStore), remove
(removeLine from saved + trip items), move (moveLine storeId), setPurchased
(setPurchasedLine in saved + trip items), completeTrip (drop the trip). freshData =
applyPending(loadGroceries, pendingWrites) parses groceries/{add,add-catalog,remove,
move,purchased,trip/complete}. startTrip stays non-optimistic (creates a server trip
+ navigates). GroceriesScreen refreshKey = dataRevision. No Home representation for
groceries. MONEY DEFERRED to next turn: it looks similar but has server-computed
balances (from entries + starting funds + reward logic) and an approval workflow
(approve/unapprove/all + reward-month/base), so it's a bigger, more nuanced job -
doing it carefully next rather than stacking two complex models.

## App: Money optimistic offline (0.110.0) — batch 2 complete
MoneyViewModel: one optimistic(form,onDone,mutate,write) helper (form=adding flag,
else approving flag). addEntry->insertRow (only if req.userId == data.selectedId),
updateEntry->updateRow, delete->removeRow (+ pendingApprovals), approve/unapprove->
setApproved (row status + pendingApprovals), approveAll->approveAllRows, reward month/
base->setRewardMonth/setRewardBase (flip bonusAvailable / completer.needsBase).
freshData(user)=applyPending(loadMoney(user), pendingWrites()) parses money/{entry,
update,delete,approve,unapprove,approve-all,rewards/approve-month,rewards/approve-base}.
Per-person balanceCents are server-computed so they lag until sync (rows/approvals
update at once). setStarting stays non-optimistic (recomputes balances). MoneyScreen
refreshKey = dataRevision. Batch 2 DONE. Next: batch 3 (calendar, workouts+create),
then settings.

## App: Calendar optimistic offline (0.111.0) — batch 3, screen 1
Rather than per-action optimistic mutates (calendar caches multiple views), routed
EVERY calendar load through loadCal = applyPending(session.loadCalendar(view,date),
pendingWrites()) - the existing create/update/delete already call load() after the
write, so offline changes reload from cache + re-apply the queue and show durably.
applyPending parses calendar/event (insertEvent, only if req.date in rangeDays; adds
a monthDot), calendar/event/update (updateEventIn), calendar/event/delete (removeEvent,
scope "one" removes that occurrence else all). Event color/owner/who are placeholders
until sync; recurring events show only the first occurrence optimistically. CalendarScreen
refreshKey = dataRevision -> vm.reload(). Home agenda: freshDashboard applies calendar/event
for today (if isFamily / self / a participant) via insertDashSchedule. calendar/prefs not
queued (device pref applies locally). Batch 3 remaining: Workouts (+ create wizard).

## App: offline add-then-delete cancellation (0.112.0)
An item created offline gets a temp id equal to its queued create's id: applyPending
now inserts created items with id = "temp-${w.id}" (w = the PendingWrite) instead of a
fresh UUID, so the on-screen temp id IS the pending-create's id. When a delete/remove
write is enqueued offline, OfflineInterceptor scans the URL+body for "temp-<id>"; if
found it removes that pending create from the queue and returns a synthetic 200 WITHOUT
queuing the delete (add+delete offline = net no-op). Covers reading/groceries/school/
money/calendar (all put the id in the body; regex also checks the URL). Immediate in-VM
optimistic inserts still use a random temp id (default param) - the stable id lands on
the first queue re-apply, which is well before the user can tap delete. Dashboard temp
inserts left as random (not deletable there). Interceptor synthetic() helper extracted.
Batch 3 remaining: Workouts (+ create/edit wizard) - the last screen, doing it solo next.

## App: Workouts optimistic offline (0.113.0) — batch 3 complete
Daily-use logging only (the frequent action). WorkoutLogViewModel.load() now routes
through freshPlan = applyPending(loadWorkout, pendingWrites): parses workouts/{complete,
rest,log} -> loggable=false, workouts/uncomplete -> loggable=true, matched by date ==
plan.date. So a workout marked offline reads as done on the main screen. WorkoutsScreen
refreshKey = dataRevision (reloads plan + progress + week). Home dashboard: workoutOp
(markWorkoutDone/restDay -> "COMPLETE", undoWorkout -> "PENDING") now optimistic via
mutateTaskStatus; freshDashboard applies workouts/complete|rest -> setWorkoutStatus
COMPLETE, workouts/uncomplete -> PENDING (matched by isWorkout && dueDate == date).
Progress chart bars are server-computed so they lag until sync.
DEFERRED (online-only, intentional, like reading-plan create): create/edit personal
workout (CreatePersonalWorkoutViewModel), plan editor + rotation + movement mgmt
(EditPlanViewModel) - server generates the plan/workout structure, faking it offline
is risky. These queue offline and reconcile on sync. Batch 3 DONE. Offline optimism
effort complete for all daily-use flows. Next: Settings menu.
