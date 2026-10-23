import React, { useState, useEffect } from 'react';
import { Radio, History, Settings, Sparkles, Maximize2, Minimize2, Type, Download, Crown } from 'lucide-react';
import { api } from '../services/api';

export default function Header({ 
  activeTab, 
  setActiveTab, 
  connectionStatus, 
  targetName = 'User', 
  onOpenSettings,
  onOpenSubscription,
  subscriptionStatus,
  fontSizeMode = 'normal',
  onToggleFontSize
}) {
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [updateAvailable, setUpdateAvailable] = useState(null);

  useEffect(() => {
    let isMounted = true;
    api.checkUpdate().then(res => {
      if (isMounted && res.has_update) {
        setUpdateAvailable(res);
      }
    }).catch(() => {});
    return () => { isMounted = false; };
  }, []);

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().then(() => setIsFullscreen(true)).catch(() => {});
    } else {
      if (document.exitFullscreen) {
        document.exitFullscreen().then(() => setIsFullscreen(false)).catch(() => {});
      }
    }
  };

  return (
    <header className="sticky top-0 z-40 bg-slate-900/90 backdrop-blur-md border-b border-slate-800 px-3 sm:px-4 py-2.5">
      <div className="max-w-4xl mx-auto flex items-center justify-between">
        
        {/* Brand & Target Identity */}
        <div className="flex items-center space-x-2.5">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-400 flex items-center justify-center shadow-lg shadow-emerald-500/20 shrink-0">
            <Sparkles className="w-4 h-4 text-white" />
          </div>
          <div>
            <div className="flex items-center space-x-1.5">
              <h1 className="font-bold text-sm sm:text-base text-white leading-tight">
                Meeting Copilot
              </h1>
              <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                LIVE
              </span>
            </div>
            <p className="text-[11px] text-slate-400 flex items-center space-x-1">
              <span>Pentru:</span>
              <strong className="text-emerald-400 font-semibold">{targetName}</strong>
            </p>
          </div>
        </div>

        {/* Tab Controls */}
        <div className="flex items-center space-x-1 bg-slate-950/70 p-1 rounded-xl border border-slate-800">
          <button
            onClick={() => setActiveTab('copilot')}
            className={`flex items-center space-x-1 px-2.5 sm:px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
              activeTab === 'copilot'
                ? 'bg-emerald-600 text-white shadow-md shadow-emerald-600/30 font-semibold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Radio className="w-3.5 h-3.5" />
            <span>Live</span>
          </button>

          <button
            onClick={() => setActiveTab('history')}
            className={`flex items-center space-x-1 px-2.5 sm:px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
              activeTab === 'history'
                ? 'bg-emerald-600 text-white shadow-md shadow-emerald-600/30 font-semibold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <History className="w-3.5 h-3.5" />
            <span>Istoric</span>
          </button>
        </div>

        {/* Usability Controls: Font Scale, Full Screen, Settings */}
        <div className="flex items-center space-x-1 sm:space-x-1.5">
          
          {/* Subscription Status / Upgrade Button */}
          {onOpenSubscription && (
            subscriptionStatus?.is_active_pro ? (
              <button
                onClick={onOpenSubscription}
                className="flex items-center space-x-1 px-2.5 py-1 rounded-xl bg-gradient-to-r from-amber-500/20 to-emerald-500/20 border border-amber-400/40 text-amber-300 text-[11px] font-bold hover:brightness-110 transition shadow-sm"
                title={`Abonament PRO activ (${subscriptionStatus.plan_name}). Apasă pentru detalii.`}
              >
                <Crown className="w-3.5 h-3.5 text-amber-400" />
                <span className="hidden sm:inline">PRO</span>
              </button>
            ) : (
              <button
                onClick={onOpenSubscription}
                className="btn-tactile flex items-center space-x-1 px-2.5 py-1 rounded-xl bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 text-[11px] font-extrabold shadow-md shadow-amber-900/30 transition shrink-0"
                title="Deblochează accesul nelimitat PRO (7 zile gratuite)"
              >
                <Crown className="w-3.5 h-3.5 text-slate-950 font-bold" />
                <span>PRO</span>
              </button>
            )
          )}

          {/* Update Available Pulsing Pill */}
          {updateAvailable && (
            <a
              href={updateAvailable.apk_download_url}
              target="_blank"
              rel="noreferrer"
              className="flex items-center space-x-1 px-2.5 py-1 rounded-xl bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 text-[11px] font-bold animate-pulse hover:bg-emerald-500/30 transition shrink-0 shadow-lg shadow-emerald-900/40"
              title={`Actualizare ${updateAvailable.latest_version} disponibilă. Apasă pentru a descărca.`}
            >
              <Download className="w-3.5 h-3.5 text-emerald-400" />
              <span className="hidden sm:inline">Update</span>
              <span>{updateAvailable.latest_version}</span>
            </a>
          )}

          {/* Font Scale Accessibility Button */}
          <button
            onClick={onToggleFontSize}
            className="px-2 py-1.5 rounded-xl bg-slate-800/90 hover:bg-slate-700 text-slate-300 hover:text-white transition flex items-center space-x-1 text-xs border border-slate-700/60"
            title="Mărește textul pentru citire ușoară"
          >
            <Type className="w-3.5 h-3.5" />
            <span className="font-bold text-[10px]">
              {fontSizeMode === 'normal' ? 'A' : fontSizeMode === 'large' ? 'A+' : 'A++'}
            </span>
          </button>

          {/* Full Screen Mode Toggle */}
          <button
            onClick={toggleFullscreen}
            className="p-1.5 sm:p-2 rounded-xl bg-slate-800/90 hover:bg-slate-700 text-slate-300 hover:text-white transition border border-slate-700/60"
            title={isFullscreen ? 'Ieși din Tot Ecranul' : 'Afișează pe Tot Ecranul'}
          >
            {isFullscreen ? <Minimize2 className="w-4 h-4 text-emerald-400" /> : <Maximize2 className="w-4 h-4" />}
          </button>

          {/* Settings */}
          <button
            onClick={onOpenSettings}
            className="p-1.5 sm:p-2 rounded-xl bg-slate-800/90 hover:bg-slate-700 text-slate-300 hover:text-white transition border border-slate-700/60"
            title="Setări"
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>

      </div>
    </header>
  );
}
