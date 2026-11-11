# سكنر (Skenr) — v1.0.0

Native Android document scanner, built from scratch in Kotlin + Jetpack Compose.
Fully offline, no account, no cloud, no ads, no subscription. Own code only —
no CamScanner code, assets, logo or trademark.

- Package: `com.talbmax.skner`, versionCode 1, versionName 1.0.0
- minSdk 24, targetSdk 34, compileSdk 34
- CameraX, Room, PdfDocument, on-device OCR (Tesseract 5.5.1, ara+eng)

## Screens
1. Home — search, folders, recent docs, big scan button. Copyright footer ONLY here:
   `© جميع الحقوق محفوظة — طالب المولى {year}` (year is dynamic, from the device calendar).
2. Camera — modes مستند/هوية/كتاب/صورة, flash, grid, auto-capture (stability heuristic).
3. Crop — 4 draggable corners, perspective warp (pure-Kotlin homography, no OpenCV in v1),
   filters أصلي/أبيض وأسود/رمادي/تحسين, before/after.
4. Document — pages, reorder/delete, rename, tags/notes, OCR, PDF export, share,
   signature, watermark, PIN lock, print, merge/split.
5. Collage (تلصيق الصور) — combine 2+ images into one page (2×2 / 1×2 / 1×3 / free),
   export PDF/JPG. Free (paid in the original).

## Build

### GitHub Actions (preferred)
The workflow `.github/workflows/android.yml` runs on push to `main` and on
`workflow_dispatch`. It downloads the Tesseract traineddata (ara+eng, "fast"
models from github.com/tesseract-ocr/tessdata_fast) at build time, builds the
debug APK and uploads it as artifact `skner-apk`.

> Repo note (2026-11-11): the fine-grained PAT cannot create repositories
> (POST /user/repos returns 403 "Resource not accessible by personal access
> token"). Create the repo `talbmax10/skner` (private) manually on github.com
> or via the live browser signed in as talbmax10, then push this project to
> `main` and run the workflow from Actions → Build APK → Run workflow.
> Never guess a release tag.

### Local (Gradle, same as Actions)
```
export JAVA_HOME=$HOME/jdk17
export ANDROID_HOME=$HOME/asdk
~/gradle8/gradle-8.9/bin/gradle :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
Tesseract traineddata must be present in `app/src/main/assets/tessdata/`
(`eng.traineddata` + `ara.traineddata`); the workflow downloads them, and the
local copy is included in the delivered ZIP. They are git-ignored (binary blobs).

## Engineering decisions (v1)
- No OpenCV: the full OpenCV Android SDK is huge and breaks Actions builds.
  Perspective warp is a pure-Kotlin homography with bilinear sampling.
  OpenCV is a v2 option.
- OCR is Tesseract via JitPack (`cz.adaptech.tesseract4android:4.9.0`),
  ara+eng, LSTM_ONLY, fully on-device. If JitPack ever fails to resolve,
  the fallback is ML Kit Latin-only and Arabic OCR becomes a v1.1 gap —
  recorded honestly, never faked.
- Storage: Room (documents/pages/tags/notes) + image files under the app's
  private filesDir; exports go to Downloads/Skenr via MediaStore.

## Test card (Galaxy S25 Ultra, Android 16)
1. Open app → Home shows, copyright footer shows the current year.
2. Camera → scan an A4 page → crop corners drag correctly → filter تحسين → confirm.
3. Add 2 more pages to the same document → reorder, delete one.
4. Export PDF → open it outside the app → pages in order.
5. OCR on an Arabic page and an English page → text is copied/saved.
6. Collage: pick 3 images → 2×2 layout → export one-page PDF/JPG.
7. Search by a word from the OCR text and by tag → document found.
8. Lock a document with a PIN → wrong PIN refused, right PIN opens.
9. Kill and reopen the app → documents, folders and notes persist.
10. Airplane mode on → every feature above still works.
