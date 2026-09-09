# Releasing Kairos (Android)

The app updates itself from **GitHub Releases**. Publishing a Release is what
makes an update go live to the household; the in-app updater checks GitHub,
notices the newer `versionCode`, and offers a one-tap install.

Publishing is deliberate — it happens only on a version **tag** or a manual
run, never on an ordinary push. Your normal `main` push build (`android.yml`)
is unchanged and is still just for your own testing.

## Cut a release

1. **Bump the version** in `app/build.gradle.kts`:
   - `versionCode` — the integer Android compares; **must increase every release**.
   - `versionName` — the human version (e.g. `0.126.0`, or `1.0.0` at launch).
   Also bump `CLIENT_BUILD` in `SessionRepository.kt`.
2. **Update `CHANGELOG.md`** — add a `## <versionName>` section at the top. Its
   bullets become the release notes the household sees in the update prompt.
3. **Commit.**
4. **Publish**, either:
   - `git tag v0.126.0 && git push origin v0.126.0`, or
   - Actions → **Release** → *Run workflow*.

The **Release** workflow builds the signed APK and publishes a GitHub Release
tagged `v<versionName>`, with two assets:

- `kairos-<versionName>.apk` — the signed app.
- `latest.json` — `{ versionCode, versionName, notes }`, which the app reads to
  decide whether to offer an update.

The APK is signed with the same key as always, so updates install **in place**
(no reinstall, no data loss).

## Notes

- Re-running for an existing tag refreshes the assets in place (`--clobber`).
- This is independent of how signing is wired on push builds. If you ever move
  signing off the push build (so the key is only used at release time), this
  workflow is its natural home — but that's an optional, separate decision.
- Keep the signing keystore backed up **off GitHub**. If it's ever lost, no
  future build can update an installed copy in place.
