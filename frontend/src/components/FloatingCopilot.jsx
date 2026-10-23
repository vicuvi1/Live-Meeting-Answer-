import React, { useState, useEffect, useRef } from 'react';
import { 
  Mic, 
  MicOff, 
  Sparkles, 
  AlertTriangle, 
  Volume2, 
  Play, 
  Square, 
  CheckCircle2, 
  Radio, 
  Send,
  HelpCircle
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
  targetName = 'Olivia',
  onOpenSimulator
}) {
  const [isMicActive, setIsMicActive] = useState(false);
  const [manualText, setManualText] = useState('');
  const [manualSpeaker, setManualSpeaker] = useState('Coleg');
  const scrollRef = useRef(null);
  const audioServiceRef = useRef(null);

  // Auto-scroll to latest transcript
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [transcripts]);

  // Audio Service Setup for Romanian live speech
  const toggleMicrophone = () => {
    if (isMicActive) {
      if (audioServiceRef.current) {
        audioServiceRef.current.stopAll();
      }
      setIsMicActive(false);
    } else {
      const service = new AudioService(
        (text, isFinal) => {
          if (isFinal && text.trim()) {
            onAddManualTranscript(text, 'Voce Cameră', true);
          }
        },
        (err) => {
          alert(`Eroare microfon: ${err}`);
          setIsMicActive(false);
        }
      );
      const success = service.startWebSpeechRecognition();
      if (success) {
        audioServiceRef.current = service;
        setIsMicActive(true);
      }
    }
  };

  const handleManualSubmit = (e) => {
    e.preventDefault();
    if (!manualText.trim()) return;
    onAddManualTranscript(manualText.trim(), manualSpeaker, true);
    setManualText('');
  };

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)] max-w-4xl mx-auto p-3 sm:p-4 pb-6">
      
      {/* Top Session Bar */}
      <div className="flex items-center justify-between bg-slate-900/90 border border-slate-800 rounded-2xl p-3 mb-3 shadow-lg">
        <div className="flex items-center space-x-2.5">
          <span className="w-3 h-3 rounded-full bg-emerald-500 animate-pulse" />
          <div>
            <h2 className="font-bold text-sm md:text-base text-white truncate max-w-[200px] sm:max-w-xs">
              {session?.title || 'Ședință în Curs'}
            </h2>
            <p className="text-xs text-slate-400">
              {transcripts.length} replici înregistrate în direct
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-2">
          {/* Simulator button */}
          <button
            onClick={onOpenSimulator}
            className="flex items-center space-x-1 px-3 py-1.5 rounded-xl bg-purple-950/60 hover:bg-purple-900/80 text-purple-300 border border-purple-500/30 text-xs font-semibold transition"
          >
            <Play className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Simulează Dialog</span>
          </button>

          {/* End meeting button */}
          <button
            onClick={onEndMeeting}
            className="flex items-center space-x-1 px-3 py-1.5 rounded-xl bg-rose-950/60 hover:bg-rose-900/80 text-rose-300 border border-rose-500/30 text-xs font-semibold transition"
          >
            <Square className="w-3.5 h-3.5" />
            <span>Încheie</span>
          </button>
        </div>
      </div>

      {/* Target Alert Banner (Pulsing when Olivia is called) */}
      {recentAlert && (
        <div className="mb-3 bg-gradient-to-r from-amber-950/90 via-rose-950/90 to-amber-950/90 border-2 border-amber-400 rounded-2xl p-4 shadow-xl animate-alert-glow flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-amber-500/20 text-amber-300 border border-amber-400/40 animate-bounce">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <div>
              <h4 className="font-bold text-sm sm:text-base text-amber-200">
                Atenție! {targetName} a fost solicitată!
              </h4>
              <p className="text-xs text-amber-300/90 line-clamp-1 italic">
                "{recentAlert.text}"
              </p>
            </div>
          </div>
          
          <button
            onClick={onDismissAlert}
            className="px-3 py-1 rounded-lg bg-amber-900/60 hover:bg-amber-800 text-amber-200 text-xs font-semibold"
          >
            Am văzut
          </button>
        </div>
      )}

      {/* Main Live Captions Viewport */}
      <div className="flex-1 bg-slate-900/70 border border-slate-800 rounded-3xl p-4 overflow-hidden flex flex-col shadow-inner relative">
        
        {/* Subtle Watermark */}
        <div className="absolute top-3 right-4 text-[10px] uppercase font-bold tracking-widest text-slate-700 pointer-events-none select-none">
          Live Romanian Captions (ro-RO)
        </div>

        {/* Scrollable Transcripts */}
        <div ref={scrollRef} className="flex-1 overflow-y-auto space-y-3 pr-1 scroll-smooth">
          {transcripts.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-500">
              <Radio className="w-10 h-10 mb-3 text-slate-600 animate-pulse" />
              <p className="font-medium text-slate-300 text-sm md:text-base mb-1">
                Așteptare conversație din ședință...
              </p>
              <p className="text-xs text-slate-500 max-w-sm">
                Activați microfonul de mai jos sau folosiți simulatorul pentru a testa detectarea cuvintelor și răspunsul AI.
              </p>
            </div>
          ) : (
            transcripts.map((t, idx) => {
              const isTargetMention = t.mentions_target;
              return (
                <div 
                  key={t.id || idx}
                  className={`p-3.5 rounded-2xl transition-all ${
                    isTargetMention
                      ? 'bg-amber-950/40 border-2 border-amber-500/60 shadow-lg shadow-amber-500/10'
                      : 'bg-slate-950/60 border border-slate-800/80'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className={`text-xs font-bold ${isTargetMention ? 'text-amber-400' : 'text-slate-400'}`}>
                      {isTargetMention && '⚡ '}{t.speaker || 'Participant'}
                    </span>
                    <span className="text-[11px] text-slate-500">
                      {t.timestamp ? (typeof t.timestamp === 'string' ? t.timestamp.split('T')[1]?.substring(0, 8) || t.timestamp : '') : ''}
                    </span>
                  </div>
                  <p className={`text-sm md:text-base leading-relaxed ${isTargetMention ? 'text-amber-100 font-semibold' : 'text-slate-200'}`}>
                    {t.text}
                  </p>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* Manual Quick Add Toolbar (for typing or testing) */}
      <form onSubmit={handleManualSubmit} className="mt-2.5 flex items-center space-x-2">
        <select 
          value={manualSpeaker} 
          onChange={(e) => setManualSpeaker(e.target.value)}
          className="bg-slate-900 border border-slate-800 text-xs rounded-xl px-2 py-2 text-slate-300 focus:outline-none focus:border-emerald-500"
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
          className="flex-1 bg-slate-900 border border-slate-800 text-xs md:text-sm rounded-xl px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
        />
        <button
          type="submit"
          className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 transition"
          title="Trimite replică"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>

      {/* TACTICAL 3-CARD QUICK DOCK */}
      <div className="mt-2.5 grid grid-cols-3 gap-2">
        <button
          onClick={() => onTriggerQuickAnswer('agree')}
          className="btn-tactile p-2 rounded-xl bg-slate-900/90 hover:bg-emerald-950/60 border border-emerald-500/40 hover:border-emerald-500 text-left flex items-center space-x-2 transition shadow-sm"
        >
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 shrink-0" />
          <div className="truncate">
            <span className="text-[11px] font-bold text-emerald-300 block leading-tight">1. Da / Acord</span>
            <span className="text-[9px] text-slate-400 block truncate">Susține propunerea</span>
          </div>
        </button>

        <button
          onClick={() => onTriggerQuickAnswer('stall')}
          className="btn-tactile p-2 rounded-xl bg-slate-900/90 hover:bg-amber-950/60 border border-amber-500/40 hover:border-amber-500 text-left flex items-center space-x-2 transition shadow-sm"
        >
          <span className="w-2.5 h-2.5 rounded-full bg-amber-400 shrink-0" />
          <div className="truncate">
            <span className="text-[11px] font-bold text-amber-300 block leading-tight">2. Scuză Mute</span>
            <span className="text-[9px] text-slate-400 block truncate">Cumpără 15s</span>
          </div>
        </button>

        <button
          onClick={() => onTriggerQuickAnswer('caution')}
          className="btn-tactile p-2 rounded-xl bg-slate-900/90 hover:bg-rose-950/60 border border-rose-500/40 hover:border-rose-500 text-left flex items-center space-x-2 transition shadow-sm"
        >
          <span className="w-2.5 h-2.5 rounded-full bg-rose-400 shrink-0" />
          <div className="truncate">
            <span className="text-[11px] font-bold text-rose-300 block leading-tight">3. Prudență</span>
            <span className="text-[9px] text-slate-400 block truncate">Verific și revin</span>
          </div>
        </button>
      </div>

      {/* THE LIFESAVER SECTION (Tactile & Prominent for Olivia) */}
      <div className="mt-2.5 grid grid-cols-4 gap-2.5">
        
        {/* Toggle Live Microphone */}
        <button
          onClick={toggleMicrophone}
          className={`btn-tactile col-span-1 flex flex-col items-center justify-center p-3 rounded-2xl border transition-all ${
            isMicActive
              ? 'bg-rose-600/90 border-rose-500 text-white shadow-lg shadow-rose-600/30'
              : 'bg-slate-900 hover:bg-slate-800 border-slate-800 text-slate-300'
          }`}
        >
          {isMicActive ? <Mic className="w-6 h-6 animate-pulse" /> : <MicOff className="w-6 h-6" />}
          <span className="text-[11px] font-bold mt-1">
            {isMicActive ? 'Ascultă' : 'Pornire Mic'}
          </span>
        </button>

        {/* THE BIG "CE SĂ RĂSPUND?" BUTTON */}
        <button
          onClick={onTriggerQuickAnswer}
          disabled={isLoadingAnswer}
          className={`btn-tactile col-span-3 flex items-center justify-center space-x-3 p-4 rounded-2xl font-bold text-base md:text-lg transition-all shadow-xl ${
            recentAlert
              ? 'bg-gradient-to-r from-amber-500 via-emerald-500 to-teal-500 text-slate-950 animate-alert-glow shadow-emerald-500/40'
              : 'bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white shadow-emerald-600/30'
          } ${isLoadingAnswer ? 'opacity-80 cursor-wait' : ''}`}
        >
          <Sparkles className={`w-6 h-6 ${isLoadingAnswer ? 'animate-spin' : ''}`} />
          <div className="text-left">
            <span className="block leading-tight">
              {isLoadingAnswer ? 'Generez răspunsul...' : '💡 Ce să răspund acum?'}
            </span>
            <span className={`text-[11px] font-normal block opacity-90 ${recentAlert ? 'text-slate-900 font-semibold' : 'text-emerald-100'}`}>
              Apasă când ești întrebată ceva în ședință
            </span>
          </div>
        </button>

      </div>

    </div>
  );
}
