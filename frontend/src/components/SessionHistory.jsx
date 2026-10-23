import React, { useState, useEffect } from 'react';
import { 
  Calendar, 
  Clock, 
  FileText, 
  Download, 
  Trash2, 
  Search, 
  Sparkles, 
  ChevronRight, 
  ArrowLeft,
  CheckCircle2,
  HelpCircle,
  MessageSquare
} from 'lucide-react';
import { api } from '../services/api';

export default function SessionHistory({ onResumeSession, targetName = 'User' }) {
  const [sessions, setSessions] = useState([]);
  const [selectedSession, setSelectedSession] = useState(null);
  const [transcripts, setTranscripts] = useState([]);
  const [answers, setAnswers] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSummarizing, setIsSummarizing] = useState(false);

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    setIsLoading(true);
    try {
      const data = await api.listSessions();
      setSessions(data);
    } catch (err) {
      console.error('Error loading sessions:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSelectSession = async (session) => {
    setSelectedSession(session);
    try {
      const [tData, aData] = await Promise.all([
        api.getTranscripts(session.id),
        api.getSessionAnswers(session.id)
      ]);
      setTranscripts(tData);
      setAnswers(aData);
    } catch (err) {
      console.error('Error fetching session details:', err);
    }
  };

  const handleSummarize = async () => {
    if (!selectedSession) return;
    setIsSummarizing(true);
    try {
      const res = await api.summarizeSession(selectedSession.id);
      setSelectedSession(prev => ({
        ...prev,
        summary: res.summary,
        action_items: Array.isArray(res.action_items) ? res.action_items.join('\n') : (res.action_items || '')
      }));
      // update list
      loadSessions();
    } catch (err) {
      alert(`Eroare generare rezumat: ${err.message}`);
    } finally {
      setIsSummarizing(false);
    }
  };

  const handleExport = async () => {
    if (!selectedSession) return;
    try {
      const markdown = await api.exportSessionMarkdown(selectedSession.id);
      const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${selectedSession.title.replace(/\s+/g, '_')}.md`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      alert(`Eroare export: ${err.message}`);
    }
  };

  const handleDelete = async (e, sessionId) => {
    e.stopPropagation();
    if (!window.confirm('Sigur doriți să ștergeți această înregistrare din istoric?')) return;
    try {
      await api.deleteSession(sessionId);
      if (selectedSession?.id === sessionId) setSelectedSession(null);
      loadSessions();
    } catch (err) {
      alert(`Eroare ștergere: ${err.message}`);
    }
  };

  const filteredTranscripts = transcripts.filter(t => 
    t.text.toLowerCase().includes(searchQuery.toLowerCase()) ||
    t.speaker.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="max-w-4xl mx-auto p-4 pb-12">
      
      {/* Title */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-xl md:text-2xl font-bold text-white">Istoric Ședințe & Conversații</h2>
          <p className="text-xs md:text-sm text-slate-400">
            Toate convorbirile, întrebările și deciziile salvate pentru {targetName}
          </p>
        </div>
      </div>

      {/* Main Content: List or Detail */}
      {!selectedSession ? (
        /* Sessions List View */
        <div className="space-y-3">
          {isLoading ? (
            <div className="p-8 text-center text-slate-500">Se încarcă istoricul...</div>
          ) : sessions.length === 0 ? (
            <div className="bg-slate-900 border border-slate-800 rounded-3xl p-10 text-center text-slate-400">
              <Calendar className="w-10 h-10 mx-auto mb-3 text-slate-600" />
              <p className="font-semibold text-white mb-1">Nu există ședințe salvate încă</p>
              <p className="text-xs text-slate-500">
                Porniți o ședință live din meniul de sus pentru a începe înregistrarea automată.
              </p>
            </div>
          ) : (
            sessions.map(s => (
              <div
                key={s.id}
                onClick={() => handleSelectSession(s)}
                className="btn-tactile bg-slate-900/80 hover:bg-slate-800/80 border border-slate-800 hover:border-emerald-500/50 rounded-2xl p-4 flex items-center justify-between cursor-pointer transition shadow-sm"
              >
                <div className="flex items-start space-x-3.5">
                  <div className="p-2.5 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    <FileText className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-bold text-sm md:text-base text-white">
                      {s.title}
                    </h3>
                    <div className="flex items-center space-x-3 text-xs text-slate-400 mt-1">
                      <span className="flex items-center space-x-1">
                        <Calendar className="w-3.5 h-3.5" />
                        <span>{s.start_time ? s.start_time.split('T')[0] : 'Astăzi'}</span>
                      </span>
                      <span className="flex items-center space-x-1">
                        <Clock className="w-3.5 h-3.5" />
                        <span>{s.start_time ? s.start_time.split('T')[1]?.substring(0, 5) : ''}</span>
                      </span>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                        s.status === 'active' 
                          ? 'bg-emerald-500/20 text-emerald-400' 
                          : 'bg-slate-800 text-slate-400'
                      }`}>
                        {s.status === 'active' ? 'În curs' : 'Finalizată'}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center space-x-2">
                  <button
                    onClick={(e) => handleDelete(e, s.id)}
                    className="p-2 rounded-xl text-slate-500 hover:text-rose-400 hover:bg-rose-950/30 transition"
                    title="Șterge din istoric"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                  <ChevronRight className="w-5 h-5 text-slate-500" />
                </div>
              </div>
            ))
          )}
        </div>
      ) : (
        /* Single Session Detail View */
        <div className="space-y-5 animate-in fade-in duration-200">
          
          {/* Back button & Action buttons */}
          <div className="flex items-center justify-between bg-slate-900 border border-slate-800 rounded-2xl p-3.5">
            <button
              onClick={() => setSelectedSession(null)}
              className="flex items-center space-x-2 text-xs md:text-sm font-semibold text-slate-300 hover:text-white"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Înapoi la listă</span>
            </button>

            <div className="flex items-center space-x-2">
              <button
                onClick={handleSummarize}
                disabled={isSummarizing}
                className="btn-tactile flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold shadow-md shadow-purple-600/30 disabled:opacity-50"
              >
                <Sparkles className="w-3.5 h-3.5" />
                <span>{isSummarizing ? 'Se generează...' : 'Generează Rezumat AI'}</span>
              </button>

              <button
                onClick={handleExport}
                className="btn-tactile flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold border border-slate-700"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Exportă (.md)</span>
              </button>
            </div>
          </div>

          {/* Session Header Details */}
          <div className="bg-slate-900 border border-slate-800 rounded-3xl p-5">
            <h2 className="text-lg md:text-xl font-bold text-white mb-2">{selectedSession.title}</h2>
            <div className="flex flex-wrap items-center gap-3 text-xs text-slate-400">
              <span>Data: <strong>{selectedSession.start_time?.split('T')[0]}</strong></span>
              <span>Ora: <strong>{selectedSession.start_time?.split('T')[1]?.substring(0, 5)}</strong></span>
              <span>Total replici: <strong>{transcripts.length}</strong></span>
              <span>Întrebări asistate: <strong>{answers.length}</strong></span>
            </div>
          </div>

          {/* Executive Summary & Action items card (if generated) */}
          {selectedSession.summary && (
            <div className="bg-gradient-to-br from-purple-950/40 via-slate-900 to-slate-950 border-2 border-purple-500/40 rounded-3xl p-5 shadow-lg">
              <div className="flex items-center space-x-2 text-purple-400 font-bold text-sm mb-2">
                <Sparkles className="w-4 h-4" />
                <span>REZUMAT EXECUTIV ȘEDINȚĂ</span>
              </div>
              <p className="text-sm text-slate-200 leading-relaxed mb-4">
                {selectedSession.summary}
              </p>

              {selectedSession.action_items && (
                <div className="bg-slate-950/60 rounded-xl p-3 border border-purple-500/20">
                  <span className="text-xs font-bold text-purple-300 block mb-2">Plan de Acțiune / Decizii:</span>
                  <div className="text-xs text-slate-300 whitespace-pre-line leading-relaxed">
                    {selectedSession.action_items}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* AI Answers Log for this call */}
          {answers.length > 0 && (
            <div className="bg-slate-900 border border-slate-800 rounded-3xl p-5">
              <h3 className="font-bold text-sm md:text-base text-white mb-3 flex items-center space-x-2">
                <MessageSquare className="w-4 h-4 text-emerald-400" />
                <span>Răspunsuri Sugerate în Timpul Ședinței</span>
              </h3>
              <div className="space-y-3">
                {answers.map(ans => (
                  <div key={ans.id} className="p-3.5 rounded-2xl bg-slate-950/80 border border-slate-800 text-xs">
                    <p className="text-amber-400 font-semibold mb-1">
                      Întrebare: "{ans.question_detected}"
                    </p>
                    <p className="text-emerald-300 font-bold text-sm mb-1">
                      Răspuns generat: "{ans.spoken_response}"
                    </p>
                    <span className="text-[10px] text-slate-500 block">
                      Generat la {ans.generated_at ? ans.generated_at.split('T')[1]?.substring(0, 5) : ''}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Transcript Viewer with Search */}
          <div className="bg-slate-900 border border-slate-800 rounded-3xl p-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
              <h3 className="font-bold text-sm md:text-base text-white">Transcriere Completă</h3>
              <div className="relative">
                <Search className="w-4 h-4 absolute left-3 top-2.5 text-slate-500" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Caută în conversație..."
                  className="bg-slate-950 border border-slate-800 text-xs rounded-xl pl-9 pr-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500 w-full sm:w-56"
                />
              </div>
            </div>

            <div className="max-h-96 overflow-y-auto space-y-2 pr-1">
              {filteredTranscripts.length === 0 ? (
                <p className="text-xs text-slate-500 text-center py-6">Niciun rezultat găsit.</p>
              ) : (
                filteredTranscripts.map(t => (
                  <div 
                    key={t.id}
                    className={`p-3 rounded-xl text-xs ${
                      t.mentions_target 
                        ? 'bg-amber-950/30 border border-amber-500/40 text-amber-100 font-medium' 
                        : 'bg-slate-950/50 text-slate-300'
                    }`}
                  >
                    <div className="flex items-center justify-between text-[11px] mb-1">
                      <span className="font-bold text-slate-400">{t.speaker}</span>
                      <span className="text-slate-600">{t.timestamp ? t.timestamp.split('T')[1]?.substring(0, 5) : ''}</span>
                    </div>
                    <p className="leading-relaxed">{t.text}</p>
                  </div>
                ))
              )}
            </div>
          </div>

        </div>
      )}

    </div>
  );
}
