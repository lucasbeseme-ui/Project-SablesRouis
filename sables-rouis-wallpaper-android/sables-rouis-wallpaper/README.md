# Sables Rouis live wallpaper, version 0.1

An animated lofi view of Sables Rouis, Île d'Yeu, as an Android live wallpaper. Surfers and kitesurfers appear when the conditions match the default Surf and Kite ranges. Conditions are simulated in this version (no internet, no map yet).

> **Please read before using.** This code was written by an AI assistant and has **not** been run on a phone or built yet. Treat it as a draft: expect one to three rounds of fixing after the first build. It asks for no permissions, collects no data and makes no network calls. It is signed with a throwaway debug key, which is fine for a personal test and not for sharing.

## What you need
- A computer (to upload the files) and your Pixel 10.
- A free GitHub account.
- About 30 minutes for the first build.

## Step by step

**1. Create a GitHub account** at github.com and confirm your email.

**2. Create a repository.** Click **+** (top right), then **New repository**.
- Name: `sables-rouis-wallpaper`
- Public is simplest and free with unlimited builds. Private also works within the free monthly allowance (2,000 build minutes), and each build uses only a few.
- Do **not** tick "Add a README".
- Click **Create repository**.

**3. Upload the project.**
- Unzip `sables-rouis-wallpaper-android.zip` on your computer.
- On the empty repository page, click **uploading an existing file**.
- Open the unzipped folder and drag its **contents** (the `app` folder, `gradle` folder, `gradlew`, `settings.gradle.kts` and the other files) into the browser. Drag the contents, not the outer folder.
- Wait until all files are listed, then click **Commit changes**.

**4. Check the build recipe is there.** In the repository, look for `.github/workflows/build.yml`. Some computers hide folders that start with a dot, so it may be missing. If so:
- Click **Add file**, then **Create new file**.
- In the name box type `.github/workflows/build.yml` (typing the slashes makes the folders).
- Open `build.yml` from the zip in a text editor, copy everything and paste it in.
- Click **Commit changes**.

**5. Watch the build.** Click the **Actions** tab. A run called "Build Android APK" starts by itself.
- Yellow dot = running (5 to 10 minutes the first time).
- Green tick = done.
- Red cross = it failed. Click the run, open the failed step, copy the last 40 lines of the log and send them to me. This is normal for a first build.

**6. Download the app on your phone.**
- On your Pixel, open your repository in Chrome and tap **Releases** (right side, or under the code tab).
- Open the newest build and tap the file ending in `.apk`.
- Chrome may ask to allow installing from this source: allow it for Chrome.
- Android may show a Play Protect warning for an unknown app. Choose **More details**, then **Install anyway**. It is your own build.

**7. Set the wallpaper.** Open the **Sables Rouis** app.
- Pick a scenario ("Both on, golden hour" is a good start).
- Pick how the light behaves: the scenario's time of day, your real clock, or a fast day cycle for a demo.
- Tap **Set as live wallpaper**, then **Set wallpaper**, and choose home screen, lock screen or both.

## Updating to a new build
Each cloud build is signed with a new throwaway key, so Android will refuse to install it over the old one. **Uninstall the old app first** (Settings, Apps, Sables Rouis, Uninstall), then install the new build and set the wallpaper again.

## What is in version 0.1
- The painted beach scene, tide, swell, wind, weather, light and animated surfers and kitesurfers, ported from the web mockup.
- Six scenarios, three light modes.
- The same default Surf and Kite ranges as the mockup.

## Not yet in this version
Real wind, swell and tide data, the map to pick a spot, the beach compass, editable ranges, and the satellite-to-lofi image step. These come next.

## Where to change things (for later)
| What | File | Name |
|---|---|---|
| Frame rate (battery vs smoothness) | `SablesWallpaperService.kt` | `frameMs = 40L` (higher = fewer frames) |
| Default ranges | `Core.kt` | `SURF_PROFILE`, `KITE_PROFILE` |
| Direction the beach faces | `Core.kt` | `BEACH_FACING` (270 = west, assumed, to confirm on site) |
| Scenarios | `Core.kt` | `SCENARIOS` |
| Look of the sea and animation | `Scene.kt` | |
| Painted land, sand and rocks | `Background.kt` | |

## If something goes wrong
- **Build fails:** send me the last 40 lines of the log.
- **App installs but the wallpaper is black or crashes:** tell me your Android version and what you see. I will add a simpler drawing mode.
- **It stutters or drains the battery:** raise `frameMs` to 60, and tell me, so I can lighten the wave drawing.
