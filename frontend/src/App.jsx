import React, { useState, useEffect, useRef } from 'react';
import Header from './components/Header';
import FloatingCopilot from './components/FloatingCopilot';
import AnswerModal from './components/AnswerModal';
import MeetingSimulator from './components/MeetingSimulator';
import SessionHistory from './components/SessionHistory';
import SettingsModal from './components/SettingsModal';
import { api } from './services/api';
import { WebSocketClient } from './services/websocketClient';
import { AudioService } from './services/audioCapture';

export default function App() {
  const [activeTab, setActiveTab] = useState('copilot'); // 'copilot' | 'history'
  const [session, setSession] = useState(null);
  const [transcripts, setTranscripts] = useState([]);
  const [currentAnswer, setCurrentAnswer] = useState(null);
  const [precomputedAnswer, setPrecomputedAnswer] = useState(null);
  const [isLoadingAnswer, setIsLoadingAnswer] = useState(false);
  const [recentAlert, setRecentAlert] = useState(null);
  const [connectionStatus, setConnectionStatus] = useState('disconnected');
  
  // Modals
  const [showSimulator, setShowSimulator] = useState(false);
  const [showSettings, setShowSettings] = useState(false);

  // Settings
  const [settings, setSettings] = useState({
    targetName: localStorage.getItem('olivia_target_name') || 'Olivia',
    geminiKey: localStorage.getItem('olivia_gemini_key') || '',
    deepgramKey: localStorage.getItem('olivia_deepgram_key') || ''
  });

  const wsClientRef = useRef(null);

  // 1. Initialize or load active session
  useEffect(() => {
    initActiveSession();
  }, []);

  const initActiveSession = async () => {
    try {
      // Check if there is an active session in history
      const list = await api.listSessions(1);
      if (list && list.length > 0 && list[0].status === 'active') {
        const active = list[0];
        setSession(active);
        const existingTranscripts = await api.getTranscripts(active.id);
        setTranscripts(existingTranscripts);
        connectWebSocket(active.id);
      } else {
        // Start a clean new session
        const newSession = await api.createSession(`Ședință Live — ${new Date().toLocaleDateString('ro-RO')}`);
        setSession(newSession);
        setTranscripts([]);
        connectWebSocket(newSession.id);
      }
    } catch (err) {
      console.error('Failed to initialize session:', err);
    }
  };

  // 2. Connect to WebSocket for Live Streaming & Real-time Alerts
  const connectWebSocket = (sessionId) => {
    if (wsClientRef.current) {
      wsClientRef.current.disconnect();
    }

    const ws = new WebSocketClient(
      sessionId,
      (data) => {
        if (data.type === 'TRANSCRIPT_SEGMENT') {
          setTranscripts(prev => [...prev, data]);
        } else if (data.type === 'TARGET_ALERT') {
          setRecentAlert(data);
          AudioService.vibrateAlert();
        } else if (data.type === 'PRECOMPUTED_ANSWERS') {
          // Speculative 0ms background cache
          setPrecomputedAnswer(data);
        } else if (data.type === 'QUICK_ANSWER') {
          setCurrentAnswer(data);
          setIsLoadingAnswer(false);
        }
      },
      (status) => {
        setConnectionStatus(status);
      }
    );

    ws.connect();
    wsClientRef.current = ws;
  };

  // 3. Quick Answer Trigger ("Ce să răspund?") with Speculative 0ms Cache
  const handleTriggerQuickAnswer = async (preferredMode = null) => {
    if (!session) return;

    // Instant Zero-Latency path if pre-computed in background
    if (precomputedAnswer) {
      setCurrentAnswer({
        ...precomputedAnswer,
        initialMode: preferredMode || 'agree'
      });
      setRecentAlert(null);
      return;
    }

    setIsLoadingAnswer(true);

    try {
      const answer = await api.getQuickAnswer(session.id, settings.targetName);
      setCurrentAnswer({
        ...answer,
        initialMode: preferredMode || 'agree'
      });
      // Dismiss any active alert banner once answered
      setRecentAlert(null);
    } catch (err) {
      alert(`Eroare la generarea răspunsului: ${err.message}`);
    } finally {
      setIsLoadingAnswer(false);
    }
  };

  // 4. Manually add transcript or from speech recognition
  const handleAddTranscript = async (text, speaker = 'Participant', isFinal = true) => {
    if (!session) return;
    // Send via WebSocket if connected (instant broadcast)
    if (wsClientRef.current && connectionStatus === 'connected') {
      wsClientRef.current.sendTranscript(text, speaker, isFinal);
    } else {
      // Fallback via HTTP REST
      try {
        const saved = await api.addTranscript(session.id, text, speaker, isFinal);
        setTranscripts(prev => [...prev, saved]);
        if (saved.mentions_target) {
          setRecentAlert({ speaker, text });
          AudioService.vibrateAlert();
        }
      } catch (err) {
        console.error('Failed to add transcript:', err);
      }
    }
  };

  // 5. End Meeting
  const handleEndMeeting = async () => {
    if (!session) return;
    if (!window.confirm('Sigur doriți să finalizați această ședință? Va fi salvată în istoric.')) return;

    try {
      await api.updateSession(session.id, { status: 'completed' });
      if (wsClientRef.current) wsClientRef.current.disconnect();
      // Generate summary
      api.summarizeSession(session.id).catch(console.warn);
      
      // Start a fresh session for next time
      const fresh = await api.createSession(`Ședință Nouă — ${new Date().toLocaleTimeString('ro-RO', { hour: '2-digit', minute: '2-digit' })}`);
      setSession(fresh);
      setTranscripts([]);
      connectWebSocket(fresh.id);
      setActiveTab('history');
    } catch (err) {
      alert(`Eroare la încheierea ședinței: ${err.message}`);
    }
  };

  // 6. Save Settings
  const handleSaveSettings = (newSettings) => {
    setSettings(newSettings);
    localStorage.setItem('olivia_target_name', newSettings.targetName);
    localStorage.setItem('olivia_gemini_key', newSettings.geminiKey);
    localStorage.setItem('olivia_deepgram_key', newSettings.deepgramKey);
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      
      {/* App Header */}
      <Header
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        connectionStatus={connectionStatus}
        targetName={settings.targetName}
        onOpenSettings={() => setShowSettings(true)}
      />

      {/* Main Body */}
      <main className="flex-1 overflow-hidden">
        {activeTab === 'copilot' ? (
          <FloatingCopilot
            session={session}
            transcripts={transcripts}
            onTriggerQuickAnswer={handleTriggerQuickAnswer}
            isLoadingAnswer={isLoadingAnswer}
            recentAlert={recentAlert}
            onDismissAlert={() => setRecentAlert(null)}
            onAddManualTranscript={handleAddTranscript}
            onEndMeeting={handleEndMeeting}
            targetName={settings.targetName}
            onOpenSimulator={() => setShowSimulator(true)}
          />
        ) : (
          <SessionHistory
            onResumeSession={(s) => {
              setSession(s);
              connectWebSocket(s.id);
              setActiveTab('copilot');
            }}
            targetName={settings.targetName}
          />
        )}
      </main>

      {/* Answer Modal ("Ce să răspunzi") */}
      {currentAnswer && (
        <AnswerModal
          answer={currentAnswer}
          onClose={() => setCurrentAnswer(null)}
          onFeedback={(ansId, feedback) => {
            api.submitAnswerFeedback(ansId, feedback).catch(console.warn);
          }}
        />
      )}

      {/* Meeting Dialogue Simulator Modal */}
      {showSimulator && (
        <MeetingSimulator
          targetName={settings.targetName}
          onInjectTranscript={handleAddTranscript}
          onClose={() => setShowSimulator(false)}
        />
      )}

      {/* Settings Modal */}
      {showSettings && (
        <SettingsModal
          currentSettings={settings}
          onSaveSettings={handleSaveSettings}
          onClose={() => setShowSettings(false)}
        />
      )}

    </div>
  );
}
