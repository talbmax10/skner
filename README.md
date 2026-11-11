# سكنر (Skenr) — v1.1.0 (P0 roadmap)

Native Android document scanner, built from scratch in Kotlin + Jetpack Compose.
Fully offline, no account, no cloud, no ads, no subscription. Own code only —
no CamScanner code, assets, logo or trademark.

- Package: `com.talbmax.skner`, versionCode 2, versionName 1.1.0
- minSdk 24, targetSdk 34, compileSdk 34
- CameraX, Room, PdfDocument, on-device OCR (Tesseract 5.5.1, ara+eng)

## Screens
1. Home — search (titles/tags/page OCR), folders, recent docs, big scan button,
   merge mode (multi-select). Copyright footer ONLY here:
   `© جميع الحقوق محفوظة — طالب المولى {year}` (year is dynamic, from the device calendar).
2. Camera — modes مستند/هوية/كتاب/صورة, flash, auto-capture (P0-1: accelerometer
   stability + Sobel edge detection + state machine SEARCH → COUNTDOWN → CAPTURE),
   manual capture button, auto-capture toggle.
3. Crop — 4 draggable corners, perspective warp (pure-Kotlin homography, no OpenCV),
   filters: أصلي/أبيض وأسود/رمادي/تحسين/سحري/تفتيح/اقتصادي/عكس/إزالة الظل.
4. Document — pages with reorder ↑↓, delete, rename, tags/notes, batch OCR for all
   pages + indexing, PDF export, share, print (system PrintManager), merge another
   doc, extract selected pages to a new doc or PDF, drawn signature templates
   (insert on a page), text signature, watermark, PIN lock.
5. Collage (تلصيق الصور) — combine 2+ images into one page (2×2 / 1×2 / 1×3 / free),
   export PDF/JPG. Free (paid in the original).
6. Signature — draw a signature with a finger, save as a transparent PNG template.

## P0 roadmap status (v1.1.0 — all 9 done)
- P0-1 auto-capture (stability + edges + countdown) ✓
- P0-2 five extra filters (magic/lighten/eco/invert/no_shadow) ✓
- P0-3 batch OCR for all pages + Room index ✓
- P0-4 merge docs (max 500 pages) ✓
- P0-5 split/extract pages ✓
- P0-6 reorder pages ✓
- P0-7 print via Android framework ✓
- P0-8 drawn signature + saved templates + placement ✓
- P0-9 search titles/tags/page OCR ✓

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

## Test card (Galaxy S25 Ultra, Android 16) — v1.1.0 P0
1. Open app → Home shows, copyright footer shows the current year.
2. Camera → hold the phone steady over an A4 page → auto-capture fires after the
   1s countdown (toggle it off to use manual capture) → crop corners drag correctly.
3. Crop → try filters سحري / تفتيح / اقتصادي / عكس / إزالة الظل → confirm.
4. Add 2 more pages → reorder with ↑↓ → delete one → export PDF → pages in order.
5. Batch OCR: «فهرسة OCR لكل الصفحات» → progress shows → search Home by a word
   from page 2 or 3 (not page 1) → document found with «مطابقة في نص صفحة».
6. Merge: Home «دمج مستندات» → select 2 docs → merged doc opens with all pages.
7. Split: in a doc, select 2 pages → «استخراج إلى مستند جديد» → new doc has 2 pages.
8. Print: «طباعة» → system print dialog appears → send to a printer/PDF printer.
9. Signature: «رسم توقيع» → draw → save → in a doc page tap «＋ توقيع» → export PDF
   → the drawn signature appears on the page.
10. Collage: pick 3 images → 2×2 layout → export one-page PDF/JPG.
11. Lock a document with a PIN → wrong PIN refused, right PIN opens.
12. Kill and reopen the app → documents, folders and notes persist (Room v2 migration).
13. Airplane mode on → every feature above still works.
