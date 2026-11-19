# Google Play submission

Everything Play Console asks for, ready to paste. The text was checked against Play's limits: app name 30 characters, short description 80, full description 4000.

## Files

| Play Console field | File |
| --- | --- |
| App bundle | `app/build/outputs/bundle/release/app-release.aab` after `./gradlew bundleRelease` (see "Signing") |
| App icon, 512 × 512 PNG | `play/graphics/icon-512.png` |
| Feature graphic, 1024 × 500 PNG | `play/graphics/feature-graphic.png` |
| Phone screenshots, at least 2 | Three uploaded in Play Console on 19 Nov 2026, taken from the testing build. They are not stored in this repository. |
| Privacy policy URL | https://dberrebi.github.io/OhrHachaimAndroid-/privacy/ |

## Signing

Use Play App Signing: Google keeps the app signing key, and you sign each upload with an **upload key**. If the upload key is ever lost, Play Console can reset it (Setup → App signing → Request upload key reset), so the app is not locked out.

The upload key lives outside the repository. To sign a release build, put the keystore anywhere private and create `keystore.properties` in the repository root. That file and `*.jks` are in `.gitignore`.

```properties
storeFile=/absolute/path/to/ohr-hachaim-upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

Then run `./gradlew bundleRelease`. Without `keystore.properties`, release builds are unsigned, as before.

Upload key certificate, SHA-256 (not secret; Play Console shows the same value after the first upload):
`28:B8:CD:B8:F9:40:C2:B9:C2:7D:8B:AC:38:10:17:F3:6B:9C:A3:3B:42:AC:CD:39:AA:21:B8:77:AF:FA:DB:55`

Play rejects a bundle whose version code it has seen. Uploads now normally come from the workflow (see "Automatic uploads"), which numbers them itself. A hand-made bundle needs a version code above the last one CI used: build it with `./gradlew bundleRelease -Pohr.versionCode=<number>`. Don't change the scheme without the owner.

## Release notes

The current notes live in `play/whatsnew/`, one file per Play locale: `whatsnew-iw-IL` (Hebrew) and `whatsnew-en-US` (English). Plain text, at most 500 characters each. The workflow attaches them to every upload; for a hand-made release, paste the same text. **Update both files in the same pull request as any change users will notice**, because merging it uploads a build with whatever they say.

Earlier notes:
- 1.1: the text now in `play/whatsnew/`.
- 1.0: `גרסה ראשונה.` / `First release.`

## Automatic uploads from GitHub

Live since 19 Nov 2026. `.github/workflows/play-publish.yml` builds a signed bundle and uploads it to the **internal testing** track, with the release notes from `play/whatsnew/`, whenever a merge to `main` changes the app: `app/`, `core/`, `data/`, `gradle/`, or the root build files. Merges that leave the app as it was (documentation, release notes alone, the workflows, `scripts/`) do not upload; to upload anyway, run the workflow by hand (Actions → Publish to Google Play → Run workflow, track `internal`). Changes still go through pull requests. The workflow never publishes to production: promote a tested build in Play Console.

### Sending a build to closed testing

Actions → Publish to Google Play → Run workflow → branch `main`, track `alpha` (Play's "Closed testing - Alpha" track). Each manual run also takes a new version code. Closed-track releases go through Google's review. While a review is pending (the first one was sent on 19 Nov 2026), a closed-track upload is added to the changes under review rather than replacing them.

### Google side

| Item | Value |
| --- | --- |
| Google Cloud project | `ohr-hachaim-play`, with the Google Play Android Developer API enabled |
| Service account | `play-publisher@ohr-hachaim-play.iam.gserviceaccount.com`. It has no Cloud roles. Its JSON key is the `PLAY_SERVICE_ACCOUNT_JSON` secret |
| Play Console access | Users and permissions, app-level for this app only: "View app information" and "Release apps to testing tracks". Play adds "View app quality information" by itself. No account-wide permissions and no production release permission, so the key cannot publish to production even if the workflow asked it to |
| API access linking | Not needed. Play Console's old Setup → API access page, which linked a Cloud project, no longer exists. Inviting the service account under Users and permissions is enough |

### Repository secrets

Settings → Secrets and variables → Actions. All three are set; don't change them without the owner. If one is missing, the workflow skips the upload and stays green.

| Secret | Value |
| --- | --- |
| `PLAY_SERVICE_ACCOUNT_JSON` | The service account's JSON key |
| `UPLOAD_KEYSTORE_BASE64` | The upload keystore, base64-encoded. In PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("ohr-hachaim-upload.jks"))` |
| `UPLOAD_KEYSTORE_PASSWORD` | The keystore password |

If Play ever answers "Only releases with status draft may be created on draft app", finish a rollout by hand in Play Console, then re-run the workflow.

### Version codes

CI builds use **100 + the workflow's run number**, so each run gets a new code. A hand-made bundle must use a code above the last CI one. Don't change the scheme without the owner.

