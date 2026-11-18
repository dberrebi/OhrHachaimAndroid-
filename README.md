# אור החיים

An offline Android reader for **Ohr HaChaim** (אור החיים), Rabbi Chaim ibn Attar's commentary on the Torah, arranged by the weekly parasha. It follows the iOS app in [dberrebi/OhrHachaim](https://github.com/dberrebi/OhrHachaim) (the behaviour and design source of truth, `main` at `25208b8`).

The commentary is Hebrew only. English is an interface language: menus, settings, and help. There is no translation of the commentary.

The app does not use the network, has no analytics, and has no payments or donation links.

## Build

Requirements: JDK 17 or 21, Android SDK 36, and build-tools 36.0.0.

```bash
./gradlew assembleDebug
./gradlew test
```

`test` runs the JVM unit tests (reading calendar, nikud-insensitive search against the bundled database, history) and the Android unit-test task. The database checksum is checked again during `assembleDebug`.

The application id is `app.ohrhachaim.android`. That id is a placeholder until Freedom confirms it. It cannot be changed after the first Play listing.

minSdk is 26. targetSdk and compileSdk are 36. There is no `INTERNET` permission.

## Data: stay in sync with iOS

The file `data/ohr_hachaim.sqlite` is a byte-for-byte copy of `OhrHaChaim/OhrHaChaim/Resources/ohr_hachaim.sqlite` from the iOS repository. `data/manifest.json` is the matching manifest. The current checksum is:

`9df2715ed8c7c389a0a6ff827b36c1008da050f83823eb9352fefd208c88e142`

Do not edit the database in this repository, and do not rebuild it here. The iOS script `scripts/build_data.py` is the source of truth (Python standard library only). When the calendar window or the text changes:

1. Run `python3 scripts/build_data.py` in the iOS repository.
2. Copy **both** `OhrHaChaim/OhrHaChaim/Resources/ohr_hachaim.sqlite` and `data/manifest.json` into this repository's `data/` directory.
3. Ship that same database to both apps, so comment ids (favourites, resume) and weekly rows stay aligned.

`./gradlew verifyDatabase` fails the build if the SHA-256 of the sqlite file does not match `sha256` in the manifest. The app copies the asset into private storage on first launch, checks the same checksum, and opens it read-only with `androidx.sqlite` `BundledSQLiteDriver`. Android's platform SQLite is not compiled with FTS5. The bundled driver is, which is what `comments_fts` needs. Startup refuses to treat the corpus as loaded if FTS5 is missing.

Readings outside 2024–2038 need a new run of the iOS script and a new database in both apps. This app does not calculate the Hebrew calendar on the device.

## What the app does

- **Home.** Centred rows: resume, history, this week's parasha, search, the five books, settings, help, and about. The dark top bar has the iOS toolbar mark, share, and a star that opens favourites.
- **Book.** The parashot of that book. Genesis also lists the author's introduction.
- **Reading.** One parasha, or two when they are read together, grouped by chapter and verse. `<b>` is the dibbur hamatchil and is emphasised. A star saves a passage. A long press copies or shares the Hebrew passage with its citation. Commentary stays right to left in both interface languages.
- **This week.** Today's row in the bundled Israel or Diaspora table, otherwise the next row one to six days ahead, otherwise the latest row one to ten days back. Combined parashot open together. וזאת הברכה is scheduled on Simchat Torah. The Israel calendar starts on only when the device region is `IL`.
- **Resume and history.** A passage is saved after it stays near the top for 4 seconds once the reader has scrolled, or after 20 seconds if the screen was only opened. History keeps one row per parasha, capped at 40. Resume freezes the comment id when the screen opens.
- **Search.** Offline FTS. A query without nikud matches the pointed text.
- **Settings.** Hebrew or English, applied immediately to strings, layout direction, and dates. Israel or Diaspora. Nikud, on by default. Type size. Light, dark, or system appearance. A privacy-policy link that opens in the browser.
- **About and help.** The author, the sources, the licences, and the Hebcal credit stored in the database: "Torah reading schedule from Hebcal.com, CC BY 4.0".

Genesis 10 (Noach) and Leviticus 27 (Bechukotai) have no commentary in this edition. The reading shows a short note.

On a wide window the home list stays beside the detail, with a welcome pane until something is selected.

## Licences and attribution

- Commentary: Sefaria Vocalized Edition of Or HaChaim on the five books. Hebrew. [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). [sefaria.org](https://www.sefaria.org)
- Author's introduction: Sefaria Hebrew version titled exactly `On Your Way`. Public domain. The version whose title ends with a space was not used.
- Reading calendar: [Hebcal.com](https://www.hebcal.com), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The required credit is the `hebcal_attribution` value in the database.
- Typeface: Noto Serif Hebrew, © 2022 The Noto Project Authors, [SIL Open Font License 1.1](app/src/main/assets/licenses/OFL.txt). The licence file is in the APK next to the font. The font is not sold by itself.

The code licence for this repository is not chosen yet. Do not add one until Freedom decides.

## Not in this version

No Play upload, no Play Billing, no PayPal link, and no donate button. The iOS support site is not linked: that page includes a PayPal donation. Settings and About open the iOS privacy URL, which is what the iOS app does. Whether that page may be linked from Android is an open question, because the privacy page links back to the support page. See the TODO on `AppLinks`.

No widgets, notifications, accounts, or sync. History, favourites, and settings stay on the device. Backup of app data is turned off.
