# Changelog
## 0.152.0
- Task alerts now have Open and Complete actions. Open takes you to the home dashboard where the
  task lives; Complete checks it off in the background and dismisses the alert (recurring tasks
  return on their next date). Tapping the alert opens the dashboard too.

## 0.151.0
- Task alerts: under Advanced, "Remind me" adds a notification time (a clock that follows your Time
  format setting). One-off tasks alert on the due date; recurring tasks alert at that time on each
  occurrence. Alerts fire through the same scheduler as calendar reminders, respect the Task alerts
  toggle, and tapping one opens Tasks.

## 0.150.0
- Turning on Repeat for a task now clears the due date — a repeating task schedules itself, and its
  end comes from how long it repeats — with a one-time note explaining why.

## 0.149.0
- Settings -> Appearance: "24-hour time" is now "Time format" — tap it to pick System time, 13:00,
  or 1 PM from a pop-up, with the current choice shown beneath the label. "System time" follows the
  device's own clock setting.

## 0.148.0
- Settings -> Notifications: "Reminders" is now "Calendar reminders", and there's a new "Task
  alerts" toggle alongside it. Turning either on prompts for notification permission if needed.

## 0.147.0
- Assigning a task now has an "Advanced" toggle that reveals the due date and a "Repeat" option.
  Turn on Repeat to make it recurring — frequency (daily/weekly/monthly), interval, weekdays, and
  an end (never / after N times / until a date) — the same options as web admin.

## 0.146.0
- Assigning a task now defaults to you (the parent/admin) at the top of the "For" list, instead of
  whichever person the server happened to list first.
- Recurring tasks show a repeat icon before their name in the task list.

## 0.145.0
- Removed the retired username/password + enrollment-code setup screens, now replaced by the
  invitation-code and lock-screen flow.
- The join screen's "confirm your password" step (adding a phone) now has a "Forgot your
  password?" link that emails a reset code, so a forgotten password isn't a dead end there.

## 0.144.0
- Logging out now locks the phone instead of un-enrolling it: the phone stays yours, and you get
  back in with your username and password — no new code needed. A brand-new phone (or one a
  parent revokes in Household admin) still needs an invitation code.
- Setting up a blank phone is a single "enter your invitation code" screen (create a password,
  confirm to add a phone, or set a new one for a reset). The old separate sign-in + child-device
  code setup is gone.

## 0.143.0
- "Have an invitation code?" on the sign-in screen now takes the new short invitation code (or a
  link): enter it to create your password (new account), confirm it (add a phone), or set a new one
  (reset), and the phone enrolls in one step.

## 0.142.0
- Forgot your password? The sign-in screen now has a "Forgot your password?" option: enter your
  name or email and, if an address is on file, a single-use reset link is emailed to you. Opening
  it in the app lets you set a new password and enrolls the phone.

## 0.141.0
- New-account onboarding in the app: tapping an invite (a "kairos://join" link), or pasting it
  via "Have an invite link?", opens a screen to create your password (new account) or confirm it
  (returning to add a phone), and enrolls the device in one step. No browser, no Authelia.

## 0.140.0
- Turn off your own reminder for an event: open it, tap Edit, and switch off "Remind me about
  this". It affects only your reminder (and the whole series for a repeating event) — everyone
  else’s stays on.

## 0.139.0
- Groundwork for per-person event reminders: a new event's reminders now go to just you
  (a personal event) or everyone (a family event). Choosing individual recipients on the
  web is coming next.

## 0.138.0
- The "Other" event type is now "Medical / Dental", and these events are red by default.

## 0.137.0
- Tapping an event now shows its type and the reminders currently set on it, before you edit.

## 0.136.0
- New calendar events now start with a default reminder based on their type: Appointment
  and Class 30 minutes before, Work shift 15 minutes, Birthday and Other none. Existing
  events are never changed.
- New Settings → Default reminders screen to set the default reminder for each event type.

## 0.135.0
- Event locations are now tappable: open an event's details and tap its location to launch it
  in your maps app, ready to navigate.
- Reminder notifications for events that have a location now include a "Navigate" action that
  opens the spot in your maps app. Tapping the notification itself still opens the calendar.

## 0.134.0
- Tapping a calendar reminder notification now opens Kairos straight to the calendar.
- New events no longer default to a time that's already passed: the start time rounds up to
  the next 15 minutes (e.g. at 10:10 it defaults to 10:15) instead of the top of the hour.

## 0.133.0
- Fixed calendar reminders not firing: creating or editing an event now reschedules its
  reminder alarms immediately, instead of only after the next app launch or the background
  refresh. (Previously an online event change didn't trigger a reschedule at all.)

## 0.132.0
- Text fields (event titles, names, notes, and the like) now auto-capitalize the first
  letter as you type. Numeric, email, password and address fields are unaffected.
- Calendar time picker now matches your clock setting: it shows 24-hour when military
  time is on, instead of always AM/PM.

## 0.131.0
- Software update: fixed the install-permission step. After you allow Kairos to install
  apps and return, the screen now re-checks the setting and continues straight to the
  download, instead of getting stuck on the "Allow installs" prompt. That prompt now only
  appears when the permission isn't granted yet.

## 0.130.0
- Software update screen: the installed version number now uses your theme color.

## 0.129.0
- In-app updates (part 2): the Software update screen can now download and install an
  update directly. Tap "Download & install", allow Kairos to install apps once, and the
  system installer takes over. No browser involved; updates install in place.
- Software update screen: the installed version now shows as plain text (no boxed field
  that looked editable), in the theme's text color, with the build date next to it.

## 0.128.0
- The "update available" badge dot is now light blue.

## 0.127.0
- In-app updates (part 1): Kairos now checks GitHub for a newer release on launch
  (and in the background), with no browser involved. When one is available, a green
  dot appears on the settings gear and an "Update available" row shows in the menu.
- New Settings → Software update screen: shows your installed version, what's new in
  the available version, and a "Check for updates" button. (One-tap install lands next.)

## 0.126.0
- Offline queue hardening (from a security review):
  - A queued offline write now stores a server-relative path and is always replayed
    against the server you're currently signed into — it can never be sent to a stale
    or different host.
  - Your device token is only ever attached to requests going to your configured
    server (API, sync, and avatar image loading), never to any other origin.
  - The queue is cleared automatically on sign-out, re-enrollment, a server change, or
    an expired login, so one person's pending writes can never replay under another.
  - Replaying a write is smarter about failures: temporary problems (auth, timeout,
    rate-limit, server errors) are retried; only writes the server flatly rejects are
    dropped, and those are now logged/counted instead of vanishing silently.
  - The offline queue is capped so a long outage can't grow it without bound.

## 0.125.0
- Calendar: switching views (day, 3-day, week, month, agenda) now always returns to today,
  instead of keeping the date you had paged to.
- Event editor: "Share with" no longer lists the event's owner (you can't share with
  yourself), and uses the standard share icon.
