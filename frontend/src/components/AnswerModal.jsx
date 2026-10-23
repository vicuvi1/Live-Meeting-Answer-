import React, { useState } from 'react';
import { X, Copy, Check, Volume2, Sparkles, MessageSquare, ThumbsUp, ThumbsDown } from 'lucide-react';
import { AudioService } from '../services/audioCapture';

export default function AnswerModal({ answer, onClose, onFeedback }) {
  const [copied, setCopied] = useState(false);
  const [isPlayingAudio, setIsPlayingAudio] = useState(false);

  if (!answer) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(answer.spoken_response);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleSpeak = () => {
    setIsPlayingAudio(true);
    AudioService.speakRomanian(answer.spoken_response);
    setTimeout(() => setIsPlayingAudio(false), 3000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-200">
      <div className="relative w-full max-w-xl bg-slate-900 border-2 border-emerald-500/80 rounded-3xl p-6 shadow-2xl shadow-emerald-500/20 overflow-hidden">
        
        {/* Glow accent */}
        <div className="absolute top-0 left-0 right-0 h-1.5 bg-gradient-to-r from-emerald-500 via-teal-400 to-emerald-600" />

        {/* Header */}
        <div className="flex items-start justify-between mb-4">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 rounded-xl bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-lg text-white">Răspuns Recomandat</h3>
              <p className="text-xs text-slate-400">Generat în {answer.latency_ms || 320}ms de asistentul tău</p>
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
        <div className="mb-4 bg-slate-950/60 border border-slate-800 rounded-2xl p-3.5">
          <div className="flex items-center space-x-1.5 text-xs font-semibold text-amber-400 mb-1">
            <MessageSquare className="w-3.5 h-3.5" />
            <span>ÎNTREBARE DETECTATĂ ÎN ȘEDINȚĂ:</span>
          </div>
          <p className="text-sm font-medium text-slate-200 italic">
            "{answer.question_detected || 'Ai fost întrebată despre desfășurarea activităților.'}"
          </p>
        </div>

        {/* The Exact Spoken Response - Senior-Friendly Large Text */}
        <div className="mb-5 bg-gradient-to-b from-emerald-950/40 to-slate-950/90 border-2 border-emerald-500/40 rounded-2xl p-5 shadow-inner">
          <span className="block text-xs font-bold uppercase tracking-wider text-emerald-400 mb-2">
            📢 Spune cu voce tare:
          </span>
          <p className="text-lg md:text-xl font-bold text-white leading-relaxed select-all">
            "{answer.spoken_response}"
          </p>
        </div>

        {/* Action Buttons: Copy & Speak */}
        <div className="grid grid-cols-2 gap-3 mb-5">
          <button
            onClick={handleCopy}
            className={`btn-tactile flex items-center justify-center space-x-2 py-3 px-4 rounded-xl font-semibold text-sm transition-all ${
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
            className={`btn-tactile flex items-center justify-center space-x-2 py-3 px-4 rounded-xl font-semibold text-sm transition-all ${
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
        {answer.talking_points && answer.talking_points.length > 0 && (
          <div className="mb-5 bg-slate-950/40 rounded-xl p-3 border border-slate-800/80">
            <span className="text-xs font-semibold text-slate-400 block mb-1.5">
              Idei de suport (dacă ți se cer detalii):
            </span>
            <ul className="space-y-1">
              {answer.talking_points.map((pt, idx) => (
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
            <span className="text-xs text-slate-400">Ți-a fost util?</span>
            <button
              onClick={() => onFeedback && onFeedback(answer.id, 'thumbs_up')}
              className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-emerald-400 transition"
              title="Răspuns excelent"
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
            className="btn-tactile px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 font-bold text-sm text-white shadow-lg shadow-emerald-600/30"
          >
            Am Răspuns (Închide)
          </button>
        </div>

      </div>
    </div>
  );
}
