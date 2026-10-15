# Floor Planner for Android

A simple 2D floor-plan app for Android, based on the [FloorPlanner 2D web app](https://github.com/nos486/floor-planner).
Draw walls with your finger, add doors and windows, and see the area of every room as you draw.

## Features

- **Projects**: a start screen lists every saved plan with a preview. Create, rename, duplicate or delete them,
  and start from an empty plan or a ready-made studio, one-, two- or three-bedroom apartment.
- **Walls**: drag on the grid to draw a wall. Ends snap to other walls, to the grid and to 0°/45°/90° angles, with a live length tag.
- **Doors and windows**: tap (or drag along) a wall to place one. Doors show their swing arc; flip the swing side or the hinge from the panel.
- **Furniture**: sofas, beds, tables, kitchen and bathroom fixtures from the Add sheet. Drag them around,
  turn them with the rotation handle, resize them; they snap their back against nearby walls.
- **Select & edit**: tap anything to select it, then drag it.
  - Drag a wall to move it; walls joined to it follow.
  - Drag the round handles at the ends of a selected wall to reshape the plan.
  - Slide doors and windows along their wall.
  - Set an exact wall length, thickness, opening width, or split a wall in half from the bottom panel.
  - "Select several" picks things by tapping or with a box, then moves, duplicates, rotates, mirrors or deletes them together.
- **Rooms**: closed areas are detected automatically, with their area and a total for the whole plan.
  Tap a room to rename it and give it a colour and a floor (parquet, tiles, stone, carpet).
- **Dimensions and notes**: overall width and height around the plan, a Measure tool for your own dimension lines,
  and a Text tool for notes with optional arrows.
- **Structure**: curved walls (drag the handle in the middle of a wall), low walls, columns, stairs and a fireplace;
  double and sliding doors.
- **Floors**: add, rename and switch floors; the floor below is drawn faintly. The PDF has a page per floor.
- **Electrical and plumbing**: sockets, switches, lights, panels, AC units, water points, drains, gas valves,
  water heaters, boilers and radiators, each on its own layer. Layers can be hidden on screen and in exports.
- **Background photo**: put a photo of a paper plan under the drawing, set its scale with a known length, and trace it.
- **3D view**: the plan extruded with openings, furniture and stairs, turned and zoomed with your fingers.
- **Materials**: floor covering, paint and skirting quantities with optional unit prices.
- **Plan check**: doors hitting furniture, overlapping furniture, furniture in walls or in front of windows,
  narrow rooms, and rooms without a door or a window.
- **Sunlight**: sun position from month, hour, latitude and the north arrow; light patches through the windows and
  hours of direct sun per room.
- **Home buyer**: usable area to the wall faces, gross area with the walls, price per m², and a comparison of all
  saved homes from the start screen.
- **Versions**: save named versions and compare one with the plan (walls removed and added, areas then and now).
- **Actual size**: zoom so the plan shows at 1:1 on the screen.
- **Sample plan**: a furnished two-storey home from the start screen.
- **Units**: metres or feet and inches.
- **Navigation**: two fingers to pan and pinch-zoom (one finger also pans in Select mode when not on a wall), plus "fit to screen".
- **Undo / redo**, and every project is saved automatically.
- **Files**: open and save projects as JSON (same format as the web app, so files move between the two; the extra
  furniture, labels and dimensions are optional fields), export the plan as PNG, PDF (A4 with a room schedule and
  scale bar) SVG or DXF (AutoCAD R12, one layer per kind of object), or share the image or PDF straight to another app.
- Light and dark theme, English and Persian.

## Build

Requires JDK 17 and the Android SDK (API 36).

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # geometry, room detection and file format tests
```

Every push and pull request is built by GitHub Actions, and the debug APK is attached to the run as the `floor-planner-debug-apk` artifact.

## Code layout

| Path | What it holds |
| --- | --- |
| `app/src/main/java/.../model` | Project file model (walls, doors, windows, rooms) |
| `app/src/main/java/.../core` | Pure Kotlin logic: geometry, room detection, snapping, edit operations, JSON/SVG |
| `app/src/main/java/.../ui` | Jetpack Compose UI: canvas drawing, gestures, inspector panel, view model |

## Releases

Every push to `main` runs the `Release` workflow: it builds a signed release APK and AAB (Android App Bundle)
and publishes them on the [Releases](../../releases) page as `v0.1.<build number>`. Signing uses these repository secrets
(Settings > Secrets and variables > Actions): `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. The keystore is never stored in the repository.

## Google Play

- Store listing texts, icon and feature graphic are in `fastlane/metadata/android/`.
- Privacy policy: [PRIVACY.md](PRIVACY.md).
- Upload the first `.aab` from a GitHub Release by hand in Play Console. After that, add a
  `PLAY_SERVICE_ACCOUNT_JSON` secret (a Google Cloud service account with release access in Play Console) and
  every release is uploaded to the `internal` track as a draft. Repository variables `PLAY_TRACK`
  (`internal`, `alpha`, `beta`, `production`) and `PLAY_RELEASE_STATUS` (`draft`, `completed`) change that.
