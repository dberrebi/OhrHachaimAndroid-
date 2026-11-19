# Google Play submission

Everything Play Console asks for, ready to paste. The text was checked against Play's limits: app name 30 characters, short description 80, full description 4000.

## Files

| Play Console field | File |
| --- | --- |
| App bundle | `app/build/outputs/bundle/release/app-release.aab` after `./gradlew bundleRelease` (see "Signing") |
| App icon, 512 × 512 PNG | `play/graphics/icon-512.png` |
| Feature graphic, 1024 × 500 PNG | `play/graphics/feature-graphic.png` |
| Phone screenshots, at least 2 | Not made yet. Take them on a phone from the closed-test build: Home, This week, a reading, Search. |
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

Before each new upload, raise `versionCode` in `app/build.gradle.kts` (1, 2, 3, …). Play rejects a bundle whose version code it has seen.

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

1. Test and release → Testing → Closed testing → Create track (or use "Alpha").
2. Testers: create an email list with at least 12 Google account emails (12 is the minimum; add a few spares in case someone opts out).
3. Create a release, upload `app-release.aab`, release name `1.0 (1)`, release notes:
   - Hebrew: `גרסה ראשונה.`
   - English: `First release.`
4. Countries: choose where testers are (for example Israel, United States, and others as needed).
5. Send for review. After approval, copy the opt-in link from the track and send it to the testers. Each tester opens the link, taps "Become a tester", then installs from Play.
6. Ask testers to open the app a few times during the 14 days. Google asks about tester engagement when you apply.
7. After 14 days: Dashboard → Apply for production, answer the questions about the test, then create a production release with the same bundle (or a newer one).

Use the closed test to take the store screenshots and to check on real phones what could not be checked without an emulator:
- Back from a search result keeps the search.
- Returning to an earlier parasha puts it first in History.
- Links and the favourite star are readable in light mode.
- TalkBack offers copy and share on a passage.
