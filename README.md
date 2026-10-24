# Asistent Ședințe

A phone-only meeting helper for someone who follows an online meeting (Zoom / Google Meet)
while also busy with something else. Built for a Xiaomi 15T Pro (HyperOS); works on Android 10+.

**What it does**
- **Alerts you when you're addressed.** A strong vibration and notification (mirrored to a
  Mi Band / Xiaomi watch) when someone says your name with a question. A gentle one when
  you're mentioned or a topic you follow comes up.
- **"Ce am pierdut?" (What did I miss?).** One tap shows what's being discussed now, the last
  3 important points, and what's expected from you. It's kept ready in the background.
- **"Ce să răspund?" (What should I answer?).** The question, what was said just before, and a
  1–2 sentence answer based on your own notes. When the AI has no facts it says so and gives a
  safe, non-committal line. It never invents numbers or promises.
- **After the meeting:** your tasks (who asked, deadline), what you promised, a summary and
  decisions. Shareable to Telegram / WhatsApp / e-mail.
- **Health warning** if captions stop arriving, so it never fails silently.
- History of every meeting, stored on the phone.

## How it hears the meeting

Android doesn't let an app record another app's call audio. So the app turns on **Zoom's /
Meet's own live captions** and reads them from the screen through an accessibility service.
The AI then fixes recognition mistakes (names, diacritics, punctuation) in the background,
using the names and terms from Settings.

Limits that follow from this:
- Captions must be **on (CC) and in Romanian** in Zoom/Meet, and the meeting must be **on screen**.
  The app keeps the screen awake while you're in a meeting.
- Accuracy is that of Zoom's/Meet's speech recognition, improved by the AI cleanup.
- The exact screen element holding the captions differs between app versions. The app learns
  it automatically (the region that keeps changing with sentences). If it doesn't, the
  diagnostic log shows why (see below).

## Install on the phone

Built for the **Xiaomi 15T Pro** (HyperOS 3, Android 16, 1280×2772); works on Android 10+.

1. Download the latest `asistent-sedinte-*.apk` from
   [Releases](https://github.com/vicuvi1/Live-Meeting-Answer-/releases/latest) on the phone and
   install it. Google Play Protect blocks the first install of apps with accessibility access:
   Play Store → profile → Play Protect → ⚙ → turn off scanning, install, turn it back on.
   HyperOS may also scan the app and warn; choose to install anyway.
2. Open **Asistent Ședințe**. The **Configurare** card lists what's left (8 steps on Xiaomi):
   - **Citirea subtitrărilor:** Accessibility → Downloaded apps → Asistent Ședințe → On.
     If it's greyed out (HyperOS): Settings → Apps → Manage apps → Asistent Ședințe → ⋮ (top right)
     → **Allow restricted settings**, then retry. This can be needed again after an update.
   - **Notificări:** allow.
   - **Numele tău** and **Cheia AI** (Setări). Use an **OpenRouter** key: Gemini's free tier allows
     only about 20 requests a day, which a single meeting uses up. With both keys filled in, the
     app falls back to OpenRouter automatically.
   - **Xiaomi (HyperOS):** Autostart on, Battery → No restrictions, **Floating notifications** on
     for "Întrebări pentru tine" (HyperOS turns them off for new apps, so alerts would arrive
     silently), and lock the app in Recents. Each step opens the right screen, then tap **Gata ✓**.
3. Watch alerts: Mi Fitness → app notifications → enable Asistent Ședințe.
4. **Setări → Test fără ședință:** send a line like "Maria, poți să ne spui când e gata raportul?".
   This checks the vibration, the answer and the summary without a real meeting.

In a meeting: join in Zoom or Meet (also Meet inside Gmail). The app turns on Romanian captions by
itself. The island around the front camera shows sound bars and the meeting timer; it stretches out
when she's mentioned and grows into the answer panel when she's asked something. Tap it to open,
tap elsewhere to close, long-press to hide until the meeting ends.

Updates: **Actualizează acum** in the app downloads the new version and opens Android's update
prompt. Every build on `main` publishes a signed APK that installs over the previous one, keeping
all data.

## If captions aren't picked up

Settings → Diagnostic → turn on **Jurnal diagnostic**, join a meeting with captions on, and tap
**Captură diagnostic** in the bubble panel a couple of times. Then **Exportă jurnalul** and send
the file. It shows exactly what Zoom/Meet put on screen. Lines marked `*` come from the element
the app locked onto. Keep the log private: it contains meeting text.

## Development

```
cd android
./gradlew testDebugUnitTest assembleDebug
```

- `caption/`: the accessibility service, the floating bubble, and `CaptionAssembler` (turns
  changing on-screen captions into final sentences; unit-tested).
- `engine/`: meeting session, name/question alerts, rolling digest, answers, summary.
- `ai/`: Gemini / OpenRouter client and prompts. `data/`: SQLite + settings.
- `ui/`: Compose screens (Acum, Istoric, Setări).

Release signing: CI uses the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD` secrets. Local release builds read an untracked `android/keystore.properties`.
Keep a backup of the keystore: without it, future updates can't be installed over the app.
