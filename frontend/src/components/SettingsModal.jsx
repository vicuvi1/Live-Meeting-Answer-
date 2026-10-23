import React, { useState } from 'react';
import { 
  X, 
  Save, 
  User, 
  Key, 
  ExternalLink, 
  Check, 
  Zap, 
  AlertCircle, 
  Cpu, 
  Layers, 
  Sparkles,
  RefreshCw 
} from 'lucide-react';
import { api } from '../services/api';

const GEMINI_MODELS = [
  { id: 'gemini-2.5-flash', name: 'Gemini 2.5 Flash (Recomandat - Rapid & Gratuit)' },
  { id: 'gemini-1.5-flash', name: 'Gemini 1.5 Flash (Stabil & Gratuit)' },
  { id: 'gemini-1.5-flash-8b', name: 'Gemini 1.5 Flash-8B (Ultra Rapid)' },
  { id: 'gemini-1.5-pro', name: 'Gemini 1.5 Pro (Raționament Avansat)' }
];

const OPENROUTER_FREE_MODELS = [
  { id: 'google/gemini-2.0-flash-exp:free', name: 'Gemini 2.0 Flash Experimental (100% Gratuit)' },
  { id: 'meta-llama/llama-3.3-70b-instruct:free', name: 'Llama 3.3 70B Instruct (100% Gratuit)' },
  { id: 'mistralai/mistral-7b-instruct:free', name: 'Mistral 7B Instruct (100% Gratuit)' },
  { id: 'deepseek/deepseek-chat', name: 'DeepSeek V3 (Cost Minim)' },
  { id: 'google/gemini-flash-1.5', name: 'Gemini Flash 1.5 via OpenRouter' }
];

