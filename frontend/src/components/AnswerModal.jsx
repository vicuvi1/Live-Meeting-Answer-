import React, { useState } from 'react';
import { 
  X, 
  Copy, 
  Check, 
  Volume2, 
  Sparkles, 
  MessageSquare, 
  ThumbsUp, 
  ThumbsDown,
  CheckCircle2,
  Clock,
  AlertCircle
} from 'lucide-react';
import { AudioService } from '../services/audioCapture';

export default function AnswerModal({ answer, onClose, onFeedback }) {
  const [copied, setCopied] = useState(false);
  const [isPlayingAudio, setIsPlayingAudio] = useState(false);
  
  // Tactical mode: 'agree' | 'stall' | 'caution'
  const [selectedMode, setSelectedMode] = useState(answer?.initialMode || 'agree');

  if (!answer) return null;

  // Extract tactical cards if available, fallback gracefully
  const tactical = answer.tactical_responses || {
    agree: {
      label: 'Acord / Da',
      spoken_response: answer.spoken_response || 'Sunt de acord cu planul prezentat și putem merge mai departe.',
      talking_points: answer.talking_points || ['Aliniere cu echipa']
    },
    stall: {
      label: 'Cumpără Timp (Scuză Mute)',
      spoken_response: 'Vă rog să mă scuzați, a durat o secundă să găsesc butonul de unmute. Sunt aliniată cu ideea, trimit confirmarea scrisă pe mail imediat după ședință.',
      talking_points: ['Câștigă 15 secunde de respiro', 'Mută detaliile pe e-mail']
    },
    caution: {
      label: 'Prudență / Amânare',
      spoken_response: 'Avem nevoie de cifrele finale de la financiar înainte să fixăm o decizie fermă; revin cu confirmarea după ce verific datele.',
      talking_points: ['Evită angajamente premature', 'Păstrează controlul deciziei']
    }
  };

  const currentCard = tactical[selectedMode] || tactical.agree;

  const handleCopy = () => {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(currentCard.spoken_response);
      }
    } catch (e) {
      console.warn('Clipboard write error:', e);
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleSpeak = () => {
    setIsPlayingAudio(true);
    AudioService.speakRomanian(currentCard.spoken_response);
    setTimeout(() => setIsPlayingAudio(false), 3000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-slate-950/85 backdrop-blur-md animate-in fade-in duration-200">
      <div className="relative w-full max-w-xl bg-slate-900 border-2 border-emerald-500/80 rounded-3xl p-5 sm:p-6 shadow-2xl shadow-emerald-500/20 overflow-hidden">
        
        {/* Glow accent */}
        <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-emerald-500 via-amber-400 to-teal-500" />

        {/* Header */}
        <div className="flex items-start justify-between mb-3">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 rounded-xl bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-base sm:text-lg text-white">Răspunsuri Tactice Disponibile</h3>
              <p className="text-[11px] text-slate-400">Selectează direcția potrivită în {answer.latency_ms || 260}ms</p>
            </div>
          </div>
          
          <button
            onClick={onClose}
            className="p-1.5 rounded-xl bg-slate-800 text-slate-400 hover:text-white hover:bg-slate-700 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Question Identified */}
        <div className="mb-4 bg-slate-950/70 border border-slate-800 rounded-2xl p-3">
          <div className="flex items-center space-x-1.5 text-[11px] font-bold text-amber-400 mb-0.5">
            <MessageSquare className="w-3.5 h-3.5" />
            <span>ÎNTREBARE ADRESATĂ:</span>
          </div>
          <p className="text-xs sm:text-sm font-medium text-slate-200 italic line-clamp-2">
            "{answer.question_detected || 'Ai fost întrebată despre desfășurarea activităților.'}"
          </p>
        </div>

        {/* The 3 Tactical Selector Tabs */}
        <div className="grid grid-cols-3 gap-2 mb-4">
          
          {/* Card 1: Agree */}
          <button
            onClick={() => setSelectedMode('agree')}
            className={`btn-tactile p-2.5 rounded-2xl border flex flex-col items-center text-center transition ${
              selectedMode === 'agree'
                ? 'bg-emerald-950/80 border-emerald-500 text-emerald-200 shadow-md shadow-emerald-500/20'
                : 'bg-slate-950/50 border-slate-800/80 text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
            }`}
          >
            <CheckCircle2 className={`w-4 h-4 mb-1 ${selectedMode === 'agree' ? 'text-emerald-400' : 'text-slate-500'}`} />
            <span className="text-xs font-bold block">1. Da / Acord</span>
            <span className="text-[10px] opacity-75 hidden sm:block">Susține planul</span>
          </button>

          {/* Card 2: Stall (Buy Time) */}
          <button
            onClick={() => setSelectedMode('stall')}
            className={`btn-tactile p-2.5 rounded-2xl border flex flex-col items-center text-center transition ${
              selectedMode === 'stall'
                ? 'bg-amber-950/80 border-amber-500 text-amber-200 shadow-md shadow-amber-500/20'
                : 'bg-slate-950/50 border-slate-800/80 text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
            }`}
          >
            <Clock className={`w-4 h-4 mb-1 ${selectedMode === 'stall' ? 'text-amber-400' : 'text-slate-500'}`} />
            <span className="text-xs font-bold block">2. Scuză Mute</span>
            <span className="text-[10px] opacity-75 hidden sm:block">Cumpără 15s</span>
          </button>

          {/* Card 3: Caution */}
          <button
            onClick={() => setSelectedMode('caution')}
            className={`btn-tactile p-2.5 rounded-2xl border flex flex-col items-center text-center transition ${
              selectedMode === 'caution'
                ? 'bg-rose-950/80 border-rose-500 text-rose-200 shadow-md shadow-rose-500/20'
                : 'bg-slate-950/50 border-slate-800/80 text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
            }`}
          >
            <AlertCircle className={`w-4 h-4 mb-1 ${selectedMode === 'caution' ? 'text-rose-400' : 'text-slate-500'}`} />
            <span className="text-xs font-bold block">3. Prudență</span>
            <span className="text-[10px] opacity-75 hidden sm:block">Amână pe mail</span>
          </button>

        </div>

        {/* The Exact Spoken Response - Senior-Friendly Large Text */}
        <div className={`mb-4 rounded-2xl p-4 sm:p-5 border-2 shadow-inner transition-all ${
          selectedMode === 'agree'
            ? 'bg-gradient-to-b from-emerald-950/50 to-slate-950 border-emerald-500/60'
            : selectedMode === 'stall'
            ? 'bg-gradient-to-b from-amber-950/50 to-slate-950 border-amber-500/60'
            : 'bg-gradient-to-b from-rose-950/50 to-slate-950 border-rose-500/60'
        }`}>
          <div className="flex items-center justify-between mb-1.5">
            <span className={`text-[11px] font-bold uppercase tracking-wider ${
              selectedMode === 'agree' ? 'text-emerald-400' : selectedMode === 'stall' ? 'text-amber-400' : 'text-rose-400'
            }`}>
              📢 Spune cu voce tare:
            </span>
            <span className="text-[10px] text-slate-400 italic">
              {selectedMode === 'stall' ? 'Scuză de microfon (Câștigi timp)' : 'Replica directă'}
            </span>
          </div>

          <p className="text-base sm:text-lg md:text-xl font-bold text-white leading-relaxed select-all">
            "{currentCard.spoken_response}"
          </p>
        </div>

        {/* Action Buttons: Copy & Speak */}
        <div className="grid grid-cols-2 gap-2.5 mb-4">
          <button
            onClick={handleCopy}
            className={`btn-tactile flex items-center justify-center space-x-2 py-3 px-4 rounded-xl font-semibold text-xs sm:text-sm transition-all ${
              copied
                ? 'bg-emerald-600 text-white'
                : 'bg-slate-800 hover:bg-slate-700 text-white border border-slate-700'
            }`}
          >
            {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            <span>{copied ? 'Copiat în memorie!' : 'Copiază Textul'}</span>
          </button>

          <button
            onClick={handleSpeak}
            className={`btn-tactile flex items-center justify-center space-x-2 py-3 px-4 rounded-xl font-semibold text-xs sm:text-sm transition-all ${
              isPlayingAudio
                ? 'bg-teal-600 text-white animate-pulse'
                : 'bg-slate-800 hover:bg-slate-700 text-teal-300 border border-teal-500/30'
            }`}
          >
            <Volume2 className="w-4 h-4" />
            <span>{isPlayingAudio ? 'Se redă în cască...' : 'Ascultă în Cască'}</span>
          </button>
        </div>

        {/* Talking points (optional details) */}
        {currentCard.talking_points && currentCard.talking_points.length > 0 && (
          <div className="mb-4 bg-slate-950/40 rounded-xl p-3 border border-slate-800/80">
            <span className="text-[11px] font-semibold text-slate-400 block mb-1">
              Idei de suport pentru această opțiune:
            </span>
            <ul className="space-y-1">
              {currentCard.talking_points.map((pt, idx) => (
                <li key={idx} className="text-xs text-slate-300 flex items-start space-x-2">
                  <span className="text-emerald-400 font-bold">•</span>
                  <span>{pt}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* Footer & Feedback */}
        <div className="flex items-center justify-between pt-2 border-t border-slate-800">
          <div className="flex items-center space-x-2">
            <span className="text-[11px] text-slate-400">Util?</span>
            <button
              onClick={() => onFeedback && onFeedback(answer.id, 'thumbs_up')}
              className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-emerald-400 transition"
              title="Răspuns util"
            >
              <ThumbsUp className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => onFeedback && onFeedback(answer.id, 'thumbs_down')}
              className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-rose-400 transition"
              title="Nu a fost potrivit"
            >
              <ThumbsDown className="w-3.5 h-3.5" />
            </button>
          </div>

          <button
            onClick={() => {
              if (onFeedback) onFeedback(answer.id, 'used');
              onClose();
            }}
            className="btn-tactile px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 font-bold text-xs sm:text-sm text-white shadow-lg shadow-emerald-600/30"
          >
            Am Răspuns (Închide)
          </button>
        </div>

      </div>
    </div>
  );
}