- 24-hour clock now reads 01:00 / 13:00 / 23:00 (with minutes) on the calendar axis.
- Settings: Notifications description updated. Spelling: "colour" is now "color" (American).

## 0.124.0
- Offline handling for profile settings: changing your colour offline is queued and syncs
  when you reconnect (and the drawer ring updates then). Changing your photo needs a
  connection — it now says so instead of failing silently. Appearance and the notifications
  switch are on-device settings, so they already work offline.

## 0.123.0
- Reminders now fire. With reminders turned on, each event's reminder posts a notification
  (the event's name) at the right time — at start, or the minutes/hours/days/week before
  you chose. Recurring events remind on each occurrence; reminders re-schedule after a reboot
  and when you change an event.

## 0.122.1
- Notifications settings: a "Sound & vibration" link that opens Android's own settings
  for the reminder channel, where you control sound, vibration, pop-ups, and on/off.

## 0.121.0
- Event editor: add reminders to any event — tap "Add notification", pick a time (at
  start, 10/15/30 min, 1 hour, 1 day, 1 week, or a custom amount), and add more than one.
  Each shows a bell and how long before, with an X to remove. New events pre-fill the
  event type's default reminder.
- Settings: clearer section descriptions.

## 0.120.0
- Settings → Notifications: simplified to a single on/off switch (with a plain On / Off
  status) plus the test button. Reminder times will live on each event instead.

## 0.119.0
- Settings → Appearance: a 24-hour (military) time switch — the calendar's hour axis
  and times show as 13:00 instead of 1:00 PM.
- Notifications settings simplified: a master switch, which events (yours / all / family),
  and birthdays. Per-event reminder times now live on each event (coming to the event
  editor next), rather than a per-type list here.

## 0.118.0
- Settings → Notifications (first part): choose what you're reminded about — a master
  switch, which events (yours / all / family), birthdays, and each event type, each with
  its own lead time (at start, 15 min, 1 hour, or 1 day before). All off by default, and
  a "Send a test notification" button to check it works. Actually firing reminders at event
  times comes in the next update.

## 0.117.0
- Settings → Profile finished: change your photo and frame it (drag to move, pinch to
  zoom) right on the phone, and pick any custom colour — not just the eight presets —
  with your last custom colour remembered. Your photo, framing and colour sync to the web
  and everywhere they show.

## 0.116.0
- Settings → Profile: pick your colour — your avatar ring, your calendar events, and
  everywhere your colour shows, on the web too. (Changing your photo and how it's framed
  is coming next.)

## 0.115.0
- Settings → Appearance: dark mode, plus a colour theme for the whole app — teal
  (default), olive drab, green, blue, purple, pink, orange, or red. The accent,
  sidebar, buttons, highlights and backgrounds all follow your choice. Stored per
  device, so each tablet or phone can look how you like.

## 0.114.0
- Added a Settings screen, reached by a gear next to your name in the menu drawer.
  It lists what's coming — Appearance (dark mode + colour themes), Profile (photo,
  framing, your colour), and Notifications — which roll out over the next updates.

## 0.113.0
- Offline optimism now covers Workouts: marking today's workout done, taking a rest
  day, undoing it, and logging a workout all update instantly — on the workout screen
  and on the Home dashboard — survive navigation/restart offline, and reconcile on
  sync. (Building/editing workouts and plans stays online-only, since their structure
  is generated server-side.)

## 0.112.0
- Offline changes that cancel each other out no longer leave a trace: if you add
  something offline and then delete it before reconnecting, both are dropped, so
  nothing syncs and the item can't reappear. Works across reading, groceries, school,
  money and the calendar.

## 0.111.0
- Offline optimism now covers the Calendar: adding, editing and deleting events show
  right away (across day/week/month views), survive navigation/restart offline, and
  reconcile on sync. An event you add for today also lands on the Home agenda.

## 0.110.0
- Offline optimism now covers Money: adding, editing and deleting transactions, and
  approving / unapproving (including approve-all and the Bible-reward approvals) all
  update instantly, survive navigation/restart offline, and reconcile on sync. The
  per-person balance figures are computed on the server, so they catch up on the next
  sync while the ledger itself updates right away. (Setting starting funds stays
  online-only, since it re-computes balances.)

## 0.109.0
- Offline optimism now covers Groceries: adding items (typed or from the catalog),
  ticking them off, moving them between stores, removing them, and finishing a
  shopping run all update instantly, survive navigation/restart offline, and
  reconcile on sync.

## 0.108.0
- Offline optimism now covers Bible: marking a plan day, marking a book's chapters,
  bulk-marking books, and deleting a plan all update instantly (the coverage grids
  fill right away), survive navigation/restart offline, and reconcile on sync.
  (Creating a plan is still online-only — its schedule is generated server-side.)
- Reworded the offline message shown when a page was never loaded: "You're offline
  — this page hasn't been cached yet."

## 0.107.0
- Offline reliability: any change you make offline now reliably appears on the
  screen you're on (and reconciles on sync) — the visible page re-derives itself
  from the pending-changes queue whenever it changes, so a change can no longer be
  queued without showing.
- School assignments you add offline now also show on the Home dashboard (under
  School), and ticking the day's Bible reading on Home is instant too.

## 0.106.0
- Offline optimism extended to Reading (add/edit a book, set your page, finish,
  shelve, delete — all instant and durable offline) and to the chore actions on
  Home (claiming an up-for-grabs chore and tapping an always-open chore done). Both
  survive navigation and restarts offline and reconcile on sync.

## 0.105.0
- Offline optimism now covers School: adding work, ticking it off, renaming, and
  deleting all update instantly and survive navigation and app restarts while
  offline, then reconcile automatically once you're back online. The page also
  refreshes itself the moment a change syncs.