export default function SettingsModal({
  currentSettings,
  onSaveSettings,
  onClose
}) {
  const [targetName, setTargetName] = useState(currentSettings.targetName || 'User');
  const [provider, setProvider] = useState(currentSettings.provider || 'gemini');
  
  // Gemini settings
  const [geminiKey, setGeminiKey] = useState(currentSettings.geminiKey || '');
  const [geminiModel, setGeminiModel] = useState(currentSettings.geminiModel || 'gemini-2.5-flash');
  
  // OpenRouter settings
  const [openrouterKey, setOpenrouterKey] = useState(currentSettings.openrouterKey || '');
  const [openrouterModel, setOpenrouterModel] = useState(currentSettings.openrouterModel || 'google/gemini-2.0-flash-exp:free');
  
  // STT
  const [deepgramKey, setDeepgramKey] = useState(currentSettings.deepgramKey || '');

  // Testing Key State
  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState(null); // { success: bool, message: string }
  const [savedSuccess, setSavedSuccess] = useState(false);

  // In-App Updates State
  const [checkingUpdate, setCheckingUpdate] = useState(false);
  const [updateInfo, setUpdateInfo] = useState(null);

  const handleCheckUpdate = async () => {
    setCheckingUpdate(true);
    try {
      const res = await api.checkUpdate();
      setUpdateInfo(res);
    } catch (e) {
      setUpdateInfo({
        has_update: false,
        latest_version: 'v1.0.0',
        release_notes: 'Nu s-a putut contacta serverul GitHub.'
      });
    } finally {
      setCheckingUpdate(false);
    }
  };

  const handleTestKey = async () => {
    const activeKey = provider === 'gemini' ? geminiKey.trim() : openrouterKey.trim();
    const activeModel = provider === 'gemini' ? geminiModel : openrouterModel;

    if (!activeKey) {
      setTestResult({
        success: false,
        message: 'Introduceți mai întâi cheia API înainte de testare.'
      });
      return;
    }

    setIsTesting(true);
    setTestResult(null);

    try {
      const res = await api.testApiKey(provider, activeKey, activeModel);
      setTestResult(res);
    } catch (err) {
      setTestResult({
        success: false,
        message: `Eroare conexiune: ${err.message}`
      });
    } finally {
      setIsTesting(false);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSaveSettings({
      targetName: targetName.trim() || 'User',
      provider,
      geminiKey: geminiKey.trim(),
      geminiModel,
      openrouterKey: openrouterKey.trim(),
      openrouterModel,
      deepgramKey: deepgramKey.trim()
    });

    setSavedSuccess(true);
    setTimeout(() => {
      setSavedSuccess(false);
      onClose();
    }, 1000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-slate-950/85 backdrop-blur-md animate-in fade-in duration-200 overflow-y-auto">
      <div className="relative w-full max-w-lg bg-slate-900 border border-slate-800 rounded-3xl p-5 sm:p-6 shadow-2xl my-6 max-h-[90vh] overflow-y-auto">
        
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 rounded-xl bg-slate-800 text-slate-300">
              <Cpu className="w-5 h-5 text-emerald-400" />
            </div>
            <div>
              <h3 className="font-bold text-base sm:text-lg text-white">Configurare Modele AI & Chei</h3>
              <p className="text-[11px] text-slate-400">Google Gemini & OpenRouter (Modele Gratuite)</p>
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
            <label className="text-xs font-semibold text-slate-300 block mb-1">
              Numele persoanei asistate:
            </label>
            <input
              type="text"
              value={targetName}
              onChange={(e) => setTargetName(e.target.value)}
              placeholder="ex: Alex / Maria"
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs sm:text-sm text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500"
            />
          </div>

          {/* Provider Selection Tabs */}
          <div>
            <label className="text-xs font-semibold text-slate-300 block mb-1.5">
              Selectează Furnizorul AI:
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => { setProvider('gemini'); setTestResult(null); }}
                className={`p-3 rounded-2xl border text-left flex items-start space-x-2.5 transition ${
                  provider === 'gemini'
                    ? 'bg-emerald-950/70 border-emerald-500 text-emerald-200 shadow-md shadow-emerald-500/20'
                    : 'bg-slate-950/60 border-slate-800 text-slate-400 hover:text-slate-200'
                }`}
              >
                <Sparkles className={`w-4 h-4 mt-0.5 shrink-0 ${provider === 'gemini' ? 'text-emerald-400' : 'text-slate-500'}`} />
                <div>
                  <span className="font-bold text-xs block text-white">Google Gemini</span>
                  <span className="text-[10px] opacity-80 block">Modele gratuite & rapide</span>
                </div>
              </button>

              <button
                type="button"
                onClick={() => { setProvider('openrouter'); setTestResult(null); }}
                className={`p-3 rounded-2xl border text-left flex items-start space-x-2.5 transition ${
                  provider === 'openrouter'
                    ? 'bg-purple-950/70 border-purple-500 text-purple-200 shadow-md shadow-purple-500/20'
                    : 'bg-slate-950/60 border-slate-800 text-slate-400 hover:text-slate-200'
                }`}
              >
                <Layers className={`w-4 h-4 mt-0.5 shrink-0 ${provider === 'openrouter' ? 'text-purple-400' : 'text-slate-500'}`} />
                <div>
                  <span className="font-bold text-xs block text-white">OpenRouter</span>
                  <span className="text-[10px] opacity-80 block">Gemini, Llama & DeepSeek</span>
                </div>
              </button>
            </div>
          </div>

          {/* TAB 1: GOOGLE GEMINI SETTINGS */}
          {provider === 'gemini' && (
            <div className="p-4 rounded-2xl bg-slate-950/80 border border-emerald-500/30 space-y-3 animate-in fade-in duration-150">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-emerald-300">Cheie API Google Gemini:</span>
                <a 
                  href="https://aistudio.google.com/app/apikey" 
                  target="_blank" 
                  rel="noreferrer"
                  className="text-[10px] text-emerald-400 hover:underline flex items-center space-x-1"
                >
                  <span>Ia cheie gratuită</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              </div>

              <input
                type="password"
                value={geminiKey}
                onChange={(e) => setGeminiKey(e.target.value)}
                placeholder="AIzaSy..."
                className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs sm:text-sm text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500"
              />

              <div>
                <label className="text-[11px] text-slate-400 block mb-1">Model Gemini:</label>
                <select
                  value={geminiModel}
                  onChange={(e) => setGeminiModel(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-emerald-500"
                >
                  {GEMINI_MODELS.map(m => (
                    <option key={m.id} value={m.id}>{m.name}</option>
                  ))}
                </select>
              </div>

              <span className="text-[10px] text-slate-400 block">
                💡 Google AI Studio oferă acces gratuit generos pentru seriile Gemini Flash.
              </span>
            </div>
          )}

          {/* TAB 2: OPENROUTER SETTINGS */}
          {provider === 'openrouter' && (
            <div className="p-4 rounded-2xl bg-slate-950/80 border border-purple-500/30 space-y-3 animate-in fade-in duration-150">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-purple-300">Cheie API OpenRouter:</span>
                <a 
                  href="https://openrouter.ai/keys" 
                  target="_blank" 
                  rel="noreferrer"
                  className="text-[10px] text-purple-400 hover:underline flex items-center space-x-1"
                >
                  <span>Ia cheie la openrouter.ai</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              </div>

              <input
                type="password"
                value={openrouterKey}
                onChange={(e) => setOpenrouterKey(e.target.value)}
                placeholder="sk-or-v1-..."
                className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs sm:text-sm text-white placeholder-slate-600 focus:outline-none focus:border-purple-500"
              />

              <div>
                <label className="text-[11px] text-slate-400 block mb-1">Model OpenRouter:</label>
                <select
                  value={openrouterModel}
                  onChange={(e) => setOpenrouterModel(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-purple-500"
                >
                  {OPENROUTER_FREE_MODELS.map(m => (
                    <option key={m.id} value={m.id}>{m.name}</option>
                  ))}
                </select>
              </div>

              <span className="text-[10px] text-slate-400 block">
                ✨ Modelele marcate cu <code>:free</code> sunt complet gratuite pe OpenRouter!
              </span>
            </div>
          )}

          {/* 1-Click Test Button & Feedback Banner */}
          <div className="space-y-2">
            <button
              type="button"
              onClick={handleTestKey}
              disabled={isTesting}
              className="btn-tactile w-full py-2.5 rounded-xl border border-slate-700 bg-slate-800 hover:bg-slate-700 text-xs font-bold text-slate-200 flex items-center justify-center space-x-2 transition disabled:opacity-50"
            >
              <Zap className={`w-3.5 h-3.5 ${isTesting ? 'animate-spin text-amber-400' : 'text-emerald-400'}`} />
              <span>{isTesting ? 'Se testează conexiunea...' : '⚡ Testează Cheia & Conexiunea'}</span>
            </button>

            {testResult && (
              <div className={`p-3 rounded-xl text-xs flex items-start space-x-2 ${
                testResult.success 
                  ? 'bg-emerald-950/70 border border-emerald-500 text-emerald-200' 
                  : 'bg-rose-950/70 border border-rose-500 text-rose-200'
              }`}>
                {testResult.success ? (
                  <Check className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                ) : (
                  <AlertCircle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                )}
                <div>
                  <span className="font-semibold block">{testResult.message}</span>
                  {testResult.latency_ms && (
                    <span className="text-[10px] opacity-75">Timp de răspuns: {testResult.latency_ms}ms</span>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* Deepgram Key (Optional) */}
          <div className="pt-2 border-t border-slate-800">
            <label className="text-xs font-semibold text-slate-400 block mb-1">
              Deepgram API Key (Opțional pentru STT Română Server):
            </label>
            <input
              type="password"
              value={deepgramKey}
              onChange={(e) => setDeepgramKey(e.target.value)}
              placeholder="Deepgram token..."
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-600 focus:outline-none focus:border-emerald-500"
            />
          </div>

          {/* In-App GitHub Updates */}
          <div className="pt-2 border-t border-slate-800">
            <div className="p-3.5 rounded-2xl bg-slate-950/80 border border-slate-800 space-y-2.5">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-1.5">
                  <RefreshCw className="w-3.5 h-3.5 text-emerald-400" />
                  <span className="text-xs font-bold text-slate-200">Actualizări Aplicație (GitHub)</span>
                </div>
                <span className="text-[10px] text-emerald-400 font-mono px-1.5 py-0.5 rounded bg-emerald-950/80 border border-emerald-800/40">
                  v1.0.0
                </span>
              </div>
              <p className="text-[11px] text-slate-400 leading-relaxed">
                Verifică dacă există o versiune nouă a aplicației Meeting Copilot pe GitHub și descarcă actualizarea direct pe telefon.
              </p>
              
              <button
                type="button"
                onClick={handleCheckUpdate}
                disabled={checkingUpdate}
                className="btn-tactile w-full py-2 rounded-xl border border-slate-700 bg-slate-900 hover:bg-slate-800 text-xs font-semibold text-emerald-300 flex items-center justify-center space-x-2 transition"
              >
                <RefreshCw className={`w-3 h-3 ${checkingUpdate ? 'animate-spin' : ''}`} />
                <span>{checkingUpdate ? 'Se caută pe GitHub...' : '🔍 Verifică Actualizări pe GitHub'}</span>
              </button>

              {updateInfo && (
                <div className={`p-2.5 rounded-xl text-xs flex items-center justify-between transition-all ${
                  updateInfo.has_update 
                    ? 'bg-emerald-950/90 border border-emerald-500 text-emerald-200 shadow-lg shadow-emerald-900/30' 
                    : 'bg-slate-900 border border-slate-800 text-slate-300'
                }`}>
                  <div className="flex flex-col">
                    <span className="font-semibold">
                      {updateInfo.has_update ? `🎉 Versiune nouă: ${updateInfo.latest_version}` : '✓ Aplicația este la zi!'}
                    </span>
                    <span className="text-[10px] opacity-75">
                      {updateInfo.has_update ? 'Apasă butonul pentru a descărca' : 'Nu sunt modificări noi pe GitHub'}
                    </span>
                  </div>

                  {updateInfo.has_update && (
                    <a
                      href={updateInfo.apk_download_url}
                      target="_blank"
                      rel="noreferrer"
                      className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-bold shadow-md shadow-emerald-700/40 active:scale-95 transition"
                    >
                      Descarcă APK
                    </a>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Save Button */}
          <div className="pt-2">
            <button
              type="submit"
              className={`btn-tactile w-full py-3 rounded-xl font-bold text-xs sm:text-sm text-white flex items-center justify-center space-x-2 transition shadow-lg ${
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
