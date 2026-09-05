# Changelog

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
