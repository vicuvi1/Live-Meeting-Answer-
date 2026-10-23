// Audio capture and speech services (Romanian STT & TTS)

export class AudioService {
  constructor(onTranscriptCallback, onErrorCallback) {
    this.onTranscript = onTranscriptCallback;
    this.onError = onErrorCallback;
    this.recognition = null;
    this.isListening = false;
    this.mediaStream = null;
    this.audioContext = null;
  }

  // Initialize browser Web Speech Recognition for Romanian (ro-RO)
  startWebSpeechRecognition() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      if (this.onError) {
        this.onError('Browserul nu suportă Web Speech API direct. Folosiți modul simulator sau backend Deepgram.');
      }
      return false;
    }

    try {
      this.recognition = new SpeechRecognition();
      this.recognition.lang = 'ro-RO';
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.maxAlternatives = 1;

      this.recognition.onstart = () => {
        this.isListening = true;
      };

      this.recognition.onresult = (event) => {
        for (let i = event.resultIndex; i < event.results.length; ++i) {
          const transcript = event.results[i][0].transcript;
          const isFinal = event.results[i].isFinal;
          if (transcript.trim() && this.onTranscript) {
            this.onTranscript(transcript.trim(), isFinal);
          }
        }
      };

      this.recognition.onerror = (event) => {
        console.warn('Speech recognition notice:', event.error);
        if (event.error === 'not-allowed') {
          if (this.onError) this.onError('Accesul la microfon a fost refuzat.');
        }
      };

      this.recognition.onend = () => {
        // Auto-restart if still flagged as listening (handles browser auto-timeout)
        if (this.isListening) {
          try {
            this.recognition.start();
          } catch (e) {
            // Already started or restarting
          }
        }
      };

      this.recognition.start();
      return true;
    } catch (err) {
      console.error('Failed to start speech recognition:', err);
      if (this.onError) this.onError(err.message);
      return false;
    }
  }

  stopWebSpeechRecognition() {
    this.isListening = false;
    if (this.recognition) {
      try {
        this.recognition.stop();
      } catch (e) {
        // Ignored
      }
      this.recognition = null;
    }
  }

  // Capture System or Tab Audio (ideal when running Zoom / Google Meet on PC/Laptop)
  async startSystemAudioCapture() {
    try {
      this.mediaStream = await navigator.mediaDevices.getDisplayMedia({
        video: true, // Required by browser API to request system/tab audio
        audio: {
          echoCancellation: false,
          noiseSuppression: false,
          autoGainControl: false
        }
      });

      const audioTrack = this.mediaStream.getAudioTracks()[0];
      if (!audioTrack) {
        throw new Error('Nu a fost selectată o sursă audio (bifați "Share audio" în fereastra de partajare).');
      }

      this.isListening = true;
      return true;
    } catch (err) {
      console.error('System audio capture error:', err);
      if (this.onError) this.onError(err.message);
      return false;
    }
  }

  stopAll() {
    this.stopWebSpeechRecognition();
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(track => track.stop());
      this.mediaStream = null;
    }
    if (this.audioContext) {
      this.audioContext.close();
      this.audioContext = null;
    }
    this.isListening = false;
  }

  // Text-To-Speech: Reads the answer in natural Romanian directly to Olivia's earphone
  static speakRomanian(text) {
    if (!('speechSynthesis' in window) || !text) return;
    try {
      window.speechSynthesis.cancel(); // Stop any ongoing speech

      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'ro-RO';
      utterance.rate = 1.0;
      utterance.pitch = 1.0;

      // Try finding a Romanian voice if available
      const voices = window.speechSynthesis.getVoices();
      const roVoice = voices.find(v => v.lang.startsWith('ro'));
      if (roVoice) utterance.voice = roVoice;

      window.speechSynthesis.speak(utterance);
    } catch (e) {
      console.warn('Speech synthesis notice:', e);
    }
  }

  // Device vibration when Olivia's name or question is detected
  static vibrateAlert() {
    try {
      if ('vibrate' in navigator) {
        // Vibrate pattern: 250ms pulse, 150ms pause, 250ms pulse, 150ms pause, 450ms attention pulse
        navigator.vibrate([250, 150, 250, 150, 450]);
      }
    } catch (e) {
      // Ignored if device does not support or permission denied
    }
  }

  // Voice whisper / announce: Reads the incoming question directly to Olivia's earphone
  static speakQuestion(questionText) {
    if (!('speechSynthesis' in window) || !questionText) return;
    try {
      window.speechSynthesis.cancel();

      const clean = questionText.replace(/^["']|["']$/g, '').trim();
      if (!clean) return;

      const utterance = new SpeechSynthesisUtterance(`Întrebare: ${clean}`);
      utterance.lang = 'ro-RO';
      utterance.rate = 1.05;
      utterance.pitch = 1.0;

      const voices = window.speechSynthesis.getVoices();
      const roVoice = voices.find(v => v.lang.startsWith('ro'));
      if (roVoice) utterance.voice = roVoice;

      window.speechSynthesis.speak(utterance);
    } catch (e) {
      console.warn('Speech synthesis question notice:', e);
    }
  }
}
