import React from 'react';
import { Radio, History, Settings, Sparkles, ShieldCheck } from 'lucide-react';

export default function Header({ 
  activeTab, 
  setActiveTab, 
  connectionStatus, 
  targetName = 'Olivia', 
  onOpenSettings 
}) {
  return (
    <header className="sticky top-0 z-40 bg-slate-900/90 backdrop-blur-md border-b border-slate-800 px-4 py-3">
      <div className="max-w-4xl mx-auto flex items-center justify-between">
        
        {/* Brand & Target Identity */}
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-400 flex items-center justify-center shadow-lg shadow-emerald-500/20">
            <Sparkles className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="font-bold text-base md:text-lg text-white leading-tight">
                Olivia Copilot
              </h1>
              <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                LIVE
              </span>
            </div>
            <p className="text-xs text-slate-400 flex items-center space-x-1">
              <span>Asistent discret pentru:</span>
              <strong className="text-emerald-400 font-semibold">{targetName}</strong>
            </p>
          </div>
        </div>

        {/* Tab Controls */}
        <div className="flex items-center space-x-1 bg-slate-950/60 p-1 rounded-xl border border-slate-800">
          <button
            onClick={() => setActiveTab('copilot')}
            className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs md:text-sm font-medium transition-all ${
              activeTab === 'copilot'
                ? 'bg-emerald-600 text-white shadow-md shadow-emerald-600/30 font-semibold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Radio className="w-4 h-4" />
            <span>Ședință Live</span>
          </button>

          <button
            onClick={() => setActiveTab('history')}
            className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs md:text-sm font-medium transition-all ${
              activeTab === 'history'
                ? 'bg-emerald-600 text-white shadow-md shadow-emerald-600/30 font-semibold'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <History className="w-4 h-4" />
            <span>Istoric</span>
          </button>
        </div>

        {/* Status & Settings */}
        <div className="flex items-center space-x-2">
          {/* Connection status indicator */}
          <div 
            title={`Status server: ${connectionStatus}`}
            className="flex items-center space-x-1.5 px-2.5 py-1 rounded-full bg-slate-800/80 border border-slate-700 text-xs"
          >
            <span className={`w-2 h-2 rounded-full ${
              connectionStatus === 'connected' 
                ? 'bg-emerald-400 shadow-sm shadow-emerald-400 animate-pulse' 
                : connectionStatus === 'reconnecting' 
                ? 'bg-amber-400 animate-ping' 
                : 'bg-rose-500'
            }`} />
            <span className="hidden sm:inline text-slate-300 text-[11px] font-medium">
              {connectionStatus === 'connected' ? 'Conectat' : connectionStatus === 'reconnecting' ? 'Reconectare...' : 'Deconectat'}
            </span>
          </div>

          <button
            onClick={onOpenSettings}
            className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors"
            title="Setări aplicație"
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>

      </div>
    </header>
  );
}
