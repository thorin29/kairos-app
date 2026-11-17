# Changelog

## [0.6.0]

Best with server 0.30; on an older server the new parts show what they can.

- Five tabs: Today, Month, Cards, Insights, More. "What can I pay?" moved under More (Today's
  balance card still opens it).
- Cards tab: the card stack, then each card with what to pay, the due date and its statement or
  payoff plan, and the reminder setting.
- Card payment reminders: around 8 AM, on the due date and optionally 1, 3 or 5 days before.
  A card already paid for the cycle is skipped. Tapping one opens the Cards tab. Off until turned
  on in the Cards tab or under More.
- Insights tab: this month's pace against a usual month, the suggestions (tap one for the
  details and charges behind it), and where the money went.
- Today is lighter: one line for the cards and the top suggestion, each opening its tab.

## [0.5.1]

- Cards can be thrown off either side of the screen. A swipe or quick flick sends the front card
  off the way it was moving, and it eases in behind the others from that side, instead of
  swinging back across to the right.

## [0.5.0]

Needs server 0.28 or later; on an older server these simply don't show.

- Card stack on Today: an All cards summary in front (what to pay this cycle, next due date, and
  the balance split into on statements and new since), then each card in the color picked on the
  web. Swipe the front card either way, or tap it, and it slides to the back.
- Review: a "to review" row on Today opens the charges that look like bill payments (Review and
  record, then Confirm with the bank's amount and date, or Not this) and the payments recorded on
  their own (Undo).
- Everyday spending on Today: this month against your average, and the top categories.
- Payments recorded on their own from the bank read "Paid … · auto". "Free after month" is now
  "Second half remaining", as on the web.

## [0.4.0]

- Bill reminders (More → Bill reminders): a notification around 8 AM on the day a bill is due, or
  one or two days before. Off unless turned on. Bills already recorded, or waiting to send, are
  skipped, and it works from the saved copy when offline.

## [0.3.3]

- Checks for an update every time the app opens fresh, as the Kairos app does, so a new release
  shows its badge right away.

## [0.3.2]

- The QR scanner is now inside the app (CameraX and ZXing, both open source), so it works without
  Google Play services. It asks for the camera only when you tap Scan QR code.

## [0.3.1]

- Update badge, as in the Kairos app: a blue dot on the More tab, an "Update available" row and a
  "New" pill on App version, and a notification once per new version that opens the Update screen.
- New Update screen: installed version, what's new, download & install, check for updates.
- Checks at launch and in the background about every two hours; the last result is kept if a check
  fails.

## [0.3.0]

- New design: Today, Month, Plan and More tabs, Figtree type and Phosphor icons.
- Recording is now pick, type on a number pad, Review, Confirm. The date can be picked from a calendar.
- Plan tab: the bills balance walked forward with a slider for "if I pay today".
- Appearance: light, dark or follow the phone, and six accent colors (Cobalt by default).
- New app icon.

## [0.2.0]

- What's kept on the phone (unsent changes, refused ones, the last copy of each month) is now in a
  Room database. Anything waiting from 0.1.x is moved across on first launch.
- Check for updates no longer shows an error when no release has been published.

## [0.1.1]

- Scan the web's QR code from inside the app (Scan QR code on the first screen). Phone cameras
  don't open `budget://` links.

## [0.1.0]

- First version: add this phone with a code or QR, see this month, record payments with a confirm
  step, update the bills balance, and see what's safe to send.
- Works offline: changes wait on the phone and send when it's back.
- Checks for updates and installs them from inside the app.