## 0.104.0
- A task you add offline now also shows immediately on the Home dashboard (under
  Tasks), not just the Tasks page — and it survives navigation and app restarts
  until it syncs, same as before.

## 0.103.0
- Offline changes now survive navigation: a task added or ticked off offline stays
  put when you leave the Tasks/Home screen and come back, instead of vanishing until
  it syncs. And once you reconnect, the screens refresh themselves the moment the
  change syncs — no more navigating away and back to see it.

## 0.102.1
- Fixed the loading spinner hanging when opening a screen for the first time while
  offline — it now fails fast to a clear "you're offline" message with Retry, and
  no request can hang for more than 20s.
- Adding a task now shows it immediately (optimistically), online or offline,
  instead of only appearing after it syncs.

## 0.102.0
- Offline (phase 3): ticking a task off now updates instantly — on the Home
  dashboard and the Tasks page — even offline, instead of waiting for the change
  to sync. Online it still refreshes the day's totals right after; offline the
  tick sticks and the change is queued. A genuine failure reverts the tick.

## 0.101.0
- Offline: fixed the "You're offline" bar getting stuck (or not appearing) until
  you reopened the app. Connectivity is now tracked live from the system's network
  events, so the bar shows and clears within a second of losing/regaining a
  connection — no relaunch or manual refresh needed.

## 0.100.0
- Offline support (phase 2): changes you make while offline (ticking things off,
  adding, editing) are now saved and sent automatically once you're back online,
  instead of being blocked. The bottom bar shows how many changes are waiting and
  a "Syncing…" state while they upload; screens refresh to the real result when
  it's done. The queue survives closing the app.

## 0.99.0
- Offline support (phase 1): screens now keep showing their last-synced data when
  you lose connection, with a slim "You're offline" bar at the bottom. Reads fall
  back to a local cache of the last successful response for every screen; changes
  (ticking things off, adding, etc.) wait until you're back online with a clear
  message. The cache is cleared on sign-out so a device never shows a previous
  user's data.

## 0.98.0
- Consistency: every pop-up now uses the app's animated dialog (converted the last
  stock AlertDialogs in Home, Calendar, Bible, and the workout wizard).
- Hid the Game time section from the drawer (kept for a future enhancement).

## 0.97.0
- Tasks: new checkbox-style Tasks icon (clearer vs Chores). Assign-task example
  text now reads "Wash the car".

## 0.96.0
- Tasks: new section plus an "assign a task" button (+) on the Home screen. The
  wizard asks who it's for, the task name, and an optional due date (calendar
  picker) — the task then shows on that person's Home until done. The Tasks page
  lists tasks by person (a child sees only their own), open and completed, with a
  "Show/Hide done" toggle. You tick off your own tasks; parents view the kids'.

## 0.95.0
- School: the Add school work wizard now shows its form on a white card (matching
  the workout wizard) instead of on the bare background.

## 0.94.0
- School: "Add school work" is now a full-screen wizard opened with + in the top
  bar (with a calendar date picker). Parents/admins view children's work only —
  no tick-off or delete. On your own work, a top-right Edit button lets you rename
  or delete items (with a confirm), then Done to save; tick items off when not
  editing.

## 0.93.0
- School: new section. See each student's classes, open assignments/tests grouped
  by class (with due dates and late flags), and per-term progress. Add work, tick
  it off, or remove it. A child sees only their own; a parent sees their kids too
  (and can add/complete/remove for them).

## 0.92.0
- Family goal: the card on the Characters page is now tappable and opens a full
  co-op screen — see each kid's season-tier progress toward the goal, browse
  reward proposals and vote, and propose your own. Parents/admins can also
  select, grant, or remove rewards.

## 0.91.0
- Characters: the Hatch icon is now a plain egg with a single crack across the
  middle (removed the side lines), and a bit bigger.

## 0.90.0
- Characters: the Hatch egg icon is bigger and clearer (matches the reference),
  and the Hatch/Deepen/Gallery button icons are a bit larger overall.

## 0.89.0
- Characters: the Hatch button now uses a cracking-egg icon instead of a plus.

## 0.88.0
- Gallery: locked creatures now show an era silhouette for every era (Modern,
  Toon, Arcade, Dragon, Vintage, Wartime), with a generic fallback — no more
  plain "?".

## 0.87.0
- Characters: the top now reads "Season · <name>".
- Gallery: locked creatures show an era silhouette placeholder (Modern, Vintage,
  Dragon) instead of a plain "?"; other eras still use "?" until art is added.

## 0.86.0
- Characters: the top now shows the season name (not "Characters"); the XP bar
  under the creature is the segmented, colour-by-domain bar from the web; and the
  Hatch / Deepen / Gallery buttons are matching cards below the character card,
  shown only when they apply.
- Gallery: browse your collection by era — unlocked creatures show their art
  (tap to enlarge), the rest are mysteries with a rarity-coloured slot.
- Companion sprite endpoint made more robust (should fix the creature not showing).

## 0.85.0
- Characters: the companion sprite now renders (fixed image sizing), with a
  progress bar under it. Added the Family goal card at the top, and the hatch
  buttons under the creature when your egg is ready ("Hatch a new companion" and,
  if you already have one, "Deepen instead").

## 0.84.0
- Startup: the logo + καιρός screen now shows for about 2 seconds on launch.
- Characters: new section mirroring the web personal view — your companion
  (creature or incubating egg), level & XP, season tier, per-domain stat bars,
  streak and badges, and mastery titles. You only ever see your own character.

## 0.83.0
- Workouts: the Create / Edit button now shows "HIIT/CrossFit" beneath it.
- Create / Edit wizard: each step now sits on a white card (matching the review
  page), and opening the saved-workout dropdown (or tapping elsewhere) closes the
  keyboard so the list is visible.

## 0.82.0
- Create / Edit workout is now a step-by-step wizard (HIIT/CrossFit): 1) name
  (new or pick one to edit, rename or delete), 2) exercises (add, add/edit custom),
  3) instructions (with an "example: 100 thrusters…" hint), 4) a review of the
  whole workout before saving. The time cap was removed.

## 0.81.0
- Chores avatars: the coloured ring is thinner on small avatars.
- Workouts styling now matches the rest of the app: the main Workouts page, Browse
  workouts, the weight calculator, recent workouts, and the plan editor use the
  standard page background with white cards (buttons, graph, today, recent, and
  each plan day are on white cards).

