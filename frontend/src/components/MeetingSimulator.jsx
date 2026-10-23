import React, { useState, useEffect } from 'react';
import { Play, Pause, RotateCcw, X, Sparkles, MessageSquare, FastForward } from 'lucide-react';

const getScenarios = (target) => [
  {
    id: 'budget',
    title: 'Discuție Buget & Resurse (T4)',
    lines: [
      { speaker: 'Director General', text: 'Bună ziua tuturor! Deschidem ședința operativă pe T4.' },
      { speaker: 'Radu (Financiar)', text: 'Am revizuit datele preliminare și avem o depășire minoră pe echipamente.' },
      { speaker: 'Elena (Operațiuni)', text: 'Cred că putem compensa din economiile realizate luna trecută.' },
      { speaker: 'Director General', text: `${target}, ce părere ai despre estimările de buget? Ne încadrăm în parametrii stabiliți inițial?` }
    ]
  },
  {
    id: 'deadline',
    title: 'Termen Livrare Proiect & Raport',
    lines: [
      { speaker: 'Manager Proiect', text: 'Echipa de audit așteaptă raportul final consolidat.' },
      { speaker: 'Andrei (Tehnic)', text: 'Din punct de vedere tehnic, ultimele verificări s-au încheiat cu succes.' },
      { speaker: 'Manager Proiect', text: `${target}, vă rugăm să ne confirmați: este fezabil să transmitem documentul finalizat până vineri la ora 12:00?` }
    ]
  },
  {
    id: 'strategy',
    title: 'Validare Strategie Echipă',
    lines: [
      { speaker: 'Mihai', text: 'Propun să simplificăm fluxul de comunicare cu clienții din această săptămână.' },
      { speaker: 'Simona', text: 'Susțin propunerea lui Mihai, economisim cel puțin două ore zilnic.' },
      { speaker: 'Mihai', text: `${target}, tu ce zici? Ești de acord să aplicăm această modificare începând de luni?` }
    ]
  }
];

