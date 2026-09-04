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
