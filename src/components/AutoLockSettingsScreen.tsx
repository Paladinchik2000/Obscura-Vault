import React, { useState, useEffect } from 'react';
import {
  Shield,
  Lock,
  Clock,
  Clipboard,
  ChevronLeft,
  CheckCircle2,
  AlertTriangle,
  RotateCcw,
  Sparkles,
  Smartphone,
  Sliders,
  Zap,
  Info,
  Timer,
  Fingerprint,
  Key,
  ShieldCheck
} from 'lucide-react';

export interface AutoLockSettingsProps {
  autoLockMinutes: number;
  onAutoLockMinutesChange: (minutes: number) => void;
  clipboardTimeoutMinutes: number;
  onClipboardTimeoutMinutesChange: (minutes: number) => void;
  lockOnBackground: boolean;
  onLockOnBackgroundChange: (enabled: boolean) => void;
  biometricOnResume: boolean;
  onBiometricOnResumeChange: (enabled: boolean) => void;
  sensitiveClipFlag: boolean;
  onSensitiveClipFlagChange: (enabled: boolean) => void;
  enforceStrongBiometricsOnly?: boolean;
  onEnforceStrongBiometricsOnlyChange?: (enabled: boolean) => void;
  onBack: () => void;
  onTriggerInstantLock: () => void;
  onClearClipboardNow: () => void;
  onAddLog: (message: string, type: 'info' | 'success' | 'error') => void;
  isDarkMode?: boolean;
}

const PRESET_MINUTES = [1, 2, 5, 10, 15, 30, 45, 60];