export default function MeetingSimulator({ onInjectTranscript, onClose, targetName = 'User' }) {
  const scenarios = getScenarios(targetName);
  const [selectedScenarioIndex, setSelectedScenarioIndex] = useState(0);
  const selectedScenario = scenarios[selectedScenarioIndex] || scenarios[0];
  const [currentLineIndex, setCurrentLineIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [timerId, setTimerId] = useState(null);

  useEffect(() => {
    return () => {
      if (timerId) clearInterval(timerId);
    };
  }, [timerId]);

  const injectLine = (line) => {
    onInjectTranscript(line.text, line.speaker, true);
  };

  const handleNextStep = () => {
    if (currentLineIndex < selectedScenario.lines.length) {
      injectLine(selectedScenario.lines[currentLineIndex]);
      setCurrentLineIndex(prev => prev + 1);
    }
  };

  const handleAutoPlay = () => {
    if (isPlaying) {
      clearInterval(timerId);
      setIsPlaying(false);
      setTimerId(null);
    } else {
      setIsPlaying(true);
      let idx = currentLineIndex;
      const id = setInterval(() => {
        if (idx < selectedScenario.lines.length) {
          injectLine(selectedScenario.lines[idx]);
          idx++;
          setCurrentLineIndex(idx);
        } else {
          clearInterval(id);
          setIsPlaying(false);
          setTimerId(null);
        }
      }, 2400); // 2.4 seconds per turn for natural reading pace
      setTimerId(id);
    }
  };

  const handleReset = () => {
    if (timerId) clearInterval(timerId);
    setIsPlaying(false);
    setTimerId(null);
    setCurrentLineIndex(0);
  };

  const handleSelectScenario = (index) => {
    handleReset();
    setSelectedScenarioIndex(index);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl">
        
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center space-x-2">
            <div className="p-2 rounded-xl bg-purple-500/20 text-purple-400 border border-purple-500/30">
              <Play className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-lg text-white">Simulator Ședință Live</h3>
              <p className="text-xs text-slate-400">Testează reacția asistentului și butonul AI</p>
            </div>
          </div>
          
          <button
            onClick={() => {
              handleReset();
              onClose();
            }}
            className="p-1.5 rounded-xl bg-slate-800 text-slate-400 hover:text-white hover:bg-slate-700 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scenario Selection */}
        <div className="mb-4">
          <label className="text-xs font-semibold text-slate-400 block mb-2">Alege un scenariu de test:</label>
          <div className="grid grid-cols-1 gap-2">
            {scenarios.map((sc, scIdx) => (
              <button
                key={sc.id}
                onClick={() => handleSelectScenario(scIdx)}
                className={`text-left p-3 rounded-xl border text-xs transition ${
                  selectedScenario.id === sc.id
                    ? 'bg-purple-950/60 border-purple-500 text-purple-200 font-semibold'
                    : 'bg-slate-950/50 border-slate-800 text-slate-400 hover:text-slate-200'
                }`}
              >
                <span className="block font-medium">{sc.title}</span>
                <span className="text-[11px] opacity-70">{sc.lines.length} replici simulate</span>
              </button>
            ))}
          </div>
        </div>

        {/* Dialogue preview */}
        <div className="mb-5 bg-slate-950/80 border border-slate-800 rounded-2xl p-4 max-h-48 overflow-y-auto space-y-2">
          {selectedScenario.lines.map((line, idx) => {
            const isDelivered = idx < currentLineIndex;
            const isNext = idx === currentLineIndex;
            const mentionsTarget = line.text.includes(targetName);

            return (
              <div 
                key={idx}
                className={`p-2.5 rounded-xl text-xs transition ${
                  isDelivered 
                    ? 'bg-emerald-950/30 border border-emerald-500/30 text-emerald-200' 
                    : isNext 
                    ? 'bg-purple-950/40 border border-purple-500/50 text-purple-100 font-medium'
                    : 'bg-slate-900/40 text-slate-500'
                }`}
              >
                <div className="flex items-center justify-between font-bold mb-0.5">
                  <span>{line.speaker}</span>
                  <span className="text-[10px]">
                    {isDelivered ? '✓ Trimis' : isNext ? 'Urmează' : 'În așteptare'}
                  </span>
                </div>
                <p className={mentionsTarget ? 'font-semibold text-amber-300' : ''}>
                  {line.text}
                </p>
              </div>
            );
          })}
        </div>

        {/* Playback Controls */}
        <div className="grid grid-cols-3 gap-2.5">
          <button
            onClick={handleNextStep}
            disabled={currentLineIndex >= selectedScenario.lines.length}
            className="btn-tactile flex items-center justify-center space-x-1.5 p-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-white font-semibold text-xs border border-slate-700 disabled:opacity-40"
          >
            <FastForward className="w-4 h-4" />
            <span>Pasul Următor</span>
          </button>

          <button
            onClick={handleAutoPlay}
            disabled={currentLineIndex >= selectedScenario.lines.length && !isPlaying}
            className={`btn-tactile flex items-center justify-center space-x-1.5 p-3 rounded-xl font-bold text-xs shadow-lg transition ${
              isPlaying 
                ? 'bg-amber-600 hover:bg-amber-500 text-white shadow-amber-600/30' 
                : 'bg-purple-600 hover:bg-purple-500 text-white shadow-purple-600/30'
            } disabled:opacity-40`}
          >
            {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
            <span>{isPlaying ? 'Pauză' : 'Redă Automat'}</span>
          </button>

          <button
            onClick={handleReset}
            className="btn-tactile flex items-center justify-center space-x-1.5 p-3 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold text-xs border border-slate-700"
          >
            <RotateCcw className="w-4 h-4" />
            <span>Reset</span>
          </button>
        </div>

      </div>
    </div>
  );
}
