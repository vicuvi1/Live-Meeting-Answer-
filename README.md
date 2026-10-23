# 🎙️ Olivia Meeting Copilot (Live Meeting Assistant)

[![Python](https://img.shields.io/badge/Python-3.11%2B-blue.svg)](https://python.org)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115%2B-009688.svg)](https://fastapi.tiangolo.com)
[![React](https://img.shields.io/badge/React-18.3-61DAFB.svg)](https://react.dev)
[![Vite](https://img.shields.io/badge/Vite-6.0-646CFF.svg)](https://vitejs.dev)
[![Language](https://img.shields.io/badge/Language-Romanian%20(ro--RO)-emerald.svg)]()

> **An ultra-fast, discreet AI Meeting Copilot designed for users juggling dual meetings simultaneously (e.g., in-person and online on Zoom/Google Meet). It transcribes Romanian conversation in real-time, alerts the user when they are called upon, and provides a 1-tap "Lifesaver" button that instantly generates the perfect natural Romanian spoken response.**

---

## 🎯 The Core Problem & Solution

### The Challenge:
1. **Dual Meetings:** Olivia is physically in an in-person meeting while also connected to an online meeting (Zoom/Google Meet/Teams) on her phone or laptop.
2. **The "Single Participant" Rule:** In the online meeting, **there must strictly be ONLY ONE Olivia visible**—no suspicious bots or duplicate accounts in the participant list!
3. **Immediate Panic Relief:** When someone in the online meeting suddenly asks: *"Olivia, ce părere ai despre termenul de vineri?"*, she needs to know immediately what was asked and have the exact 1–2 sentence professional reply ready to read out loud.

### The Solution:
- **Discreet Companion Interface:** Runs either on her phone via mobile browser/PWA or as an Android floating overlay bubble directly over Zoom.
- **Real-Time Romanian STT:** Transcribes the meeting conversation with low latency and highlights when Olivia is addressed.
- **💡 "Ce să răspund?" (Lifesaver Button):** Tapping the button feeds the last 3–5 minutes of meeting context to a fast LLM (Gemini 2.5 Flash / Smart Heuristic engine) which formulates a tailored, natural, professional Romanian spoken response in under a second.
- **Long-Term Meeting Memory:** Stores complete timestamped transcripts, AI answer history, and generates executive summaries and action items.

---

## 🏗️ System Architecture

```
                  ┌─────────────────────────────────────┐
                  │    Meeting Room / Zoom / Meet       │
                  └──────────────────┬──────────────────┘
                                     │ Romanian Audio Stream
                                     ▼
        ┌─────────────────────────────────────────────────────────┐
        │            FastAPI Async Backend Engine                 │
        │                                                         │
        │  • WebSocket Streaming & Session Manager                │
        │  • Keyword & Question Detector (Regex / Romanian NLP)   │
        │  • STT Bridge (Deepgram Nova-2 / Web Speech ro-RO)      │
        │  • Lifesaver LLM Engine (Gemini 2.5 Flash / Heuristic)  │
        │  • Persistent SQLite Database (SQLAlchemy + aiosqlite)  │
        └────────────────────────────┬────────────────────────────┘
                                     │ Bidirectional WebSocket
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        User Interface Layer                            │
│                                                                        │
│  📱 Mobile Web App (PWA)               🤖 Android Native Companion     │
│  • Senior-friendly, high-contrast      • SYSTEM_ALERT_WINDOW chat head │
│  • Live auto-scrolling Romanian subs   • Floats directly over Zoom     │
│  • Tactile "Ce să răspund?" button     • MediaProjection internal      │
│  • Device vibration alerts              audio capture                  │
│  • Romanian Text-To-Speech (earphone)  • One-touch answer card         │
│  • Meeting dialogue test simulator                                     │
│  • Meeting History, Search & Summaries                                 │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start (Run in 2 Minutes)

### Prerequisites:
- **Node.js** (v18+)
- **Python** (3.11+)

### 1. Clone the Repository:
```bash
git clone https://github.com/vicuvi1/Live-Meeting-Answer-.git
cd Live-Meeting-Answer-
```

### 2. Automated One-Click Start (Windows):
Double-click `start_dev.bat` or run in PowerShell:
```powershell
.\start_dev.ps1
```

### Or Start Manually:

#### Backend:
```bash
# Set up virtual environment
python -m venv .venv

# Activate venv:
# Windows: .venv\Scripts\activate
# Linux/Mac: source .venv/bin/activate

# Install dependencies
pip install -r backend/requirements.txt

# Run the backend
cd backend
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

#### Frontend:
```bash
cd frontend
npm install
npm run dev
```

Open **`http://localhost:5173`** on your computer, or open **`http://<YOUR_COMPUTER_LOCAL_IP>:5173`** directly on Olivia's phone connected to the same WiFi.

---

## 🎮 How to Test Without a Real Meeting (Built-in Simulator)

The app comes with an interactive **Romanian Business Meeting Dialogue Simulator**:
1. Click **"Simulează Dialog"** in the top bar.
2. Select a scenario:
   - *Discuție Buget & Resurse (T4)*
   - *Termen Livrare Proiect & Raport*
   - *Validare Strategie Echipă*
3. Click **"Redă Automat"** or **"Pasul Următor"**.
4. Watch the live subtitles roll in Romanian. When the colleague says: *"Olivia, ce părere ai despre estimările de buget?"*, notice:
   - The line glows in bright amber/gold.
   - The phone gives a subtle vibration alert.
   - The big **"💡 Ce să răspund acum?"** button pulses.
5. Tap the button—the AI provides the exact natural sentence for Olivia to read out loud!

---

## 🤖 Android Native Floating Overlay (Over Zoom)

The repository includes the complete Kotlin Android project inside `/android`:
- **Floating Chat Head:** Uses `SYSTEM_ALERT_WINDOW` to display a draggable floating icon on top of the native Zoom or Google Meet app.
- **Audio Service:** Hooks into Android 10+ `AudioPlaybackCaptureConfiguration` via `MediaProjection`.
- **Zero Zoom Footprint:** Only 1 Olivia appears in the call because the overlay runs locally on the same device.

To build the APK:
1. Open the `/android` folder in **Android Studio**.
2. Build & Run or select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
3. Install the APK on her phone and grant the *"Display over other apps"* permission.

---

## ⚙️ Configuration & Environment Variables

Copy `backend/.env.example` to `backend/.env` or configure directly from the in-app **Settings Modal**:

```ini
TARGET_NAME=Olivia
DEFAULT_LANGUAGE=ro
DATABASE_URL=sqlite+aiosqlite:///./data/copilot.db

# Provider Choice: "gemini" or "openrouter"
DEFAULT_PROVIDER=gemini

# Option 1: Google Gemini (Free tier available at https://aistudio.google.com/)
GEMINI_API_KEY=your_gemini_api_key_here
LLM_MODEL=gemini-2.5-flash

# Option 2: OpenRouter (Free & Paid models like Gemini, Llama 3.3, DeepSeek at https://openrouter.ai/)
OPENROUTER_API_KEY=your_openrouter_api_key_here
OPENROUTER_MODEL=google/gemini-2.0-flash-exp:free

# Optional: Add Deepgram API key for server-side Romanian STT
DEEPGRAM_API_KEY=your_deepgram_api_key_here
```

### 🆓 Supported Free Models:
- **Google Gemini Direct:** `gemini-2.5-flash`, `gemini-1.5-flash`, `gemini-1.5-flash-8b` (free on Google AI Studio).
- **OpenRouter Free Tier:** `google/gemini-2.0-flash-exp:free`, `meta-llama/llama-3.3-70b-instruct:free`, `mistralai/mistral-7b-instruct:free` (100% free via OpenRouter key).
- **Instant Connection Test:** In the app Settings, click **"⚡ Testează Cheia & Conexiunea"** to verify your key and measure model response time.

---

## 🧪 Running Automated Tests

To run the backend test suite:
```bash
cd backend
pytest
```
*All tests verify database lifecycle, Romanian keyword/question detection, and AI response generation.*

---

## 📄 License
MIT License. Built with ❤️ to make meeting multitasking effortless and stress-free.
