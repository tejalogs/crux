# Crux: F-Droid release plan and pending steps

Updated 8 Nov 2026. App ID `org.crux.social`, version 1.0.1 (versionCode 2), AGPL-3.0-only.

**Who does what**
- **Teja:** anything involving accounts, passwords, publishing or pushing. That covers GitHub, GitLab, signing keys and the merge request itself.
- **Claude:** code, metadata, builds and checks.

---

## 1. Where things stand

### Ready
- [x] FOSS-only build: no Firebase, no Google Play Services, no SNAPSHOT dependencies.
- [x] `dependenciesInfo` is off, and no timestamps or git hashes are written into the APK.
- [x] The only non-free Gradle plugin (the foojay toolchain resolver) sits between `// START/END Non-FOSS component` markers in `settings.gradle.kts`, and the recipe's prebuild strips it.
- [x] Unicode data is vendored in `third_party/unicode-ucd-16.0.0`, so the build downloads nothing beyond Maven.
- [x] JDK 17 is enough. The toolchain is set to 17, and the recipe deletes `gradle/gradle-daemon-jvm.properties`.
- [x] Version lives in `fdroid.properties` (`versionName=1.0.1`, `versionCode=2`).
- [x] Store text in `fastlane/metadata/android/en-US/`: title, short and full description, `changelogs/1.txt`, `images/icon.png`.
- [x] Draft fdroiddata recipe at `docs/fdroid/org.crux.social.yml`, with the NonFreeNet anti-feature declared.
- [x] Privacy policy at `docs/PRIVACY.md`. About screen with an open-source licenses screen.
- [x] Backups are off (`allowBackup=false`, and the data extraction rules exclude everything).
- [x] Release APK builds (about 19 MB). Unit tests pass: app, shared, subscription, agent, compose-ui.

### Status
- [x] Unshallowed and pushed repository to **https://github.com/tejalogs/crux** (AGPL-3.0)
- [x] Tagged and released **v1.0.0** with attached release APK
- [x] Verified feed scrolling performance on phone (1.39% jank, 120 FPS)
- [x] Verified Onboarding and Privacy counter screens on device
- [x] 6 high-res phone screenshots captured in `fastlane/metadata/android/en-US/images/phoneScreenshots/`
- [x] All unit test suites passed
- [x] Forked `fdroid/fdroiddata` to `tejalogs/fdroiddata`
- [x] Created branch `org.crux.social` with `metadata/org.crux.social.yml`
- [x] Opened Merge Request: **https://gitlab.com/fdroid/fdroiddata/-/merge_requests/56349**

---

## 2. Upload steps, in order

### Step 1: Finish and commit (Claude, after Teja says yes)
1. Rebuild, install on the phone, and check onboarding, the Privacy screen, X, Reddit and the reader.
2. Run the full gate:
   ```bash
   ./gradlew :app:assembleRelease :app:testDebugUnitTest :shared:jvmTest :feature:subscription:jvmTest :feature:agent:jvmTest :compose-ui:jvmTest
   ```
3. Commit on `crux/live-web-and-reader` in logical chunks.
4. Run a secret scan before pushing. Keep `signing.properties`, `local.properties` and keystores out of git.

### Step 2: Capture store screenshots (Claude, phone connected)
- Take 4 to 6 PNGs at 1080×2340, saved as `fastlane/metadata/android/en-US/images/phoneScreenshots/1.png`, `2.png`, and so on.
- Suggested set:
  1. Home dock with the X tab
  2. Reddit tab
  3. Feeds list
  4. Paged reader
  5. Site protection and tweaks
  6. Onboarding welcome
- Use a clean test account or signed-out pages. No personal timelines, names or DMs: these images go public.
- Optional: `images/featureGraphic.png` at 1024×500.

### Step 3: Create the public GitHub repo (Teja)
1. On GitHub, create **tejalogs/crux** as public and empty: no README and no license, since the repo already has both.
2. Add it as a new remote and push. These commands are for Teja to run:
   ```bash
   cd ~/Documents/Flare
   git remote add crux https://github.com/tejalogs/crux.git
   git push -u crux crux/live-web-and-reader:main
   ```
3. Optional but recommended: rename upstream so nothing ever pushes there by accident:
   ```bash
   git remote rename origin upstream
   git remote set-url --push upstream DISABLED
   ```
4. Check that the repo page shows the AGPL-3.0 license and that the README says Crux is a fork of Flare.

