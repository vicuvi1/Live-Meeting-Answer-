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

1. Download the latest `asistent-sedinte-*.apk` from
   [Releases](https://github.com/vicuvi1/Live-Meeting-Answer-/releases/latest) on the phone and
   install it (allow installing from the browser when asked).
2. Open **Asistent Ședințe**. The **Pregătire** card lists what's left:
   - **Citirea subtitrărilor:** Accessibility → Downloaded apps → Asistent Ședințe → On.
     If it's greyed out: App info → ⋮ (top right) → **Allow restricted settings**, then retry.
   - **Notificări:** allow.
   - **Setări → Despre tine:** her name, other forms of it, notes (role, projects, facts),
     colleagues' names and terms, topics to follow.
   - **Setări → AI:** a Gemini key (free at aistudio.google.com/apikey) or an OpenRouter key,
     then **Testează cheia**.
3. **Setări → Telefon Xiaomi:** Autostart → On, Battery → No restrictions, and lock the app in Recents.
4. Watch alerts: Mi Fitness → app notifications → enable Asistent Ședințe.
5. **Setări → Test fără ședință:** send a line like "Maria, poți să ne spui când e gata raportul?".
   This checks the vibration, the answer and the summary without a real meeting.

In a meeting: join in Zoom/Meet, tap **CC**, choose Romanian. The bubble turns green (listening),
amber (mentioned or topic) or red (question). Tap it for the panel.

Updates: the app checks GitHub and shows **Descarcă actualizarea**. Every build on `main`
publishes a signed APK that installs over the previous one, keeping all data.

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