| Version code | Version | Made by | Track | Notes |
| --- | --- | --- | --- | --- |
| 1 | 1.0 | Hand | Internal testing | First upload, registered the upload key |
| 2 | 1.1 | Hand (`ohr-hachaim-1.1-2.aab`) | Closed testing - Alpha | In Google's first review since 19 Nov 2026 |
| 102 | 1.1 | CI, run 44579959643 (run number 2, manual) from `main` at `c288d98` | Internal testing | First automatic upload |
| 103 | 1.1 | CI, run 44587024317 (run number 3, merge of #6) from `main` at `8998e25` | Internal testing | First upload with release notes from `play/whatsnew/`; the app is the same as 102 |

The next CI build is 104 or higher. Run numbers also count runs that upload nothing, so codes can skip. Add a row here when a build is promoted or a hand-made one is uploaded.

## Create the app

All apps → Create app:

- App name: `אור החיים` (the default language is Hebrew; add English, see below)
- Default language: Hebrew – he
- App or game: App
- Free or paid: Free (this cannot be changed to paid later)
- Declarations: tick both

## Store listing

Grow → Store presence → Main store listing. Add English as a second language under "Manage translations".

### Hebrew (default)

App name:

```
אור החיים
```

Short description:

```
פירוש אור החיים על התורה, מנוקד, לפי פרשת השבוע. פועל בלי אינטרנט.
```

Full description:

```
אור החיים הוא פירושו של רבי חיים בן עטר על התורה. האפליקציה מביאה את הפירוש המלא בעברית על חמשת חומשי התורה, מסודר לפי פרשות השבוע, ופועלת בלי חיבור לאינטרנט.

• פרשת השבוע נפתחת בלחיצה אחת, לפי לוח ארץ ישראל או חוץ לארץ, כולל פרשות מחוברות ופרשת וזאת הברכה בשמחת תורה.
• הנוסח המנוקד של ספריא, עם הדגשת דיבור המתחיל. אפשר להציג את הטקסט גם בלי ניקוד.
• הקדמת המחבר לספר בראשית.
• חיפוש בכל הפירוש, עם ניקוד או בלעדיו.
• חזרה למקום האחרון, היסטוריית קריאה ומועדפים.
• העתקה ושיתוף של כל קטע.
• ממשק בעברית או באנגלית, מראה בהיר או כהה, גודל אות מתכוונן ותמיכה ב-TalkBack.

הפירוש בעברית בלבד. אין באפליקציה תרגום.

אין חשבון, אין פרסומות ואין ניתוח שימוש. לאפליקציה אין הרשאת אינטרנט, וההיסטוריה והמועדפים נשמרים רק במכשיר שלכם.

מקורות: Sefaria Vocalized Edition (CC0); הקדמת המחבר מספריא (נחלת הכלל); לוח הפרשות מ-Hebcal.com (CC BY 4.0).
```

### English

App name:

```
Ohr HaChaim
```

Short description:

```
Ohr HaChaim's Torah commentary in Hebrew, by weekly parasha. Works offline.
```

Full description:

```
Ohr HaChaim (אור החיים) is Rabbi Chaim ibn Attar's commentary on the Torah. This app brings the complete Hebrew commentary on the five books to your phone, arranged by the weekly parasha, and works with no internet connection.

• This week's parasha opens with one tap, following the Israel or Diaspora calendar, including combined parashot and Vezot HaBerachah on Simchat Torah.
• The vocalized text from Sefaria, with the dibbur hamatchil emphasised. Nikud can be turned off.
• The author's introduction to Genesis.
• Search the whole commentary, with or without nikud.
• Resume where you stopped, reading history, and favourites.
• Copy or share any passage.
• Hebrew or English interface, light and dark appearance, adjustable text size, and TalkBack support.

The commentary is in Hebrew. The app does not include a translation.

No account, no ads, no analytics. The app has no internet permission, and your history and favourites stay on your device.

Sources: Sefaria Vocalized Edition (CC0); the author's introduction from Sefaria (public domain); the reading calendar from Hebcal.com (CC BY 4.0).
```

### Other listing fields

- Category: Books & Reference
- Contact email: `ohrhachaimapp@gmail.com`
- Website: leave empty, or https://dberrebi.github.io/OhrHachaimAndroid-/privacy/

## App content

Policy and programs → App content. Answers that match what the app does:

| Section | Answer |
| --- | --- |
| Privacy policy | https://dberrebi.github.io/OhrHachaimAndroid-/privacy/ |
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access |
| Content rating | Category "Reference, News, or Educational". No to violence, sexual content, profanity, controlled substances, gambling. No to user interaction or content sharing between users (sharing uses the system share sheet), no location sharing, no digital purchases. Expected result: Everyone / PEGI 3 |
| Target audience | 13 and over. Do not select under-13 age groups unless you want the app in the Families program, which adds requirements |
| News app | No |
| Data safety | "Does your app collect or share any of the required user data types?" **No**. Nothing leaves the device and there is no internet permission. That makes the encryption-in-transit and account-deletion questions not applicable |
| Government app | No |
| Financial features | None |
| Health | None |

## Closed test (required before production)

New personal accounts must run a closed test with at least **12 testers who stay opted in for 14 days in a row** before applying for production.

Status on 19 Nov 2026: track "Closed testing - Alpha" (`alpha`), all 178 countries, testers from the email list "Owner" (3 people so far), release "1.1 (2)" in Google's first review. Steps 1–5 are done; continue at step 5's "After approval".

1. Test and release → Testing → Closed testing → Create track (or use "Alpha").
2. Testers: create an email list with at least 12 Google account emails (12 is the minimum; add a few spares in case someone opts out).
3. Create a release with the bundle and the notes from `play/whatsnew/`. Later closed-track builds can come from the workflow (see "Sending a build to closed testing").
4. Countries: choose where testers are (for example Israel, United States, and others as needed).
5. Send for review. After approval, copy the opt-in link from the track and send it to the testers. Each tester opens the link, taps "Become a tester", then installs from Play.
6. Ask testers to open the app a few times during the 14 days. Google asks about tester engagement when you apply.
7. After 14 days: Dashboard → Apply for production, answer the questions about the test, then create a production release with the same bundle (or a newer one).

Use the closed test to take the store screenshots and to check on real phones what could not be checked without an emulator:
- Back from a search result keeps the search.
- Returning to an earlier parasha puts it first in History.
- Links and the favourite star are readable in light mode.
- TalkBack offers copy and share on a passage.
