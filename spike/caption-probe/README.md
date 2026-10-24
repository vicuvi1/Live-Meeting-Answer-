# Caption Probe: phone-only test

**Question this answers:** on the Xiaomi 15T Pro, can an app read the live captions that
Zoom / Google Meet show for a **Romanian** call, reliably and fast enough?

The full meeting app depends on this. A normal Android app can't record call audio from
Zoom/Meet, so the only phone-only route is to let Zoom/Meet do the speech-to-text and read
their caption text through an accessibility service.

The probe reads all text on screen (including system caption overlays), logs only the
lines that are new or changed with millisecond timestamps, and shows the latest line in a
floating strip. Nothing leaves the phone until you export the log yourself.

## Install

1. On the phone, open the latest **Caption Probe (test build)** release on GitHub and
   download `caption-probe.apk`. Or copy
   `spike/caption-probe/app/build/outputs/apk/debug/app-debug.apk` from the PC.
2. Install it and allow "install from this source" when asked.
3. Open **Caption Probe**:
   - **Step 1 button** opens App info. Tap **⋮ → Allow restricted settings**. Android blocks
     accessibility for sideloaded apps until you do this. If the option isn't there, do step 2
     first: the warning that appears unlocks it.
   - **Step 2 button**: Accessibility → Downloaded apps → **Caption Probe → ON**.
     HyperOS shows a warning with a countdown. Accept it.
   - The status should show **✅ Accessibility service ON** and a black strip appears at the top.

## Test (about 30 minutes, two people)

Use a test call between you and your mother, not a real work meeting. Speak **Romanian**,
in normal meeting phrases, including her name and questions ("…, ce părere ai despre…?").

For each app (**Zoom**, then **Google Meet**):

1. Join the call on the Xiaomi.
2. Turn on captions:
   - **Zoom:** More (…) → Captions / Show captions → set the spoken language to **Romanian**.
     Captions may need to be enabled by the host or account (zoom.us → Settings → Automated captions).
   - **Meet:** tap the **CC** button, then choose **Romanian** as the meeting language if it's offered.
   - Write down whether Romanian is offered at all, and on which account (free / Workspace).
3. Tap **REC** on the black strip. Talk for 3–5 minutes.
4. While captions are visible, tap **DUMP** 2–3 times.
5. Also try the meeting with the screen locked, with Zoom minimized, and with the strip hidden.
6. Tap **STOP**.

Extra (optional): try **Android Live Caption** (Settings → Accessibility → Live Caption) and
Xiaomi's **AI subtitles**, if the phone offers Romanian for either. The probe records those too.

Then in Caption Probe: **Export log to Downloads + share** and send the `.txt` back.
It contains whatever was on screen (names, chat), so keep it private.

## What we look for in the log

| Check | Good result |
|---|---|
| Romanian captions offered | Zoom and/or Meet offers Romanian on her account |
| Readable | Lines like `~ [Zoom] id=… :: <words being said>` appear |
| Accuracy | Romanian words mostly correct, names recognisable |
| Speed | Text appears within ~1–2 s of speaking (compare timestamps with what you said) |
| Stability | Still logging after 30 min, with the screen off / app in background |

**Pass** means at least one of Zoom/Meet gives readable, mostly correct Romanian captions
that keep flowing for 30 minutes. Then the real app is built on this route. **Fail** means
the phone-only plan is blocked, and the realistic options are a second device (an old phone
or tablet running the meeting, or one listening to the speaker) or a laptop.
