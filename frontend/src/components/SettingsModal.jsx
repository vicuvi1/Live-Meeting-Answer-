import React, { useState } from 'react';
import { X, Save, User, Key, Globe, Shield, Check } from 'lucide-react';

export default function SettingsModal({
  currentSettings,
  onSaveSettings,
  onClose
}) {
  const [targetName, setTargetName] = useState(currentSettings.targetName || 'Olivia');
  const [geminiKey, setGeminiKey] = useState(currentSettings.geminiKey || '');
  const [deepgramKey, setDeepgramKey] = useState(currentSettings.deepgramKey || '');
  const [savedSuccess, setSavedSuccess] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    onSaveSettings({
      targetName: targetName.trim() || 'Olivia',
      geminiKey: geminiKey.trim(),
      deepgramKey: deepgramKey.trim()
    });
    setSavedSuccess(true);
    setTimeout(() => {
      setSavedSuccess(false);
      onClose();
    }, 1000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-200">
      <div className="relative w-full max-w-md bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl">
        
        {/* Header */}
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 rounded-xl bg-slate-800 text-slate-300">
              <User className="w-5 h-5 text-emerald-400" />
            </div>
            <div>
              <h3 className="font-bold text-lg text-white">Setări Asistent</h3>
              <p className="text-xs text-slate-400">Personalizare pentru utilizator</p>
            </div>
          </div>
          
          <button
            onClick={onClose}
            className="p-1.5 rounded-xl bg-slate-800 text-slate-400 hover:text-white hover:bg-slate-700 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          
          {/* Target Name */}
          <div>
            <label className="text-xs font-semibold text-slate-300 block mb-1.5">
              Numele persoanei asistate (Target Name):
            </label>
            <input
              type="text"
              value={targetName}
              onChange={(e) => setTargetName(e.target.value)}
              placeholder="ex: Olivia"
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
            />
            <span className="text-[11px] text-slate-500 block mt-1">
              Aplicația va vibra și va semnaliza când acest nume este rostit în ședință.
            </span>
          </div>

          {/* Gemini API Key */}
          <div>
            <label className="text-xs font-semibold text-slate-300 block mb-1.5">
              Google Gemini API Key (Opțional):
            </label>
            <input
              type="password"
              value={geminiKey}
              onChange={(e) => setGeminiKey(e.target.value)}
              placeholder="AIzaSy..."
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
            />
            <span className="text-[11px] text-slate-500 block mt-1">
              Fără cheie, asistentul folosește motorul euristic inteligent offline inclus.
            </span>
          </div>

          {/* Deepgram API Key */}
          <div>
            <label className="text-xs font-semibold text-slate-300 block mb-1.5">
              Deepgram API Key (Opțional pentru STT Română):
            </label>
            <input
              type="password"
              value={deepgramKey}
              onChange={(e) => setDeepgramKey(e.target.value)}
              placeholder="Deepgram streaming key..."
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
            />
            <span className="text-[11px] text-slate-500 block mt-1">
              În browser este folosit nativ Web Speech API (ro-RO) gratuit.
            </span>
          </div>

          {/* Save Button */}
          <div className="pt-2">
            <button
              type="submit"
              className={`btn-tactile w-full py-3 rounded-xl font-bold text-sm text-white flex items-center justify-center space-x-2 transition shadow-lg ${
                savedSuccess 
                  ? 'bg-emerald-600 shadow-emerald-600/30' 
                  : 'bg-emerald-600 hover:bg-emerald-500 shadow-emerald-600/30'
              }`}
            >
              {savedSuccess ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />}
              <span>{savedSuccess ? 'Salvat cu succes!' : 'Salvează Setările'}</span>
            </button>
          </div>

        </form>

      </div>
    </div>
  );
}
