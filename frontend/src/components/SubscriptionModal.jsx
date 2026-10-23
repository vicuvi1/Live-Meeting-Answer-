import React, { useState, useEffect } from 'react';
import { 
  X, 
  Check, 
  Sparkles, 
  Crown, 
  ShieldCheck, 
  Zap, 
  Clock, 
  Gift, 
  AlertCircle,
  ArrowRight,
  CreditCard
} from 'lucide-react';
import { api } from '../services/api';

export default function SubscriptionModal({ isOpen, onClose, onSubscriptionUpdated }) {
  const [subData, setSubData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [voucherCode, setVoucherCode] = useState('');
  const [voucherLoading, setVoucherLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState(null); // { type: 'success' | 'error', text: '' }
  const [processingPlan, setProcessingPlan] = useState(null); // 'pro_monthly' | 'pro_yearly' | 'trial'

  useEffect(() => {
    if (isOpen) {
      loadSubscriptionStatus();
    }
  }, [isOpen]);

  const loadSubscriptionStatus = async () => {
    setLoading(true);
    try {
      const res = await api.getSubscription();
      setSubData(res);
    } catch (err) {
      console.error('Failed to load subscription:', err);
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  const handleStartTrial = async () => {
    setProcessingPlan('trial');
    setActionMessage(null);
    try {
      const res = await api.startTrial();
      setActionMessage({ type: 'success', text: res.message });
      await loadSubscriptionStatus();
      if (onSubscriptionUpdated) onSubscriptionUpdated();
    } catch (err) {
      setActionMessage({ type: 'error', text: err.message });
    } finally {
      setProcessingPlan(null);
    }
  };

  const handleCheckout = async (planType) => {
    setProcessingPlan(planType);
    setActionMessage(null);
    try {
      const res = await api.checkoutSubscription(planType);
      setActionMessage({ type: 'success', text: res.message });
      await loadSubscriptionStatus();
      if (onSubscriptionUpdated) onSubscriptionUpdated();
    } catch (err) {
      setActionMessage({ type: 'error', text: err.message });
    } finally {
      setProcessingPlan(null);
    }
  };

  const handleActivateVoucher = async (e) => {
    e.preventDefault();
    if (!voucherCode.trim()) return;

    setVoucherLoading(true);
    setActionMessage(null);
    try {
      const res = await api.activateVoucher(voucherCode.trim());
      setActionMessage({ type: 'success', text: res.message });
      setVoucherCode('');
      await loadSubscriptionStatus();
      if (onSubscriptionUpdated) onSubscriptionUpdated();
    } catch (err) {
      setActionMessage({ type: 'error', text: err.message });
    } finally {
      setVoucherLoading(false);
    }
  };

  const isPro = subData?.is_active_pro;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/80 backdrop-blur-md overflow-y-auto">
      <div className="relative w-full max-w-xl bg-slate-900 border border-slate-800 rounded-3xl shadow-2xl p-5 sm:p-7 text-white animate-in fade-in zoom-in-95 duration-200 my-auto">
        
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 rounded-full bg-slate-800/80 hover:bg-slate-700 text-slate-400 hover:text-white transition"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Header with Romanian Branding */}
        <div className="text-center mb-6">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-to-tr from-amber-500 to-emerald-400 shadow-xl shadow-amber-500/20 mb-3">
            <Crown className="w-7 h-7 text-slate-950 font-bold" />
          </div>
          <h2 className="text-xl sm:text-2xl font-bold tracking-tight text-white flex items-center justify-center gap-2">
            <span>Meeting Copilot</span>
            <span className="text-amber-400 font-extrabold text-sm uppercase px-2 py-0.5 rounded-full bg-amber-400/10 border border-amber-400/30">
              PRO
            </span>
          </h2>
          <p className="text-slate-400 text-xs sm:text-sm mt-1 max-w-md mx-auto">
            Asistentul tău personal nelimitat pentru ședințe live în limba română
          </p>
        </div>

        {/* Active PRO Status Banner if already subscribed */}
        {isPro && (
          <div className="mb-6 p-4 rounded-2xl bg-gradient-to-r from-emerald-950/80 to-teal-950/80 border border-emerald-500/50 flex items-center justify-between shadow-lg shadow-emerald-950/40">
            <div className="flex items-center space-x-3">
              <div className="w-10 h-10 rounded-xl bg-emerald-500/20 flex items-center justify-center text-emerald-400">
                <ShieldCheck className="w-6 h-6" />
              </div>
              <div>
                <h4 className="text-xs sm:text-sm font-bold text-emerald-300">
                  {subData?.plan_name || 'Abonament PRO Activ'}
                </h4>
                <p className="text-[11px] text-emerald-400/80">
                  {subData?.days_remaining > 365 
                    ? 'Acces Nelimitat pe Viață activ' 
                    : `Valabil încă ${subData?.days_remaining} zile`}
                </p>
              </div>
            </div>
            <span className="text-[11px] font-bold px-2.5 py-1 rounded-full bg-emerald-500 text-slate-950 uppercase tracking-wider">
              Activ
            </span>
          </div>
        )}

        {/* Feedback Alert message */}
        {actionMessage && (
          <div className={`mb-5 p-3.5 rounded-2xl text-xs flex items-center space-x-2.5 ${
            actionMessage.type === 'success' 
              ? 'bg-emerald-950/90 border border-emerald-500 text-emerald-200' 
              : 'bg-rose-950/90 border border-rose-500 text-rose-200'
          }`}>
            {actionMessage.type === 'success' ? (
              <Check className="w-4 h-4 text-emerald-400 shrink-0" />
            ) : (
              <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
            )}
            <span className="font-medium">{actionMessage.text}</span>
          </div>
        )}

        {/* Free 7-Day Trial Option (if not already used) */}
        {!isPro && subData?.trial_ends_at === null && (
          <div className="mb-5 p-4 rounded-2xl bg-gradient-to-r from-amber-500/15 via-emerald-500/10 to-teal-500/15 border border-amber-500/40 flex flex-col sm:flex-row items-center justify-between gap-3 shadow-lg">
            <div className="flex items-center space-x-2.5">
              <Gift className="w-6 h-6 text-amber-400 shrink-0" />
              <div>
                <span className="text-xs font-bold text-amber-300 block">
                  Perioadă de Probă Gratuită 7 Zile
                </span>
                <span className="text-[11px] text-slate-300">
                  Deblochează toate funcțiile fără card și fără niciun risc.
                </span>
              </div>
            </div>
            <button
              onClick={handleStartTrial}
              disabled={processingPlan === 'trial'}
              className="btn-tactile w-full sm:w-auto px-4 py-2 rounded-xl bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-slate-950 font-bold text-xs shrink-0 shadow-md shadow-amber-900/30 transition disabled:opacity-50"
            >
              {processingPlan === 'trial' ? 'Se activează...' : 'Activează 7 Zile Gratis'}
            </button>
          </div>
        )}

        {/* Romanian Pricing Cards in LEI (RON) */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5 mb-6">
          
          {/* Monthly Plan */}
          <div className={`p-4 rounded-2xl border transition-all ${
            subData?.plan_tier === 'pro_monthly'
              ? 'bg-slate-950/90 border-emerald-500 ring-2 ring-emerald-500/30'
              : 'bg-slate-950/50 border-slate-800 hover:border-slate-700'
          }`}>
            <span className="text-xs font-bold text-slate-300 block">PRO Lunar</span>
            <div className="mt-2 flex items-baseline space-x-1">
              <span className="text-2xl sm:text-3xl font-extrabold text-white">49</span>
              <span className="text-xs font-semibold text-slate-400">Lei / lună</span>
            </div>
            <p className="text-[11px] text-slate-500 mt-1 mb-4">
              Facturat lunar, anulezi oricând dorești.
            </p>
            <button
              onClick={() => handleCheckout('pro_monthly')}
              disabled={processingPlan === 'pro_monthly' || subData?.plan_tier === 'pro_monthly'}
              className="btn-tactile w-full py-2.5 rounded-xl border border-slate-700 bg-slate-800 hover:bg-slate-700 text-xs font-bold text-slate-200 transition disabled:opacity-50"
            >
              {subData?.plan_tier === 'pro_monthly' 
                ? 'Planul Tău Curent' 
                : processingPlan === 'pro_monthly' ? 'Se procesează...' : 'Alege Planul Lunar'}
            </button>
          </div>

          {/* Yearly Plan (Best Value - Popular) */}
          <div className={`relative p-4 rounded-2xl border transition-all ${
            subData?.plan_tier === 'pro_yearly'
              ? 'bg-emerald-950/40 border-emerald-500 ring-2 ring-emerald-500/30'
              : 'bg-slate-950/70 border-emerald-500/60 shadow-lg shadow-emerald-950/30'
          }`}>
            <div className="absolute -top-2.5 right-3 bg-gradient-to-r from-emerald-500 to-teal-400 text-slate-950 font-extrabold text-[10px] px-2.5 py-0.5 rounded-full uppercase tracking-wider shadow">
              Economisești 32%
            </div>
            <span className="text-xs font-bold text-emerald-400 block">PRO Anual (Recomandat)</span>
            <div className="mt-2 flex items-baseline space-x-1">
              <span className="text-2xl sm:text-3xl font-extrabold text-white">399</span>
              <span className="text-xs font-semibold text-slate-400">Lei / an</span>
            </div>
            <p className="text-[11px] text-emerald-400/80 mt-1 mb-4">
              Doar ~33 Lei / lună (economie de 189 Lei).
            </p>
            <button
              onClick={() => handleCheckout('pro_yearly')}
              disabled={processingPlan === 'pro_yearly' || subData?.plan_tier === 'pro_yearly'}
              className="btn-tactile w-full py-2.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-xs font-bold text-white shadow-lg shadow-emerald-900/30 transition disabled:opacity-50"
            >
              {subData?.plan_tier === 'pro_yearly' 
                ? 'Planul Tău Curent' 
                : processingPlan === 'pro_yearly' ? 'Se procesează...' : 'Alege Planul Anual'}
            </button>
          </div>

        </div>

        {/* Romanian Feature Checklist */}
        <div className="mb-6 p-4 rounded-2xl bg-slate-950/60 border border-slate-800/80 space-y-2.5">
          <span className="text-xs font-bold text-slate-300 block mb-2">
            Ce include pachetul complet Meeting Copilot PRO:
          </span>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-[11px] text-slate-300">
            <div className="flex items-center space-x-2">
              <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              <span>Transcriere nelimitată în limba română</span>
            </div>
            <div className="flex items-center space-x-2">
              <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              <span>3 Răspunsuri tactice instant sub 1 secundă</span>
            </div>
            <div className="flex items-center space-x-2">
              <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              <span>Alertă la numire directă sau întrebare</span>
            </div>
            <div className="flex items-center space-x-2">
              <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              <span>Bula plutitoare peste Zoom, Teams & Meet</span>
            </div>
            <div className="flex items-center space-x-2">
              <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              <span>Rezumate AI & Listă de Acțiuni automate</span>
            </div>
            <div className="flex items-center space-x-2">
              <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              <span>100% Confidențialitate pe telefon (1TB)</span>
            </div>
          </div>
        </div>

        {/* Voucher / License Activation Section */}
        <div className="pt-4 border-t border-slate-800">
          <form onSubmit={handleActivateVoucher} className="space-y-2">
            <label className="text-xs font-semibold text-slate-400 flex items-center justify-between">
              <span>Ai un cod de activare sau voucher?</span>
              <span className="text-[10px] text-emerald-400 font-mono">ex: VIP-PRO-2026</span>
            </label>
            <div className="flex space-x-2">
              <input
                type="text"
                value={voucherCode}
                onChange={(e) => setVoucherCode(e.target.value.toUpperCase())}
                placeholder="Introdu codul de licență..."
                className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 text-xs sm:text-sm text-white placeholder-slate-600 focus:outline-none focus:border-amber-400 uppercase font-mono tracking-wider"
              />
              <button
                type="submit"
                disabled={voucherLoading || !voucherCode.trim()}
                className="btn-tactile px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs shrink-0 transition disabled:opacity-40"
              >
                {voucherLoading ? 'Se verifică...' : 'Activează'}
              </button>
            </div>
          </form>
        </div>

        {/* Trust Badges */}
        <div className="mt-5 text-center flex items-center justify-center space-x-4 text-[10px] text-slate-500">
          <span className="flex items-center space-x-1">
            <ShieldCheck className="w-3.5 h-3.5 text-slate-400" />
            <span>Facturare securizată în LEI</span>
          </span>
          <span>•</span>
          <span>Anulare oricând</span>
          <span>•</span>
          <span>Suport dedicat România</span>
        </div>

      </div>
    </div>
  );
}
