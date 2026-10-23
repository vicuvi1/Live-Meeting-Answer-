import React from 'react';

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('[NEVER-CRASH FRONTEND GUARD] Error intercepted by ErrorBoundary:', error, errorInfo);
  }

  handleReload = () => {
    this.setState({ hasError: false, error: null });
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-slate-950 text-white flex flex-col items-center justify-center p-6 text-center select-none">
          <div className="w-20 h-20 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-4xl mb-6 shadow-xl shadow-amber-500/5">
            🛡️
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-amber-400 mb-2">
            Copilotul Olivia a fost protejat cu succes
          </h1>
          <p className="text-slate-400 max-w-md text-sm leading-relaxed mb-6">
            Aplicația a întâmpinat o nepotrivire de date temporară, dar sistemul anti-crash a oprit blocarea ecranului. Poți continua fără griji.
          </p>

          <div className="bg-slate-900 border border-slate-800 rounded-xl p-3 text-xs text-slate-500 font-mono max-w-sm mb-6 overflow-hidden text-ellipsis whitespace-nowrap">
            {this.state.error?.message || 'Eroare necunoscută interceptată'}
          </div>

          <button
            onClick={this.handleReload}
            className="px-6 py-3 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-semibold rounded-xl shadow-lg shadow-emerald-900/30 active:scale-95 transition-all text-sm flex items-center gap-2"
          >
            <span>🔄</span>
            <span>Reîncarcă Ecranul</span>
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
