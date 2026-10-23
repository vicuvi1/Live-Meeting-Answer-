import React, { useState, useEffect, useRef } from 'react';
import { 
  Mic, 
  MicOff, 
  Sparkles, 
  AlertTriangle, 
  Volume2, 
  Play, 
  Square, 
  Send,
  Radio,
  Copy,
  Check,
  CheckCircle2,
  Clock,
  AlertCircle
} from 'lucide-react';
import { AudioService } from '../services/audioCapture';

export default function FloatingCopilot({
  session,
  transcripts,
  onTriggerQuickAnswer,
  isLoadingAnswer,
  recentAlert,
  onDismissAlert,
  onAddManualTranscript,
  onEndMeeting,
  targetName = 'User',
  onOpenSimulator,
  fontSizeMode = 'normal',
  precomputedAnswer
}) {
  const [isMicActive, setIsMicActive] = useState(false);
  const [manualText, setManualText] = useState('');
  const [manualSpeaker, setManualSpeaker] = useState('Coleg');
  const [selectedTacticalMode, setSelectedTacticalMode] = useState('agree');
  const [copiedInline, setCopiedInline] = useState(false);
  const [isPlayingAudio, setIsPlayingAudio] = useState(false);
  const [micNotice, setMicNotice] = useState(null);

  const scrollRef = useRef(null);
  const audioServiceRef = useRef(null);

  // Auto-scroll to latest transcript
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [transcripts]);

  // Cleanup microphone on tab switch or unmount
  useEffect(() => {
    return () => {
      if (audioServiceRef.current) {
        audioServiceRef.current.stopAll();
      }
    };
  }, []);

  // Audio Service Setup for Romanian live speech
  const toggleMicrophone = () => {
    if (isMicActive) {
      if (audioServiceRef.current) {
        audioServiceRef.current.stopAll();
      }
      setIsMicActive(false);
      setMicNotice(null);
    } else {
      const service = new AudioService(
        (text, isFinal) => {
          if (isFinal && text.trim()) {
            onAddManualTranscript(text, 'Voce Cameră', true);
          }
        },
        (err) => {
          setMicNotice(err);
          setTimeout(() => setMicNotice(null), 4500);
          setIsMicActive(false);
        }
      );
      const success = service.startWebSpeechRecognition();
      if (success) {
        audioServiceRef.current = service;
        setIsMicActive(true);
        setMicNotice(null);
      }
    }
  };

  const handleManualSubmit = (e) => {
    e.preventDefault();
    if (!manualText.trim()) return;
    onAddManualTranscript(manualText.trim(), manualSpeaker, true);
    setManualText('');
  };

  // Font sizing helper classes
  const fontClass = fontSizeMode === 'huge' ? 'text-lg md:text-xl' : fontSizeMode === 'large' ? 'text-base md:text-lg' : 'text-sm md:text-base';
  const responseFontClass = fontSizeMode === 'huge' ? 'text-xl md:text-2xl' : fontSizeMode === 'large' ? 'text-lg md:text-xl' : 'text-base md:text-lg';

  // Active Tactical Card data
  const tactical = precomputedAnswer?.tactical_responses || {
    agree: {
      label: 'Acord / Da',
      spoken_response: 'Sunt de acord cu propunerea discutată și susțin direcția propusă de echipă.',
      talking_points: ['Aliniere cu grupul', 'Disponibilitate pentru implementare']
    },
    stall: {
      label: 'Cumpără Timp (Scuză Mute)',
      spoken_response: 'Vă rog să mă scuzați, a durat o secundă să găsesc butonul de unmute. Sunt aliniată cu ideea, trimit confirmarea detaliată pe mail imediat după ședință.',
      talking_points: ['Câștigi 15 secunde fără stres', 'Mută detaliile pe e-mail']
    },
    caution: {
      label: 'Prudență / Amânare',
      spoken_response: 'Avem nevoie de cifrele finale de la financiar înainte să fixăm o decizie fermă; revin cu confirmarea după ce verific datele.',
      talking_points: ['Evită angajamente premature', 'Păstrează controlul deciziei']
    }
  };

  const currentInlineCard = tactical[selectedTacticalMode] || tactical.agree;

  const handleInlineCopy = () => {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(currentInlineCard.spoken_response);
      }
    } catch (e) {
      console.warn('Clipboard write error:', e);
    }
    setCopiedInline(true);
    setTimeout(() => setCopiedInline(false), 2000);
  };

  const handleInlineSpeak = () => {
    setIsPlayingAudio(true);
    AudioService.speakRomanian(currentInlineCard.spoken_response);
    setTimeout(() => setIsPlayingAudio(false), 3000);
  };

  return (
    <div className="flex flex-col h-[calc(100vh-3.8rem)] max-w-4xl mx-auto p-2.5 sm:p-4 pb-4">
      
      {/* Top Session & Audio Activity Bar */}
      <div className="flex items-center justify-between bg-slate-900/90 border border-slate-800 rounded-2xl p-2.5 sm:p-3 mb-2.5 shadow-lg">
        <div className="flex items-center space-x-2.5">
          {/* Active Audio Waveform Equalizer */}
          <div className="flex items-end space-x-0.5 h-5 px-1.5 py-0.5 bg-slate-950 rounded-lg border border-slate-800">
            <span className={`w-1 rounded-full bg-emerald-400 ${isMicActive ? 'wave-bar-1' : 'h-1.5'}`} />
            <span className={`w-1 rounded-full bg-emerald-400 ${isMicActive ? 'wave-bar-2' : 'h-2'}`} />
            <span className={`w-1 rounded-full bg-emerald-400 ${isMicActive ? 'wave-bar-3' : 'h-1.5'}`} />
            <span className={`w-1 rounded-full bg-emerald-400 ${isMicActive ? 'wave-bar-4' : 'h-2.5'}`} />
          </div>

          <div>
            <h2 className="font-bold text-xs sm:text-sm text-white truncate max-w-[180px] sm:max-w-xs leading-tight">
              {session?.title || 'Ședință în Curs'}
            </h2>
            <p className="text-[10px] text-slate-400">
              {isMicActive ? 'Microfon activ • Ascultare în limba română' : 'Microfon oprit • Apasă pe microfon mai jos'}
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-1.5">
          {/* Simulator button */}
          <button
            onClick={onOpenSimulator}
            className="flex items-center space-x-1 px-2.5 py-1.5 rounded-xl bg-purple-950/70 hover:bg-purple-900/90 text-purple-300 border border-purple-500/30 text-xs font-semibold transition"
          >
            <Play className="w-3 h-3" />
            <span className="hidden sm:inline">Simulează</span>
          </button>

          {/* End meeting button */}
          <button
            onClick={onEndMeeting}
            className="flex items-center space-x-1 px-2.5 py-1.5 rounded-xl bg-rose-950/70 hover:bg-rose-900/90 text-rose-300 border border-rose-500/30 text-xs font-semibold transition"
          >
            <Square className="w-3 h-3" />
            <span>Încheie</span>
          </button>
        </div>
      </div>

      {/* Mic Warning Notice if microphone is blocked */}
      {micNotice && (
        <div className="mb-2.5 p-2.5 rounded-xl bg-amber-950/80 border border-amber-500/50 text-amber-200 text-xs flex items-center justify-between animate-in fade-in">
          <span>⚠️ {micNotice}</span>
          <button onClick={() => setMicNotice(null)} className="text-amber-400 font-bold px-1.5 hover:text-amber-200">✕</button>
        </div>
      )}

      {/* Target Alert Banner (Pulsing when target is called) */}
      {recentAlert && (
        <div className="mb-2.5 bg-gradient-to-r from-amber-950/90 via-rose-950/90 to-amber-950/90 border-2 border-amber-400 rounded-2xl p-3 sm:p-4 shadow-xl animate-alert-glow flex items-center justify-between">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 rounded-xl bg-amber-500/20 text-amber-300 border border-amber-400/40 animate-bounce">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <h4 className="font-bold text-xs sm:text-sm text-amber-200">
                Atenție! {targetName} a primit o întrebare!
              </h4>
              <p className="text-[11px] text-amber-300/90 italic line-clamp-1">
                "{recentAlert.text}"
              </p>
            </div>
          </div>
          
          <button
            onClick={onDismissAlert}
            className="px-2.5 py-1 rounded-lg bg-amber-900/70 hover:bg-amber-800 text-amber-200 text-xs font-semibold"
          >
            Am văzut
          </button>
        </div>
      )}

      {/* Main Live Captions Viewport */}
      <div className="flex-1 bg-slate-900/70 border border-slate-800 rounded-3xl p-3.5 sm:p-4 overflow-hidden flex flex-col shadow-inner relative min-h-[140px]">
        
        {/* Subtle Watermark */}
        <div className="absolute top-2.5 right-3.5 text-[9px] uppercase font-bold tracking-widest text-slate-700 pointer-events-none select-none">
          Live Romanian Subtitles (ro-RO)
        </div>

        {/* Scrollable Transcripts */}
        <div ref={scrollRef} className="flex-1 overflow-y-auto space-y-2.5 pr-1 scroll-smooth">
          {transcripts.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-500">
              <Radio className="w-8 h-8 mb-2 text-slate-600 animate-pulse" />
              <p className="font-medium text-slate-300 text-sm mb-1">
                Așteptare conversație din ședință...
              </p>
              <p className="text-xs text-slate-500 max-w-sm">
                Porniți microfonul de mai jos sau folosiți simulatorul pentru a testa detectarea cuvintelor și răspunsurile AI.
              </p>
            </div>
          ) : (
            transcripts.map((t, idx) => {
              const isTargetMention = t.mentions_target;
              return (
                <div 
                  key={t.id || idx}
                  className={`p-3 rounded-2xl transition-all ${
                    isTargetMention
                      ? 'bg-amber-950/45 border-2 border-amber-500/70 shadow-lg shadow-amber-500/10'
                      : 'bg-slate-950/60 border border-slate-800/80'
                  }`}
                >
                  <div className="flex items-center justify-between mb-0.5">
                    <span className={`text-[11px] font-bold ${isTargetMention ? 'text-amber-400' : 'text-slate-400'}`}>
                      {isTargetMention && '⚡ '}{t.speaker || 'Participant'}
                    </span>
                    <span className="text-[10px] text-slate-500">
                      {t.timestamp ? (typeof t.timestamp === 'string' ? t.timestamp.split('T')[1]?.substring(0, 8) || t.timestamp : '') : ''}
                    </span>
                  </div>
                  <p className={`${fontClass} leading-relaxed ${isTargetMention ? 'text-amber-100 font-semibold' : 'text-slate-200'}`}>
                    {t.text}
                  </p>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* FULL-SCREEN INLINE TACTICAL CARDS (Senior-friendly, always ready) */}
      <div className="mt-2.5 bg-slate-900/90 border border-slate-800 rounded-2xl p-3 shadow-lg">
        
        {/* 3 Tactical Mode Selectors */}
        <div className="grid grid-cols-3 gap-1.5 mb-2.5">
          <button
            onClick={() => setSelectedTacticalMode('agree')}
            className={`btn-tactile p-2 rounded-xl border text-left flex items-center space-x-1.5 transition ${
              selectedTacticalMode === 'agree'
                ? 'bg-emerald-950/80 border-emerald-500 text-emerald-200 shadow-md shadow-emerald-500/20'
                : 'bg-slate-950/50 border-slate-800 text-slate-400'
            }`}
          >
            <CheckCircle2 className={`w-3.5 h-3.5 shrink-0 ${selectedTacticalMode === 'agree' ? 'text-emerald-400' : 'text-slate-500'}`} />
            <div className="truncate">
              <span className="text-[11px] font-bold block leading-tight">1. Da / Acord</span>
              <span className="text-[9px] opacity-75 hidden sm:block">Susține propunerea</span>
            </div>
          </button>

          <button
            onClick={() => setSelectedTacticalMode('stall')}
            className={`btn-tactile p-2 rounded-xl border text-left flex items-center space-x-1.5 transition ${
              selectedTacticalMode === 'stall'
                ? 'bg-amber-950/80 border-amber-500 text-amber-200 shadow-md shadow-amber-500/20'
                : 'bg-slate-950/50 border-slate-800 text-slate-400'
            }`}
          >
            <Clock className={`w-3.5 h-3.5 shrink-0 ${selectedTacticalMode === 'stall' ? 'text-amber-400' : 'text-slate-500'}`} />
            <div className="truncate">
              <span className="text-[11px] font-bold block leading-tight">2. Scuză Mute</span>
              <span className="text-[9px] opacity-75 hidden sm:block">Cumpără 15 secunde</span>
            </div>
          </button>

          <button
            onClick={() => setSelectedTacticalMode('caution')}
            className={`btn-tactile p-2 rounded-xl border text-left flex items-center space-x-1.5 transition ${
              selectedTacticalMode === 'caution'
                ? 'bg-rose-950/80 border-rose-500 text-rose-200 shadow-md shadow-rose-500/20'
                : 'bg-slate-950/50 border-slate-800 text-slate-400'
            }`}
          >
            <AlertCircle className={`w-3.5 h-3.5 shrink-0 ${selectedTacticalMode === 'caution' ? 'text-rose-400' : 'text-slate-500'}`} />
            <div className="truncate">
              <span className="text-[11px] font-bold block leading-tight">3. Prudență</span>
              <span className="text-[9px] opacity-75 hidden sm:block">Amână pe mail</span>
            </div>
          </button>
        </div>

        {/* The Exact Spoken Phrase (Huge text for Mom) */}
        <div className={`p-3 rounded-xl border transition-all ${
          selectedTacticalMode === 'agree'
            ? 'bg-emerald-950/40 border-emerald-500/50'
            : selectedTacticalMode === 'stall'
            ? 'bg-amber-950/40 border-amber-500/50'
            : 'bg-rose-950/40 border-rose-500/50'
        }`}>
          <div className="flex items-center justify-between mb-1">
            <span className={`text-[10px] font-bold uppercase tracking-wider ${
              selectedTacticalMode === 'agree' ? 'text-emerald-400' : selectedTacticalMode === 'stall' ? 'text-amber-400' : 'text-rose-400'
            }`}>
              📢 Replica ta de rostit cu voce tare:
            </span>

            {/* Quick Actions inside the box */}
            <div className="flex items-center space-x-1.5">
              <button
                onClick={handleInlineCopy}
                className="px-2 py-0.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-[10px] font-bold border border-slate-700 flex items-center space-x-1"
                title="Copiază"
              >
                {copiedInline ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                <span>{copiedInline ? 'Copiat!' : 'Copiază'}</span>
              </button>

              <button
                onClick={handleInlineSpeak}
                className={`px-2 py-0.5 rounded-lg bg-teal-900/80 hover:bg-teal-800 text-teal-200 text-[10px] font-bold border border-teal-600/40 flex items-center space-x-1 ${isPlayingAudio ? 'animate-pulse' : ''}`}
                title="Ascultă în cască"
              >
                <Volume2 className="w-3 h-3" />
                <span>{isPlayingAudio ? 'Redare...' : 'Ascultă'}</span>
              </button>
            </div>
          </div>

          <p className={`${responseFontClass} font-bold text-white leading-relaxed select-all`}>
            "{currentInlineCard.spoken_response}"
          </p>
        </div>

      </div>

      {/* Manual Quick Input Toolbar */}
      <form onSubmit={handleManualSubmit} className="mt-2 flex items-center space-x-2">
        <select 
          value={manualSpeaker} 
          onChange={(e) => setManualSpeaker(e.target.value)}
          className="bg-slate-900 border border-slate-800 text-[11px] rounded-xl px-2 py-1.5 text-slate-300 focus:outline-none focus:border-emerald-500"
        >
          <option value="Coleg">Coleg</option>
          <option value="Director">Director</option>
          <option value="Radu">Radu</option>
          <option value="Elena">Elena</option>
          <option value={targetName}>{targetName}</option>
        </select>
        <input
          type="text"
          value={manualText}
          onChange={(e) => setManualText(e.target.value)}
          placeholder={`Tastează o replică... (ex: '${targetName}, ce părere ai?')`}
          className="flex-1 bg-slate-900 border border-slate-800 text-xs rounded-xl px-3 py-1.5 text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
        />
        <button
          type="submit"
          className="p-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 transition"
          title="Trimite"
        >
          <Send className="w-3.5 h-3.5" />
        </button>
      </form>

      {/* BOTTOM ACTION BAR */}
      <div className="mt-2 grid grid-cols-4 gap-2">
        
        {/* Toggle Live Microphone */}
        <button
          onClick={toggleMicrophone}
          className={`btn-tactile col-span-1 flex flex-col items-center justify-center p-2.5 rounded-2xl border transition-all ${
            isMicActive
              ? 'bg-rose-600/90 border-rose-500 text-white shadow-lg shadow-rose-600/30'
              : 'bg-slate-900 hover:bg-slate-800 border-slate-800 text-slate-300'
          }`}
        >
          {isMicActive ? <Mic className="w-5 h-5 animate-pulse" /> : <MicOff className="w-5 h-5" />}
          <span className="text-[10px] font-bold mt-0.5">
            {isMicActive ? 'Ascultă' : 'Pornire Mic'}
          </span>
        </button>

        {/* THE BIG "CE SĂ RĂSPUND?" BUTTON */}
        <button
          onClick={() => onTriggerQuickAnswer(selectedTacticalMode)}
          disabled={isLoadingAnswer}
          className={`btn-tactile col-span-3 flex items-center justify-center space-x-2.5 p-3 sm:p-3.5 rounded-2xl font-bold text-sm sm:text-base transition-all shadow-xl ${
            recentAlert
              ? 'bg-gradient-to-r from-amber-500 via-emerald-500 to-teal-500 text-slate-950 animate-alert-glow shadow-emerald-500/40'
              : 'bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white shadow-emerald-600/30'
          } ${isLoadingAnswer ? 'opacity-80 cursor-wait' : ''}`}
        >
          <Sparkles className={`w-5 h-5 ${isLoadingAnswer ? 'animate-spin' : ''}`} />
          <div className="text-left">
            <span className="block leading-tight">
              {isLoadingAnswer ? 'Generez răspunsul...' : '💡 Deschide Fereastra de Răspuns'}
            </span>
            <span className={`text-[10px] font-normal block opacity-90 ${recentAlert ? 'text-slate-900 font-semibold' : 'text-emerald-100'}`}>
              Apasă când primești o întrebare în ședință
            </span>
          </div>
        </button>

      </div>

    </div>
  );
}