export const AutoLockSettingsScreen: React.FC<AutoLockSettingsProps> = ({
  autoLockMinutes,
  onAutoLockMinutesChange,
  clipboardTimeoutMinutes,
  onClipboardTimeoutMinutesChange,
  lockOnBackground,
  onLockOnBackgroundChange,
  biometricOnResume,
  onBiometricOnResumeChange,
  sensitiveClipFlag,
  onSensitiveClipFlagChange,
  enforceStrongBiometricsOnly = false,
  onEnforceStrongBiometricsOnlyChange,
  onBack,
  onTriggerInstantLock,
  onClearClipboardNow,
  onAddLog,
  isDarkMode = true,
}) => {
  // Local simulation timer
  const [remainingSeconds, setRemainingSeconds] = useState<number>(autoLockMinutes * 60);
  const [isSimulationActive, setIsSimulationActive] = useState<boolean>(true);
  const [testFastLockSeconds, setTestFastLockSeconds] = useState<number | null>(null);

  // Synchronize simulation seconds on minute change
  useEffect(() => {
    setRemainingSeconds(autoLockMinutes * 60);
  }, [autoLockMinutes]);

  // Live countdown ticker simulation
  useEffect(() => {
    if (!isSimulationActive) return;

    const interval = setInterval(() => {
      setRemainingSeconds((prev) => {
        if (prev <= 1) {
          return autoLockMinutes * 60; // loop simulation in demo
        }
        return prev - 1;
      });

      if (testFastLockSeconds !== null) {
        setTestFastLockSeconds((prev) => {
          if (prev === null) return null;
          if (prev <= 1) {
            onAddLog(`[AUTO_LOCK_TRIGGERED] 5-second fast test timeout expired. Triggering lock.`, 'info');
            onTriggerInstantLock();
            return null;
          }
          return prev - 1;
        });
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [isSimulationActive, autoLockMinutes, testFastLockSeconds, onTriggerInstantLock, onAddLog]);

  const formatCountdown = (totalSecs: number) => {
    const mins = Math.floor(totalSecs / 60);
    const secs = totalSecs % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const handleSliderChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = parseInt(e.target.value, 10);
    if (!isNaN(val) && val >= 1 && val <= 60) {
      onAutoLockMinutesChange(val);
      onAddLog(`[AUTO_LOCK_CONFIG] Vault Inactivity timeout set to ${val} minutes`, 'info');
    }
  };

  const handleClipboardSliderChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = parseInt(e.target.value, 10);
    if (!isNaN(val) && val >= 1 && val <= 60) {
      onClipboardTimeoutMinutesChange(val);
      onAddLog(`[CLIPBOARD_CONFIG] SecureClipboardManager auto-clear delay set to ${val} minutes (${val * 60}s)`, 'info');
    }
  };

  return (
    <div className="flex-1 overflow-y-auto p-3 space-y-3.5 animate-fade-in">
      {/* Top Header Navigation */}
      <div className="flex items-center justify-between pb-1">
        <button
          onClick={onBack}
          className={`flex items-center gap-1 text-xs font-bold font-mono px-2 py-1 rounded-lg border transition-all ${
            isDarkMode
              ? 'bg-[#181818] border-obscura-border text-gray-300 hover:text-white hover:border-obscura-crimson'
              : 'bg-slate-100 border-slate-200 text-slate-700 hover:text-slate-900'
          }`}
        >
          <ChevronLeft className="w-3.5 h-3.5" />
          <span>Back to Settings</span>
        </button>

        <span className="text-[9px] font-mono font-bold px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-500 border border-emerald-500/30">
          LIFECYCLE OBSERVER ACTIVE
        </span>
      </div>

      {/* Hero Card: Auto-Lock Overview */}
      <div
        className={`border rounded-2xl p-4 space-y-3 transition-colors ${
          isDarkMode
            ? 'bg-gradient-to-br from-[#161616] to-[#0d0d0d] border-obscura-border shadow-md'
            : 'bg-gradient-to-br from-white to-slate-50 border-slate-200 shadow-sm'
        }`}
      >
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-obscura-crimson/20 border border-obscura-crimson/40 text-obscura-crimson">
              <Clock className="w-5 h-5" />
            </div>
            <div>
              <h2 className={`text-sm font-extrabold tracking-wide ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
                Auto-Lock & Timeout Guards
              </h2>
              <p className={`text-[10px] font-mono ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>
                ProcessLifecycleOwner & SecureClipboardManager SSOT
              </p>
            </div>
          </div>
          <div className="text-right">
            <span className="text-xs font-mono font-black text-obscura-crimson block">
              {autoLockMinutes} min
            </span>
            <span className="text-[8px] font-mono text-gray-500 uppercase">Timeout</span>
          </div>
        </div>

        <p className={`text-[11px] leading-relaxed ${isDarkMode ? 'text-gray-300' : 'text-slate-600'}`}>
          Configure automatic vault protection and clipboard memory auto-clearing. Inactive sessions trigger AES-256 memory wipes, and sensitive clipboard payloads are automatically purged.
        </p>

        {/* Live Inactivity Countdown Meter */}
        <div
          className={`p-3 rounded-xl border flex items-center justify-between ${
            isDarkMode ? 'bg-[#0f0f0f] border-obscura-border/60' : 'bg-slate-100 border-slate-200'
          }`}
        >
          <div className="flex items-center gap-2">
            <Timer className="w-4 h-4 text-obscura-crimson animate-pulse" />
            <div>
              <div className="text-[10px] font-bold font-mono">Inactivity Idle Countdown</div>
              <div className="text-[9px] text-gray-500 font-mono">Simulated background worker</div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-sm font-mono font-black text-obscura-crimson tracking-wider">
              {testFastLockSeconds !== null ? `00:0${testFastLockSeconds}` : formatCountdown(remainingSeconds)}
            </span>
            <button
              onClick={() => {
                setRemainingSeconds(autoLockMinutes * 60);
                onAddLog(`[AUTO_LOCK] Inactivity idle timer reset to ${autoLockMinutes} minutes`, 'info');
              }}
              className="p-1 rounded bg-white/5 hover:bg-white/10 text-gray-400 hover:text-white"
              title="Reset Timer"
            >
              <RotateCcw className="w-3 h-3" />
            </button>
          </div>
        </div>
      </div>

      {/* SECTION 1: VAULT INACTIVITY TIMEOUT (1 to 60 minutes) */}
      <div
        className={`border rounded-2xl p-4 space-y-3.5 transition-colors ${
          isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-sm'
        }`}
      >
        <div className="flex items-center justify-between border-b pb-2.5 border-inherit">
          <div className="flex items-center gap-2">
            <Lock className={`w-4 h-4 ${isDarkMode ? 'text-obscura-crimson' : 'text-red-600'}`} />
            <h3 className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
              Vault Auto-Lock Duration (1 - 60 min)
            </h3>
          </div>
          <span className="text-[10px] font-mono font-black text-obscura-crimson px-2 py-0.5 rounded bg-obscura-crimson/10 border border-obscura-crimson/30">
            {autoLockMinutes} {autoLockMinutes === 1 ? 'Minute' : 'Minutes'}
          </span>
        </div>

        {/* Range Slider (1 to 60) */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-[10px] font-mono text-gray-400">
            <span>1 min (Tight Security)</span>
            <span>30 min</span>
            <span>60 min (Extended)</span>
          </div>

          <input
            type="range"
            min="1"
            max="60"
            step="1"
            value={autoLockMinutes}
            onChange={handleSliderChange}
            className="w-full accent-obscura-crimson cursor-pointer h-2 bg-gray-800 rounded-lg appearance-none"
          />

          <div className="flex items-center justify-between pt-1">
            <span className={`text-[10px] ${isDarkMode ? 'text-gray-400' : 'text-slate-600'}`}>
              Locks master encryption key after no touch/typing detected.
            </span>
          </div>
        </div>

        {/* Quick Presets Grid */}
        <div>
          <label className={`text-[10px] font-bold font-mono uppercase tracking-wider block mb-1.5 ${
            isDarkMode ? 'text-gray-400' : 'text-slate-600'
          }`}>
            Quick Duration Presets:
          </label>
          <div className="grid grid-cols-4 gap-1.5">
            {PRESET_MINUTES.map((mins) => (
              <button
                key={mins}
                onClick={() => {
                  onAutoLockMinutesChange(mins);
                  onAddLog(`[AUTO_LOCK] Selected preset: ${mins} minutes`, 'info');
                }}
                className={`py-1.5 px-1 rounded-lg border text-[10px] font-mono font-bold transition-all ${
                  autoLockMinutes === mins
                    ? 'bg-obscura-crimson text-black font-black border-obscura-crimson shadow-xs scale-102'
                    : isDarkMode
                    ? 'bg-[#181818] border-obscura-border text-gray-300 hover:text-white'
                    : 'bg-slate-50 border-slate-200 text-slate-700 hover:bg-slate-100'
                }`}
              >
                {mins} min
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* SECTION 2: SECURE CLIPBOARD MANAGER TIMEOUT (1 to 60 minutes) */}
      <div
        className={`border rounded-2xl p-4 space-y-3.5 transition-colors ${
          isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-sm'
        }`}
      >
        <div className="flex items-center justify-between border-b pb-2.5 border-inherit">
          <div className="flex items-center gap-2">
            <Clipboard className={`w-4 h-4 ${isDarkMode ? 'text-cyan-400' : 'text-cyan-600'}`} />
            <h3 className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
              SecureClipboardManager Auto-Clear (1 - 60 min)
            </h3>
          </div>
          <span className="text-[10px] font-mono font-black text-cyan-400 px-2 py-0.5 rounded bg-cyan-500/10 border border-cyan-500/30">
            {clipboardTimeoutMinutes} {clipboardTimeoutMinutes === 1 ? 'Minute' : 'Minutes'} ({clipboardTimeoutMinutes * 60}s)
          </span>
        </div>

        <p className={`text-[10px] leading-relaxed ${isDarkMode ? 'text-gray-400' : 'text-slate-600'}`}>
          Schedules automatic clipboard destruction via Kotlin Coroutine <code>externalScope.launch {'{ delay(...) }'}</code>. If content remains unchanged, the system clipboard is securely wiped.
        </p>

        {/* Clipboard Range Slider (1 to 60) */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-[10px] font-mono text-gray-400">
            <span>1 min (Default 60s)</span>
            <span>15 min</span>
            <span>60 min (Max)</span>
          </div>

          <input
            type="range"
            min="1"
            max="60"
            step="1"
            value={clipboardTimeoutMinutes}
            onChange={handleClipboardSliderChange}
            className="w-full accent-cyan-400 cursor-pointer h-2 bg-gray-800 rounded-lg appearance-none"
          />
        </div>

        {/* Clipboard Presets */}
        <div className="grid grid-cols-4 gap-1.5">
          {PRESET_MINUTES.map((mins) => (
            <button
              key={mins}
              onClick={() => {
                onClipboardTimeoutMinutesChange(mins);
                onAddLog(`[CLIPBOARD_AUTO_CLEAR] Preset updated to ${mins} minutes`, 'info');
              }}
              className={`py-1.5 px-1 rounded-lg border text-[10px] font-mono font-bold transition-all ${
                clipboardTimeoutMinutes === mins
                  ? 'bg-cyan-400 text-black font-black border-cyan-400 shadow-xs'
                  : isDarkMode
                  ? 'bg-[#181818] border-obscura-border text-gray-300 hover:text-white'
                  : 'bg-slate-50 border-slate-200 text-slate-700 hover:bg-slate-100'
              }`}
            >
              {mins}m clear
            </button>
          ))}
        </div>

        {/* Instant Clipboard Wipe Button */}
        <button
          onClick={() => {
            onClearClipboardNow();
            onAddLog(`[SECURE_CLIPBOARD] Immediate clipboard purge executed via SecureClipboardManager.clearImmediately()`, 'success');
          }}
          className="w-full py-2 bg-cyan-500/10 hover:bg-cyan-500/20 border border-cyan-500/30 text-cyan-300 font-bold text-xs rounded-xl flex items-center justify-center gap-1.5 transition-all"
        >
          <RotateCcw className="w-3.5 h-3.5 text-cyan-400" />
          <span>Wipe System Clipboard Immediately</span>
        </button>
      </div>

      {/* SECTION 3: ADVANCED LIFECYCLE & PRIVACY TOGGLES */}
      <div
        className={`border rounded-2xl p-4 space-y-3 transition-colors ${
          isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-sm'
        }`}
      >
        <div className="flex items-center gap-2 border-b pb-2.5 border-inherit">
          <Smartphone className={`w-4 h-4 ${isDarkMode ? 'text-amber-400' : 'text-amber-600'}`} />
          <h3 className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
            Android Lifecycle & System Guard Toggles
          </h3>
        </div>

        {/* Toggle 1: Lock on Background */}
        <div className="flex items-center justify-between gap-2 pt-1">
          <div>
            <div className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-800'}`}>
              Lock on App Backgrounding
            </div>
            <div className={`text-[10px] ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>
              Triggers on <code>ProcessLifecycleOwner.ON_STOP</code> event
            </div>
          </div>
          <button
            onClick={() => {
              onLockOnBackgroundChange(!lockOnBackground);
              onAddLog(`Lock on Background toggled: ${!lockOnBackground ? 'ENABLED' : 'DISABLED'}`, 'info');
            }}
            className={`w-11 h-6 rounded-full p-0.5 transition-colors cursor-pointer ${
              lockOnBackground ? 'bg-obscura-crimson' : isDarkMode ? 'bg-gray-700' : 'bg-slate-300'
            }`}
          >
            <div
              className={`w-5 h-5 rounded-full bg-white transition-transform ${
                lockOnBackground ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Toggle 2: Biometric Gate on Resume */}
        <div className="flex items-center justify-between gap-2 pt-2 border-t border-inherit">
          <div>
            <div className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-800'}`}>
              Require Biometrics on Resume
            </div>
            <div className={`text-[10px] ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>
              Immediate <code>BiometricPrompt</code> challenge when returning to app
            </div>
          </div>
          <button
            onClick={() => {
              onBiometricOnResumeChange(!biometricOnResume);
              onAddLog(`Biometric on Resume toggled: ${!biometricOnResume ? 'ENABLED' : 'DISABLED'}`, 'info');
            }}
            className={`w-11 h-6 rounded-full p-0.5 transition-colors cursor-pointer ${
              biometricOnResume ? 'bg-emerald-500' : isDarkMode ? 'bg-gray-700' : 'bg-slate-300'
            }`}
          >
            <div
              className={`w-5 h-5 rounded-full bg-white transition-transform ${
                biometricOnResume ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Toggle 3: Android 13+ EXTRA_IS_SENSITIVE */}
        <div className="flex items-center justify-between gap-2 pt-2 border-t border-inherit">
          <div>
            <div className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-800'}`}>
              ClipDescription.EXTRA_IS_SENSITIVE
            </div>
            <div className={`text-[10px] ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>
              Android 13+ (API 33) suppresses clipboard preview overlay
            </div>
          </div>
          <button
            onClick={() => {
              onSensitiveClipFlagChange(!sensitiveClipFlag);
              onAddLog(`EXTRA_IS_SENSITIVE flag toggled: ${!sensitiveClipFlag ? 'ENABLED' : 'DISABLED'}`, 'info');
            }}
            className={`w-11 h-6 rounded-full p-0.5 transition-colors cursor-pointer ${
              sensitiveClipFlag ? 'bg-indigo-500' : isDarkMode ? 'bg-gray-700' : 'bg-slate-300'
            }`}
          >
            <div
              className={`w-5 h-5 rounded-full bg-white transition-transform ${
                sensitiveClipFlag ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Toggle 4: Enforce Strong Biometrics Only (BIOMETRIC_STRONG only, NO DEVICE_CREDENTIAL fallback) */}
        <div className={`pt-2.5 mt-1 border-t border-inherit p-2.5 rounded-xl border transition-all ${
          enforceStrongBiometricsOnly
            ? isDarkMode
              ? 'bg-obscura-crimson/10 border-obscura-crimson/40 ring-1 ring-obscura-crimson/20'
              : 'bg-red-50/70 border-red-200 ring-1 ring-red-200'
            : isDarkMode
              ? 'bg-[#0d0d0d] border-obscura-border/60'
              : 'bg-slate-50 border-slate-200'
        }`}>
          <div className="flex items-start justify-between gap-2">
            <div className="flex items-start gap-2">
              <div className={`p-1.5 rounded-lg shrink-0 mt-0.5 ${
                enforceStrongBiometricsOnly
                  ? 'bg-obscura-crimson text-black font-extrabold shadow-xs'
                  : isDarkMode ? 'bg-[#1e1e1e] text-gray-400' : 'bg-slate-200 text-slate-600'
              }`}>
                <Fingerprint className="w-4 h-4" />
              </div>
              <div className="space-y-1">
                <div className="flex items-center gap-1.5 flex-wrap">
                  <span className={`text-xs font-extrabold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
                    Enforce Strong Biometrics Only
                  </span>
                  <span className={`text-[8.5px] font-mono px-1.5 py-0.2 rounded font-extrabold border ${
                    enforceStrongBiometricsOnly
                      ? 'bg-obscura-crimson/20 text-obscura-crimson border-obscura-crimson/50 animate-pulse'
                      : 'bg-gray-500/10 text-gray-400 border-gray-500/20'
                  }`}>
                    {enforceStrongBiometricsOnly ? 'BIOMETRIC_STRONG ONLY' : 'FALLBACK ALLOWED'}
                  </span>
                </div>
                <p className={`text-[10px] leading-snug ${isDarkMode ? 'text-gray-300' : 'text-slate-600'}`}>
                  Restricts <code className="text-obscura-crimson font-mono text-[9px]">BiometricPrompt.PromptInfo</code> to Class 3 hardware biometrics (<code className="font-mono text-[9px]">BIOMETRIC_STRONG</code>). Explicitly disables device PIN/pattern (<code className="font-mono text-[9px]">DEVICE_CREDENTIAL</code>) fallback and requires negative cancellation button.
                </p>
                {enforceStrongBiometricsOnly && (
                  <div className="flex items-center gap-1 text-[9px] font-mono text-emerald-400 pt-0.5">
                    <ShieldCheck className="w-3 h-3 text-emerald-400" />
                    <span>PromptInfo: setAllowedAuthenticators(BIOMETRIC_STRONG) + setNegativeButtonText("Cancel")</span>
                  </div>
                )}
              </div>
            </div>
            <button
              onClick={() => {
                const nextState = !enforceStrongBiometricsOnly;
                if (onEnforceStrongBiometricsOnlyChange) {
                  onEnforceStrongBiometricsOnlyChange(nextState);
                }
                onAddLog(
                  `[BIOMETRIC_POLICY] Enforce Strong Biometrics Only: ${nextState ? 'ENABLED (BIOMETRIC_STRONG only, DEVICE_CREDENTIAL stripped)' : 'DISABLED (BIOMETRIC_STRONG | DEVICE_CREDENTIAL enabled)'}`,
                  nextState ? 'success' : 'info'
                );
              }}
              className={`w-11 h-6 rounded-full p-0.5 transition-colors cursor-pointer shrink-0 ${
                enforceStrongBiometricsOnly ? 'bg-obscura-crimson shadow-md shadow-obscura-crimson/30' : isDarkMode ? 'bg-gray-700' : 'bg-slate-300'
              }`}
              title="Toggle Enforce Strong Biometrics Only"
            >
              <div
                className={`w-5 h-5 rounded-full bg-white transition-transform ${
                  enforceStrongBiometricsOnly ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>
        </div>
      </div>

      {/* SECTION 4: INTERACTIVE TESTING & IMMEDIATE LOCK ACTIONS */}
      <div
        className={`border rounded-2xl p-4 space-y-3 transition-colors ${
          isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-sm'
        }`}
      >
        <div className="flex items-center gap-2 border-b pb-2.5 border-inherit">
          <Zap className="w-4 h-4 text-obscura-crimson" />
          <h3 className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
            Live Auto-Lock Simulation & Testing
          </h3>
        </div>

        <div className="grid grid-cols-2 gap-2">
          {/* 5-second Test Trigger */}
          <button
            onClick={() => {
              setTestFastLockSeconds(5);
              onAddLog(`[AUTO_LOCK_TEST] Starting 5-second fast auto-lock simulation timer...`, 'info');
            }}
            className="py-2.5 px-2 bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 text-amber-300 font-bold text-[11px] rounded-xl flex items-center justify-center gap-1.5 transition-all"
          >
            <Clock className="w-3.5 h-3.5 text-amber-400" />
            <span>Test 5s Auto-Lock</span>
          </button>

          {/* Immediate Lock */}
          <button
            onClick={() => {
              onAddLog(`[MANUAL_LOCK] Lock now invoked from Auto-Lock configuration screen`, 'info');
              onTriggerInstantLock();
            }}
            className="py-2.5 px-2 bg-obscura-crimson/15 hover:bg-obscura-crimson/25 border border-obscura-crimson/40 text-obscura-crimson font-bold text-[11px] rounded-xl flex items-center justify-center gap-1.5 transition-all"
          >
            <Lock className="w-3.5 h-3.5" />
            <span>Lock Vault Now</span>
          </button>
        </div>
      </div>
    </div>
  );
};