### Step 4: Tag the release (Teja)
```bash
git tag -a v1.0.0 -m "Crux 1.0.0"
git push crux v1.0.0
```
Optional: create a GitHub Release for `v1.0.0` and attach an APK signed with your own key (see step 7). F-Droid doesn't need it.

### Step 5: Test the recipe locally (Claude can run this; it uses Docker or fdroidserver)
1. Clone fdroiddata: `git clone https://gitlab.com/fdroid/fdroiddata.git`.
2. Copy `docs/fdroid/org.crux.social.yml` to `fdroiddata/metadata/org.crux.social.yml`. Delete the "Notes for whoever submits this" comment block.
3. Run each of these and fix any errors:
   ```bash
   fdroid readmeta
   fdroid rewritemeta org.crux.social
   fdroid lint org.crux.social
   fdroid build -v -l org.crux.social
   fdroid checkupdates --allow-dirty org.crux.social
   ```
4. Things that commonly go wrong:
   - **JDK on the build server.** If the default JDK is older than 17, add a `sudo:` line to the recipe that installs `openjdk-17-jdk-headless`.
   - **The `UpdateCheckData` pattern** for `fdroid.properties`.
   - **The prebuild `sed`** that removes the Non-FOSS block.
   - **The scanner.** It flags any leftover binary jars or blobs. `gradle-wrapper.jar` is fine.

### Step 6: Open the merge request (Teja)
1. Sign in to GitLab and fork **gitlab.com/fdroid/fdroiddata**.
2. Push a branch `org.crux.social` that contains only `metadata/org.crux.social.yml`.
3. Open the merge request with the "App inclusion" template. Fill in:
   - **Summary:** "Crux is a privacy-focused, web-only social reader for Instagram, X, LinkedIn and Reddit in isolated WebView profiles, plus an RSS reader. Fork of Flare (AGPL-3.0)."
   - **Anti-features:** NonFreeNet, already declared.
   - **Confirmations:** the app has no trackers, no proprietary libraries and no ads.
4. Reply to reviewer comments. Claude can draft the replies and make code changes. Expect a few rounds, typically 1 to 4 weeks.
5. After it's merged, the app shows up in the F-Droid client within about 1 to 3 days, after the next index build.

### Step 7: Signing key and reproducible builds (Teja, optional)
- F-Droid signs its own build by default, so you don't need a key for step 6.
- If you want GitHub release APKs and F-Droid builds to update each other, set up reproducible builds:
  1. Create your own keystore. Keep it out of git and back it up.
  2. Sign your release with it.
  3. Add `Binaries:` and `AllowedAPKSigningKeys:` to the recipe.
- This is a later improvement, not needed for 1.0.0.

### Step 8: Updates after 1.0.0
1. Bump `fdroid.properties` (`versionName`, `versionCode`).
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Tag `v<versionName>` and push the tag.
4. `AutoUpdateMode: Version` with `UpdateCheckMode: Tags` picks the new tag up automatically. No new merge request is needed.

---

## 3. Other pending work, not blocking F-Droid

| Item | Owner | Notes |
|---|---|---|
| Verify the new onboarding on the phone | Claude | Starter feeds now preselect 2 per category instead of all 32, replaying the tour no longer re-adds deleted feeds, and the Calm step has a "Protection is already on" note. |
| Privacy counter labels | Claude | Fixed so the two labels no longer run together. Check on the phone. |
| Reworded strings in other languages | Later | Some changed English strings still carry their old translations. F-Droid doesn't block on this. |
| Image and video share targets | Later | The compose sheet accepts text only. |
| Remove the unused `androidx.browser` dependency and `HardenedBrowserScreen` | Claude | Small cleanup. |
| Instagram and LinkedIn "shadow account" code | Later | Used only for sign-in detection. Simplify when convenient. |
| X tweak hides "Subscribe" on creator profiles too | Accepted | Side effect of the Premium upsell filter. |
| Play Store | Not planned | Play would likely reject it as a WebView wrapper of third-party sites. F-Droid is the target. |

## 4. Rules that still apply
- Never push to `origin` (DimensionDev/Flare).
- Commit and push only after Teja says yes.
- Claude never enters passwords or signs in to anything: Teja handles GitHub, GitLab and every social login.
- On logged-in sites, Instagram stays on Standard (hide-only) protection. Don't add request blocking or API patching there.