## 0.80.0
- Reading: saving a page now clears the input (the cursor stops blinking and the
  keyboard closes) and the Save button turns lighter and reads "Saved ✓".
- Startup screen shows the logo with "καιρός" (Koine Greek for kairos) beneath it, set
  in Gentium Plus.
- Avatars: the side-menu profile now has the person's coloured ring, and profile
  icons on the chores page use a coloured ring instead of a solid colour fill —
  matching the web.

## 0.79.0
- Deleting a shopping item now asks to confirm and names the item, so it's
  harder to remove something by accident.
- You can only delete items you added — no delete button on other people's
  items. Parents and admins can delete anything. (Changing an item's store is
  still open to everyone.)

## 0.78.0
- Napkins, paper towels, bottled water, and protein now show real picture icons
  (bundled artwork) instead of a box.
- The add wizard's item list is sorted alphabetically. (Saved/shopping lists are
  alphabetical too, from the server.)

## 0.77.0
- Item icons: napkins and bottled water now show a small drawn glyph instead of
  a generic box (there's no emoji for either).
- The add wizard has a white background.
- Editing the list: an edit (pencil) icon at the top-right of the Groceries page
  turns on edit mode for the whole list. While editing, every line shows a
  change-store button (saved items) and a delete button; tap Done to finish.
  Items no longer delete on a stray tap outside edit mode.

## 0.76.0
- Add item is now a full-screen page: search the whole catalog (so you reuse an
  item instead of duplicating it) or add a new one, then pick a store. Matching
  is case-insensitive on the server, so "napkins" and "Napkins" are one item.
- Items no longer delete on a stray tap. Each item has an edit (pencil) icon;
  from there you can Change store (pop-up of stores) or Delete. Change store is
  hidden while an item is in an active shopping run.

## 0.75.1
- Groceries add matches the web: start typing and it suggests items you already
  have (with their icons) so you don't create duplicates, plus one-tap **Common**
  items when the box is empty. Picking a suggestion uses its remembered store;
  a brand-new item asks which store (pre-selecting its usual one, skipped when
  there's only one store).

## 0.75.0
- Groceries: the shared family shopping list is now in the app. See the saved
  list grouped by store, add an item (its store is remembered from the catalog,
  and the picker is skipped when there's only one store), tap **I'm going
  shopping** to pick a store and start a run, tick items off as you go with a
  live progress bar, and finish with **Done shopping** — bought items drop and
  the rest go back on the list. Shopper defaults to you. Store and catalog
  editing stays on the web.


## 0.74.2 — Reading: page-you're-on + white pop-ups
- Reading now works by the page you're on: enter what page (or chapter) you're up
  to and how far you've read is figured out from that for the Scholar stat —
  paging back and forth never double-counts, each page counts once. Mark finished
  completes the book to 100%.
- Bookmark was dropped as redundant with Shelve (both just moved a book to the
  shelf and kept your place). The Bookshelf is now To read / Read, and a shelved
  book shows where you left off so you resume there. Edit a book to correct the
  page you're on.
- All pop-ups are now a uniform white (removed the faint teal tint on dialogs).

## 0.74.1 — Reading (leisure book-tracker)
- New Reading section, strictly your own — no one sees anyone else's books.
- Add a book with an optional author and a size in pages and/or chapters (at
  least one; if you give both, progress tracks pages and the chapter count shows
  as a note). Log how much you read today; it feeds the Scholar stat.
- Set a book aside two ways: **bookmark** it to keep your place and resume later,
  or **shelve** it to save for later. The Bookshelf groups everything into To
  read / Bookmarked / Read; "Move to reading" (or "Reopen") brings a book back and
  resumes from your last logged page. A bookmarked book shows where you left off.
- Removing a book asks first.

## 0.73.0 — Money admin + home reminder
- Parent admins can now manage money from the phone: approve or unapprove filed
  transactions (single or all at once), edit or delete any transaction, and set
  a person's starting funds — everything the web Money admin does except the
  one-time CSV import. Approvals on the shared wall tablet still use the PIN.
- The home dashboard shows an amber reminder when money needs attention
  (transactions to approve and/or Bible-reading rewards ready); Review opens Money.
- Bible-reading reward approvals now use the amber/orange styling from the web.
- Adding a transaction is less cramped: Type, Category, For, and the frequent-
  payment picker now open a quick pop-up instead of an inline dropdown. A child
  only ever files for themselves (their name is filled in automatically).

## 0.72.0 — Money
- New Money section: per-person ledger with running balances, add a deposit or
  payment (lands pending, balance moves right away), and search your transactions.
  A single person collapses the people selector automatically.
- Parent admins can approve the month’s Bible-reading rewards right from the phone
  (per person or all at once), with a confirm pop-up — no PIN needed.

## 0.71.2 — Week view names on one line
- Week view event names now show on a single line with “…” when too long, instead of a
  word breaking into an ugly fragment on the next line (e.g. a lone “p”). Day and 3-day
  views keep two lines since their columns are wider.

## 0.71.1 — Week view event text
- Week view now uses a smaller chip font so more of an event name fits (many names
  now show in full over two lines), with a clean “…” when a name is still too long —
  instead of names breaking awkwardly across rows.

## 0.71.0 — Calendar colour picker rework
- Calendar colour settings: "Now line" is now "Current time", with a bright bold
  palette (blue, red, orange, black, white, etc.) so it's easy to see.
- More diverse colours in the custom-colour palette.
- The colour picker now confirms before applying: tap a colour to select it (it gets
  a teal ring), or pick Default (its colour shown beside it, teal-ringed when chosen),
  then Confirm or Cancel at the bottom.

## 0.70.1 — 3-day view scrolls as one grid
- Rebuilt the 3-day view as a single two-way-scrolling grid: one shared vertical
  scroll for all three days (they no longer drift apart on fast flings or at the top/
  bottom edges), with day-by-day horizontal snapping and frozen day headers + hour
  axis preserved.

## 0.69.5 — Attendee names left-align
- When an event has multiple people, their names now start from the same position
  (left-aligned) instead of right-justifying.

## 0.69.4 — App attendance marker true baseline alignment
- The app now baseline-aligns the attendance marker to the name (reporting the icon's
  baseline as its bottom edge), matching the web, so the marker's bottom sits on the
  name's baseline instead of floating high.

## 0.69.3 — Attendance marker sits on the name baseline
- Redrew the person marker to fill its box to the bottom so, bottom-aligned, its
  visible bottom lines up with the bottom of the name (matches web, which now
  baseline-aligns the marker).

## 0.69.2 — Attendance marker alignment
- The attendance marker now bottom-aligns with the name text (and the person glyph
  sits lower in its box so its visible bottom lines up), on the app and web.

## 0.69.1 — Attendance icon polish
- Moved the attendance marker to the left of each name.
- Redrew the person markers so the check / X / ? sits higher and centered, with a
  fuller body, instead of looking cut off at the bottom.

## 0.69.0 — Per-person attendance markers
- Sport events now show a per-person marker: a green check-person for attended, a red
  X-person for did not attend, and a light-grey question-person when it's not answered
  yet. One marker per person.
- Agenda events, home schedule cards, and event details now list one name per line,
  each with its own marker (family events just show "Family", no marker).

## 0.68.7 — Fix home not loading on first open
- The dashboard now loads on first open again. 0.68.6 removed the old load trigger
  and only reloaded on return; since the ViewModel doesn't load in init, first open
  spun forever. It now loads on first show and on every return.

## 0.68.6 — Reliable dashboard refresh on return
- The home dashboard now reloads when you come back to it (e.g. after logging a
  workout on another screen) via a navigation signal instead of the unreliable
  ON_RESUME hook, so today's counts stay current.

## 0.68.5 — Apply the state-collection fix app-wide
- Converted every screen from lifecycle-tied state collection to plain
  collectAsState(), so the "screen frozen after navigating away and back" bug can't
  appear anywhere else (chores, bible, calendar, all workout screens, etc.), not just
  the home dashboard. Recorded as a standing rule in DECISIONS.

## 0.68.4 — Home workout menu opens reliably after navigation
- Root cause fix: the dashboard state was collected tied to the screen lifecycle,
  which stopped updating after navigating away and back, so tapping the workout did
  nothing until an app restart. It now observes state while the screen is on-screen
  regardless of lifecycle, so the menu opens every time.

## 0.68.3 — Home workout menu is now a reliable dialog
- Replaced the home workout bottom sheet with the shared animated dialog. The sheet's
  window was leaking across navigation (popping up on the wrong screen and freezing);
  the dialog opens every time you tap and disposes cleanly when you navigate away.

## 0.68.2 — Home workout sheet reliably reopens
- Fixed the workout action sheet not sliding up after navigating around the app and
  returning to the dashboard: the sheet is now explicitly animated open and rebuilt
  fresh on every tap, so it no longer needs an app restart.

## 0.68.1 — Log wizard + did-not-attend everywhere
- "Log a different workout" is now "Log something else": a button that opens a
  step-by-step wizard — pick type, then (weights) muscle group → exercise → log,
  (running/rowing/etc.) straight to the value, (HIIT/CrossFit) pick workout → result.
  Much less scrolling than the old stacked dropdowns.
- "Did not attend" now shows on the home dashboard (schedule row + its popup) as well
  as the calendar detail and agenda.

## 0.67.0 — Did not attend, home workout menu, log tidy-up
- Answering "No" to "did you attend?" now shows "Did not attend" in red on the
  event detail, and a red marker beside the person on the agenda.
- Home workout menu: removed "Mark as done" (Log workout covers it); the menu now
  slides down before the log screen opens and reliably re-opens next time.
- The "today's max" hint now only shows for weight logging (not times/reps).

## 0.66.0 — Log a HIIT/CrossFit workout by name
- "Log a different workout" now shows "HIIT/CrossFit", and picking it lists your
  named workouts — the shared library (admin, incl. Hero) plus your own — instead
  of loose movements. Logging one records the result its type calls for.

## 0.65.0 — Share icon, calculator label, smoother pop-ups
- Proper Material “share” node icon on the browse cards.
- Pop-up dialogs now ease in smoothly (fade + gentle scale from centre) without the
  first-frame stutter.
- "Weight calc" is now "Calculator".

## 0.64.0 — Share to many, smoother pop-ups, HIIT logging option
- Share a workout with more than one person at once (checkboxes), with a cleaner
  share arrow icon on the browse cards.
- Pop-ups (share, confirmations) now ease in with a soft fade + scale, matching the
  roll-down menus — via a reusable animated dialog.
- "Log a different workout" now offers HIIT/CrossFit, and all its dropdowns use the
  same smooth roller as the Create / Edit form.

## 0.63.0 — Event delete rules, workout share icon, custom-exercise editing
- Admin/system events (holidays, profile birthdays, school work, family events,
  subscribed calendars) no longer show a delete icon in their detail.
- Browse workouts: your personal workouts now have a Share icon on the right
  (pick a person to send them a copy); deleting a workout is done from Create / Edit.
- Create / Edit: a new "Edit custom exercises" button lets you rename or delete your
  own custom exercises, each with a confirm.

## 0.62.1 — Create / Edit workouts
- The workout button is now "Create / Edit". The name box lists your saved workouts:
  pick one to load it (read-only), tap Edit (top right) to change any field including
  the name, with Delete + confirm; or type a new name to build one.
- Exercises come from the HIIT/CrossFit pool only; custom exercises you add go into
  that pool for you alone. "Movements" is now "Exercises", "Notes" is "Instructions".
- The time cap now shows only for the types that use one (minutes for AMRAP, seconds
  for timed stations) and is hidden otherwise.
- Type and exercise dropdowns now roll open and closed smoothly.

## 0.61.2 — Personal workouts
- New "Create workout" button (bottom actions are now one even grid) opens a form to
  build your own HIIT/CrossFit workout: name, type, optional cap, movements with
  reps/weight/distance, and notes.
- Add custom movements right from the form; they show only in your own menus.
- Browse workouts now has a "Personal" section under the shared library. Tap one of
  your workouts to Share it with someone (they get their own copy) or Delete it.

## 0.60.0 — Agenda date sliding
- The agenda now slides left/right to change day like the other views (snap, fling,
  Today, and the month dropdown all work), with neighbouring days pre-fetched.

## 0.59.1 — Home event popup + calendar settings bar
- Tap a schedule item on the home dashboard to open a centered detail popup with
  everyone on the event, time, location and notes.
- Event details (calendar) now show location and notes too.
- The calendar settings drawer now dims the status-bar strip like the main menu.

## 0.58.0 — Calendar detail + agenda + graph
- Event details (tapping an event) now list all the people on it, or "Family".
- Agenda: heading now reads "Sunday 6" and turns teal on today; the top shadow
  shows here too.
- Workout graph: darker gridlines, with solid lines at the quarter marks and
  dashed lines between so it's easier to read values off the scale.

## 0.57.0 — Shared event names + smarter times
- Shared events now list everyone they belong to — both names on the home
  dashboard card and in the agenda, "Family" for family events, and "Name +N"
  when too many to fit. The agenda now shows the name(s) on the right, like the
  home dashboard.
- New events default to the top of the current hour, and the end time now follows
  the event type's default length (set in admin) — or one hour if none — and
  updates automatically when you change the start time or the type.

## 0.56.0 — Plan wizard fixes
- Book picker now matches the manual-log page: grouped, colour-coded chips, with
  Whole Bible / Whole Old Testament / Whole New Testament buttons.
- Reordering is its own step and the drag no longer gets stuck (the list only
  commits the new order on drop; other books slide to show where it lands).
- Editing an existing plan now shows it first with Edit / Delete (delete confirms);
  Edit opens the wizard pre-filled with the plan's books.

## 0.55.0 — Reading plan builder
- Building a personal reading plan is now a 3-step wizard: pick books and
  drag to reorder them, choose the pace (chapters per day or finish by a date)
  with a start date, then preview the generated schedule before creating it.

## 0.54.0 — Personal reading order
- On the personal reading page the reading deck (days) is now at the top, matching
  the family layout — deck, then Edit / Log buttons, then "Your reading" and the rest.

## 0.53.1 — Personal reading redesign
- The personal reading view now mirrors the family view — a reading deck of your
  plan's daily passages plus coverage — once you've created a plan.
- Two buttons up top: Create/Edit plan and Log. The plan editor and the reading
  log each open on their own page (white card) instead of stacking inline, and the
  "Manual checklist" label is gone.

## 0.52.0 — Home sport prompt + Bible tabs
- The home dashboard now shows "Did you do X?" for a sport event that just
  finished, with Yes / No — same as the web.
- Bible reading tabs now read "Family" / "Personal", and the unselected tab is
  clearer.

## 0.51.2 — 3-day snap, properly fixed
- Rebuilt the 3-day slider on a snapping list instead of the custom fractional
  pager, which was mis-snapping back a day when a forward swipe got near the end.
  It now lands on the day you drag toward at any distance.

## 0.51.1 — 3-day snap fix
- Fixed the 3-day view snapping back a day when you advanced a swipe almost all the
  way: the incoming day wasn't composed yet so the pager mis-measured the snap.
  Neighbouring days are now pre-composed.

## 0.51.0 — 3-day finger-follow (Slice 3)
- 3-day view now slides day-by-day under your finger with the frozen time column,
  like day and week: three days show at once, a swipe snaps one day over, and
  neighbouring days are pre-fetched so they slide in populated. Today and the
  month dropdown work here too. That completes the calendar's finger-follow paging.

## 0.50.2 — Today + dropdown swipe fixes
- Today now works on the first tap in day/week/month (it was reading the old date
  before the reload landed, so the first tap did nothing and a second was needed).
- The month dropdown no longer jumps back and forth before settling when you swipe
  between months — same underlying cause.

## 0.50.1 — Week frozen column + Today slide fix
- Week view now has the frozen time column like the day view: the hour axis and
  day/date headers stay put while only the days slide.
- The column line now runs the full height (through the header) on day, 3-day and
  week — with the date beside it on day, blank on 3-day/week — at the same weight
  as the grid lines.
- Fixed Today: it now actually slides back in the right direction instead of
  jumping and sticking a day/week/month short (the settle handler was cancelling
  the animation).

## 0.50.0 — Today slide + week pager
- Tapping Today now slides quickly back to today in the correct direction instead
  of jumping (across day, week, month, and the month dropdown).
- Week view is now a finger-follow pager too (swipe left/right, snap, fling), with
  neighbouring weeks pre-fetched so they slide in populated. (3-day is next.)

## 0.49.1 — Month scroll directions
- The main month view now pages up and down (not left/right).
- The month dropdown in day/3-day/week views is now its own left/right
  finger-follow pager — grab and slide between months, snap to the nearest,
  with neighbours pre-fetched. Picking a day or landing on a new month navigates
  the view behind it.
- Pager caches now persist across navigation (cleared only after you add/edit/
  delete an event), so paging stays smooth.

## 0.49.0 — Top shadow + month pager
- Replaced the line under the expanded month dropdown with a soft shadow along the
  top of the day/3-day/week content — it stays whether the month is expanded or
  collapsed, like the month is floating above.
- Month view is now a finger-follow pager too (swipe left/right, snap, fling), with
  neighbouring months pre-fetched so they slide in populated. (Week and 3-day get
  the pager next.)

## 0.48.0 — Day view layout + time labels
- Hour labels now read "1 AM", "12 PM", etc. (all time-grid views).
- Day, 3-day and week now open near the current hour instead of pinned at 1 AM.
- Day view reworked: the time column and the day/date on the left are frozen and
  don't slide; only the day's hours slide left/right. The top row (date + all-day
  events) is frozen when scrolling up/down; all-day events, or "No events" when a
  day is empty, slide in with each day.

## 0.47.0 — Day view finger-follow (Slice 1)
- Day view now slides under your finger and snaps to the day you release on, with
  neighbouring days pre-fetched so they appear populated as they slide in. A quick
  flick flips to the next day. (Week, 3-day and month keep the previous swipe for
  now — they get the same treatment in the next slices.)

## 0.46.4 — Month grid lines
- Month grid lines are now single thin 1px lines (they were per-cell borders that
  doubled up at shared edges, making them look heavier than the other views).

## 0.46.3 — Month dropdown
- The month dropdown now rolls open and closed with a smooth animation instead of
  jumping, and stays open after you pick a day (collapse it with the month name).
- Swipe the open mini-month left/right to move between months.
- The main month view pages left/right again.

## 0.46.2 — Calendar swipe polish
- 3-day view now slides one day at a time, so you can shift by a day or two.
  Day and week still advance a full period.
- Month view now pages by swiping up and down instead of left and right.
- Lightened the month grid lines (other views' lines are unchanged).

## 0.46.1 — Calendar colour model
- Calendar colours are now a simple System / Custom choice. System follows the
  shared settings with nothing to change; Custom greys out everyone else's
  events automatically and lets you set your own colours for the now-line, event
  kinds, holidays, the family calendar, custom event types and subscriptions.

## 0.45.1 — Calendar polish
- Lightened the grey page background a touch.
- Darkened the calendar grid lines a little more, added a permanent divider
  between the day headers / all-day events and the hour grid, and gave the
  current-day column (and month cell) a subtle tint in every view.
- The calendar settings menu now matches the main sidebar: narrower, a rounded
  top corner, and aligned to the top edge.

## 0.45.0 — Calendar colour personalization
- New "Personalize colours" section in the calendar settings: choose how other
  people's events look (their colour / one grey / family scheme), and set your
  own colours for event kinds, holidays, event types and subscribed calendars.
  Each colour falls back to a sensible default until you change it.
- The workouts page is back to a white background.
- Calendar grid lines are a little darker.

## 0.44.1 — Calendar background
- The calendar page is back to a white background (the new grey page background
  made the grid hard to read).

## 0.44.0 — Event editor redesign + card fix
- Reworked the event editor: a borderless bold "Add Title", the all-day toggle
  and start/end date-time with no boxes, and full-width divider lines between the
  remaining sections.
- The type, calendar, time zone and repeat pickers now open a full screen with
  radio buttons instead of a dropdown; repeat shows a repeat icon and defaults to
  "Does not repeat".
- Cards are now properly white (the previous fix set the wrong theme role, so
  cards blended into the background); progress bars use a neutral grey track.

## 0.43.1 — Card colours
- Cards are now white (they were picking up a stray purple tint from unset theme
  colours) on a light-grey page background, so sections stand out clearly.
- Today's schedule shows a divider line between events.

## 0.43.0 — Home in cards
- Home sections (tasks by category, up for grabs, always open, today's schedule)
  are now grouped into cards so the different sections are easier to tell apart,
  matching the web.

## 0.42.0 — Event editor date/time
- Reworked the event editor: date and time sit at the top under the title as two
  lines (start, end) — tap the date to open the calendar, tap the time to open the
  clock. You can now give an event a different end date, too.

## 0.41.0 — Edit an event's type, calendar & people
- Editing an event now lets you change its type, its calendar (yours vs family,
  for parents/admins), and who it's shared with — not just the title and time.

## 0.40.0 — Share events with people
- When creating an event you can now share it with other people ("Share with"),
  so it shows on their calendars too.

## 0.39.0 — Event types & family calendar; birthdays
- New events can now be a chosen type — Appointment, Class, Work shift, Birthday,
  Other, or one of your family's custom types.
- Parents and admins can add an event to the family calendar (everyone else adds
  to their own). Birthdays: anyone can add one to their own calendar; adding to
  the family calendar, and editing any birthday, is parent/admin only.
- Picking Birthday sets it all-day and yearly automatically. Auto-generated
  profile birthdays stay read-only.

## 0.38.0 — Edit repeating events; smarter default time
- You can now edit a repeating event. On save it asks whether to change just
  this event or all of them (changing all is parent-only).
- New events now default to starting at the current time (rounded up) instead of
  9 AM, so you're not creating events in the past.

## 0.37.0 — Delete one occurrence of a repeat
- Deleting a repeating event now asks what to remove: just this event, this and
  the following ones, or all of them.

## 0.36.0 — Repeating events
- New events can now repeat: choose Daily, Weekly, Monthly or Yearly when
  creating one. (Editing a repeat, or changing just one occurrence, is coming
  next; deleting a repeat removes the whole series for now.)

## 0.35.0 — Full-screen event details & editing
- Tapping an event now opens a full-screen view (instead of a small pop-up): a
  colour bar and title, the date, the time with duration, whether it repeats, and
  the calendar it belongs to — with edit and delete in the top bar.
- You can now edit an event (title, all-day or times, date, location, timezone).
  Repeating events and birthdays aren't editable yet and stay parent-only to
  delete.

## 0.34.0 — Delete calendar events
- Tap any event (in any view) to see its details — time, location, who it's for,
  and whether it repeats — and delete it from there.
- You can remove your own and family events; repeating events and birthdays stay
  parent-only, and subscribed-feed events point you to unsubscribing instead.

## 0.33.0 — Today's schedule on Home
- Home now shows "Today's schedule" at the bottom: the whole household's events
  for the day (all-day first, then by time), each with its colour, time/location,
  and who it belongs to.

## 0.32.0 — Add calendar events
- The "+" button is back on the calendar: create an event with a title, all-day
  or a start/end time, a date, and an optional location.
- When your phone and home are in different timezones, you can choose which
  timezone the event's time is in (default home) — the calendar then shows it at
  the right local time wherever you are.
- Recurring events, inviting other people, event types, and editing existing
  events are coming next.

## 0.31.0 — Calendar month grid & settings drawer
- Month view: every day is now the same size and the grid fills the screen,
  instead of days growing with how many events they have.
- Calendar settings now slide in from the right. The view chooser sits at the
  top with icons and the current view highlighted (tap to switch); a new
  "Default view" option lets you pick which view the calendar opens to — a
  specific one or your last-used ("Last view").
- All event filters (family, school work, people, subscriptions) use checkboxes
  now, grouped into "My calendars" and "Other calendars".

## 0.30.0 — Calendar layout rework
- Cleaner calendar top bar: the redundant "Calendar" title and the prev/next
  arrows are gone. The heading now shows a single month name you can tap to drop
  down a month grid (with event dots) and jump to any day; tap again to close.
- A "today" button showing today's date returns you to today, and a new settings
  button holds the view chooser (Agenda / Day / 3-day / Week / Month) and all the
  filters (people, family, school work, subscriptions).
- Month view now shows event chips (like the web), always titled with one month
  and greying the neighbouring months' days.

## 0.29.1 — Calendar travel timezones
- When your phone is in a different timezone from home, timed events now shift to
  show at the wall-clock time they actually happen where you are (e.g. a 9 AM
  home event shows at 10 AM one zone east), and the "now" line follows your phone.
- At home (phone and home in the same zone) nothing changes. All-day events never
  shift.

## 0.29.0 — Calendar filters & timezone fix
- New Filters panel on the calendar: choose which people to show, toggle Family
  events and School work, and pick which subscribed calendars appear. Your
  choices are saved and shared with the web.
- The "now" line is now placed in the household's timezone, so it stays correct
  even if your phone is set to a different zone.

## 0.28.0 — Calendar time-grids
- Calendar now has Day, 3-day, and Week time-grid views with an hour axis, an
  all-day strip, a live "now" line, and side-by-side layout for overlapping
  events. Pick a view from the menu at the top.
- Swipe left/right anywhere on the calendar to move to the next/previous day,
  week, or month (the arrows still work too).

## 0.27.0 — Calendar (Month & Agenda)
- New Calendar section (read-only for now): an Agenda view of a day's events with
  prev/today/next, and a Month grid with coloured event dots — tap a day to open
  its agenda. Events use the colours and filters you've set on the web.
- Time-grid views (week / 3-day), adding and editing events, the options drawer,
  and colour controls are coming in later updates.

## 0.26.2 — Up for grabs & always-open on Home
- Home now shows household chores anyone can pick up: "Up for grabs" (shared
  chores and any chore released to the household) with a Take it button, and
  "Always open" chores with a Done button. Taking or tapping one logs it to you.
- Always-open chores on a cooldown show "Not back yet" until they're available.

## 0.26.1 — Personal reading on Home
- When you have a personal reading plan, the day's reading now shows on Home in
  the Bible reading section as "Personal bible reading", ready to tick off — no
  separate screen needed. Create or change your plan on the Bible reading page.

## 0.26.0 — Chores
- New Chores section: a read-only household overview mirroring the web chores
  page. Shows a pause banner when chores are paused, "This week" (each person's
  due/done/open/missed), the "Weekly rotation" day-by-day (past-due in red, a
  green check when done), "Always open" tap-counts, and "Shared chores" status
  with a completion tally.
- Parents and admins see the whole household (themselves and every child);
  everyone else sees just their own summary and rotation. Always-open and shared
  chores are shown to everyone.
- Completing chores stays on Home, as before — this page is the overview.

## 0.25.1 — Bible reading fixes
- Fixed: the "Personal Progress" tab now works — tapping it switches to your own
  coverage, plan and checklist (it was inert before).
- Renamed "Mark what you've read" to "Manual checklist", and removed the small
  explanatory notes under it and "Your plan".

## 0.25.0 — Bible reading
- New Bible reading section, to full parity with the web personal view. Two tabs:
  - Family Progress: the reading deck (swipe or use the arrows through the days,
    with a "Back to today"), how many days are left in the family plan, and the
    Old/New Testament and by-group coverage bars.
  - Personal Progress: your own coverage; "Your plan" — create a personal
    reading plan (pick books, a start date and chapters/day, with a live chapter
    and day estimate) or work through the one you have, ticking each day off
    (deleting a plan is confirmed and keeps your read chapters); and "Mark what
    you've read" — tick whole books or open a book to mark individual chapters,
    plus quick "Mark Old/New Testament read" and "Clear hand-marked".
- Backed by the new GET /api/v1/reading aggregate and the reading write endpoints
  (plan, plan/delete, mark, books, books/bulk); /me now carries your colour.

## 0.24.1 — Avatar position
- Avatars now honour the web's position/zoom adjustment (the API now sends
  avatarPosition and the app applies the same transform), so a centred photo
  matches the web instead of showing the raw crop.

## 0.24.0 — Avatar photos
- Uploaded avatar photos now display in the app (nav footer), loaded over the new
  device-authed /api/v1/avatars endpoint via a token-aware image loader. Falls
  back to the emoji/initials when there's no photo or it can't load.

## 0.23.0 — Edit plan step 3: Rotation + add-refresh fix
- Fixed: adding a workout to a day now refreshes the card immediately (a
  status-only response was failing to parse and skipping the reload).
- New Rotation builder (Edit plan -> Rotation): start/stop a rotation, toggle
  fixed rest weekdays, see the next 10 days, and build the cycle (add named or
  rest slots, reorder, remove). Backed by GET /workouts/rotation + start/stop/
  rest-days/add-slot/remove-slot/move-slot. Slot editing + anchor date come next.

## 0.22.0 — Edit plan (step 2: add workouts)
- The Edit plan "Add workout" button now opens a picker: choose a category (or
  Rest), pick a muscle group + exercises for weights (each with a "log a metric"
  toggle and metric), a named workout for HIIT, or a metric-only day. Backed by
  GET /workouts/plan/options and add-pool / add-hiit routes.
- Next: rotation mode (step 3).

## 0.21.0 — Edit plan (step 1: view, remove, rest, copy)
- New Workout plan editor (from the Edit plan button): the 7-day plan with each
  workout's name + detail, remove a workout, mark a day as rest, and copy another
  day's plan onto a day. Backed by GET /workouts/plan and rest/copy/remove routes.
- Next: adding workouts (the category -> exercise picker), then rotation mode.

## 0.20.0 — Workouts: This week + Browse
- The Workouts page now shows "This week" (your non-weights sessions grouped, e.g.
  "Punishers @ Gun Slingers 1\u00d7") between the graph and Today.
- New Browse workouts page (from the Browse button): filter Workouts / Hero WODs,
  each with its type, a Personal tag, and its details. Backed by new
  GET /workouts/week and GET /workouts/browse.

## 0.19.3 — Weight calculator: exact bar corners
- Matched the re-uploaded SVGs precisely: sleeves round only their outer corners
  (r2), collars r1.5, shaft r1, EZ shaft round joins with flat (butt) ends. The
  earlier version over-rounded every corner.

## 0.19.2 — Weight calculator: rounded bar corners
- Added the rounded corners from the Design art (the SVG export had flattened
  them to square). Correct proportions kept; every exposed corner/angle rounded.

## 0.19.1 — Weight calculator: use the real bar art
- Rebuilt the barbell to match the uploaded SVGs exactly: shaft half-width 49,
  11-wide collars, 89-long sleeves, flat corners, no end caps, the light outline,
  and the exact EZ-curl W-path. (The previous version used a wrong reconstruction.)

## 0.19.0 — Weight calculator
- New Weight calculator (from the Workouts page): tap plates to load a pair per
  side, pick the bar (45 / 15 / EZ), see the running total, and a scaled barbell
  drawing that mirrors the web — including the corrected EZ-curl bar. Tap a
  loaded chip to remove a pair; Clear resets. Bumpers / Steel / Fractional plates
  with real colours and sizes.

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
