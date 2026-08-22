import React, { useEffect, useState, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { Shield, ShieldAlert, ShieldCheck, Zap, Activity, RefreshCw } from 'lucide-react';

interface SecurityHealthGaugeProps {
  score: number;
  grade: string;
  isScanning?: boolean;
  scanProgress?: number;
  criticalIssuesCount?: number;
  reusedCount?: number;
  weakCount?: number;
  onRunScan?: () => void;
  onAutoFixAll?: () => void;
  isDarkMode?: boolean;
  size?: 'sm' | 'md' | 'lg';
  showDetails?: boolean;
}

export const SecurityHealthGauge: React.FC<SecurityHealthGaugeProps> = ({
  score,
  grade,
  isScanning = false,
  scanProgress = 0,
  criticalIssuesCount = 0,
  reusedCount = 0,
  weakCount = 0,
  onRunScan,
  onAutoFixAll,
  isDarkMode = true,
  size = 'md',
  showDetails = true,
}) => {
  // Smooth animated counter mirroring animateFloatAsState in Jetpack Compose
  const [displayScore, setDisplayScore] = useState<number>(score);
  const prevScoreRef = useRef<number>(score);
  const [isScoreBursting, setIsScoreBursting] = useState<boolean>(false);

  useEffect(() => {
    if (prevScoreRef.current !== score) {
      setIsScoreBursting(true);
      const timer = setTimeout(() => setIsScoreBursting(false), 1200);

      // Smooth step interpolation
      const startScore = prevScoreRef.current;
      const endScore = score;
      const duration = 800; // ms
      const startTime = performance.now();

      const updateCounter = (now: number) => {
        const elapsed = now - startTime;
        const progress = Math.min(elapsed / duration, 1);
        // EaseOutQuart function for natural spring-like deceleration
        const easeOut = 1 - Math.pow(1 - progress, 4);
        const currentVal = Math.round(startScore + (endScore - startScore) * easeOut);
        setDisplayScore(currentVal);

        if (progress < 1) {
          requestAnimationFrame(updateCounter);
        } else {
          setDisplayScore(endScore);
          prevScoreRef.current = endScore;
        }
      };

      requestAnimationFrame(updateCounter);
      return () => clearTimeout(timer);
    } else {
      setDisplayScore(score);
    }
  }, [score]);

  // Color mappings based on security health thresholds
  const getThemeColors = (val: number) => {
    if (val >= 85) {
      return {
        primary: '#10B981', // Emerald 500
        secondary: '#059669',
        glow: 'rgba(16, 185, 129, 0.4)',
        bgTint: 'rgba(16, 185, 129, 0.12)',
        border: 'border-emerald-500/40',
        text: 'text-emerald-400',
        badgeBg: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/40',
      };
    }
    if (val >= 60) {
      return {
        primary: '#F59E0B', // Amber 500
        secondary: '#D97706',
        glow: 'rgba(245, 158, 11, 0.4)',
        bgTint: 'rgba(245, 158, 11, 0.12)',
        border: 'border-amber-500/40',
        text: 'text-amber-400',
        badgeBg: 'bg-amber-500/15 text-amber-300 border-amber-500/40',
      };
    }
    return {
      primary: '#E50914', // Cinematic Crimson Red
      secondary: '#DC2626',
      glow: 'rgba(229, 9, 20, 0.45)',
      bgTint: 'rgba(229, 9, 20, 0.12)',
      border: 'border-obscura-crimson/50',
      text: 'text-obscura-crimson',
      badgeBg: 'bg-red-500/15 text-red-400 border-red-500/40',
    };
  };

  const colors = getThemeColors(score);

  // SVG Geometry Calculations (270° arc gauge from -135° to +135°)
  const radius = 38;
  const circumference = 2 * Math.PI * radius;
  const arcLength = circumference * 0.75; // 270 degrees arc
  const strokeDashoffset = arcLength - (arcLength * Math.min(Math.max(score, 0), 100)) / 100;

  // Needle and Indicator rotation angle: maps 0..100 to -135° .. +135°
  const needleRotation = (Math.min(Math.max(score, 0), 100) / 100) * 270 - 135;

  return (
    <div
      className={`relative overflow-hidden rounded-2xl border transition-all duration-500 ${
        isDarkMode
          ? 'bg-[#121212] border-obscura-border text-white shadow-xl shadow-black/40'
          : 'bg-white border-slate-200 text-slate-900 shadow-md'
      }`}
    >
      {/* Background Decorative Ambient Radial Glow */}
      <motion.div
        animate={{
          scale: isScanning ? [1, 1.25, 1] : isScoreBursting ? [1, 1.35, 1] : 1,
          opacity: isScanning ? 0.35 : isScoreBursting ? 0.25 : 0.12,
        }}
        transition={{ duration: isScanning ? 1.2 : 0.8, repeat: isScanning ? Infinity : 0 }}
        className="absolute -top-10 -left-10 w-44 h-44 rounded-full pointer-events-none blur-3xl"
        style={{ background: colors.glow }}
      />

      <div className="p-4 flex flex-col sm:flex-row items-center gap-4 relative z-10">
        {/* Animated Radial Gauge Viewport */}
        <div className="relative w-28 h-28 shrink-0 flex items-center justify-center select-none">
          {/* Outer Scanner Sweep Radar Ring (Active during diagnostic coroutine) */}
          {isScanning && (
            <motion.div
              animate={{ rotate: 360 }}
              transition={{ repeat: Infinity, duration: 1.4, ease: 'linear' }}
              className="absolute inset-0 rounded-full border-2 border-dashed border-obscura-crimson/50 pointer-events-none"
            />
          )}

          {/* Calibrated Tick Marks Ring */}
          <svg className="w-28 h-28 absolute inset-0 overflow-visible" viewBox="0 0 100 100">
            <defs>
              <linearGradient id="obscuraGaugeGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stopColor="#E50914" />
                <stop offset="50%" stopColor="#F59E0B" />
                <stop offset="100%" stopColor="#10B981" />
              </linearGradient>
              <filter id="gaugeGlow">
                <feGaussianBlur stdDeviation="2.5" result="coloredBlur" />
                <feMerge>
                  <feMergeNode in="coloredBlur" />
                  <feMergeNode in="SourceGraphic" />
                </feMerge>
              </filter>
            </defs>

            {/* Background Track Arc (270°) */}
            <circle
              cx="50"
              cy="50"
              r={radius}
              stroke={isDarkMode ? '#1e1e1e' : '#e2e8f0'}
              strokeWidth="7"
              fill="transparent"
              strokeDasharray={`${arcLength} ${circumference}`}
              strokeDashoffset={0}
              strokeLinecap="round"
              transform="rotate(135 50 50)"
            />

            {/* Smooth Animated Progress Arc (using motion/react spring transition) */}
            <motion.circle
              cx="50"
              cy="50"
              r={radius}
              stroke="url(#obscuraGaugeGradient)"
              strokeWidth="7.5"
              fill="transparent"
              strokeDasharray={`${arcLength} ${circumference}`}
              initial={{ strokeDashoffset: arcLength }}
              animate={{ strokeDashoffset }}
              transition={{
                type: 'spring',
                stiffness: 140,
                damping: 24,
                restDelta: 0.001,
              }}
              strokeLinecap="round"
              transform="rotate(135 50 50)"
              filter={isScoreBursting ? 'url(#gaugeGlow)' : undefined}
            />

            {/* Minor Tick Mark Lines at 0%, 25%, 50%, 75%, 100% */}
            {[0, 25, 50, 75, 100].map((tick) => {
              const angle = (tick / 100) * 270 - 135;
              const rad = (angle - 90) * (Math.PI / 180);
              const x1 = 50 + (radius - 8) * Math.cos(rad);
              const y1 = 50 + (radius - 8) * Math.sin(rad);
              const x2 = 50 + (radius - 3) * Math.cos(rad);
              const y2 = 50 + (radius - 3) * Math.sin(rad);
              const isPassed = score >= tick;

              return (
                <line
                  key={tick}
                  x1={x1}
                  y1={y1}
                  x2={x2}
                  y2={y2}
                  stroke={isPassed ? colors.primary : isDarkMode ? '#333333' : '#cbd5e1'}
                  strokeWidth={tick === 0 || tick === 100 ? '2' : '1.2'}
                  strokeLinecap="round"
                  className="transition-colors duration-300"
                />
              );
            })}
          </svg>

          {/* Smooth Rotating Needle & Glowing Marker (Animatable / animateFloatAsState reproduction) */}
          <motion.div
            className="absolute inset-0 flex items-center justify-center pointer-events-none"
            initial={{ rotate: -135 }}
            animate={{ rotate: needleRotation }}
            transition={{
              type: 'spring',
              stiffness: 180,
              damping: 22,
              mass: 0.8,
            }}
          >
            {/* Needle Pivot Center & Pointer Beacon */}
            <div className="relative w-full h-full flex items-center justify-center">
              {/* Outer Pointer Node Orbit */}
              <div
                className="absolute top-1 w-3.5 h-3.5 rounded-full border-2 border-white shadow-lg flex items-center justify-center transition-colors"
                style={{
                  backgroundColor: colors.primary,
                  boxShadow: `0 0 10px ${colors.glow}`,
                }}
              >
                <div className="w-1 h-1 bg-white rounded-full" />
              </div>

              {/* Center Hub */}
              <div
                className={`w-4 h-4 rounded-full border shadow-md ${
                  isDarkMode ? 'bg-[#181818] border-white/20' : 'bg-white border-slate-300'
                }`}
              />
            </div>
          </motion.div>

          {/* Center Numeric Score Display & Grade */}
          <div className="absolute flex flex-col items-center justify-center text-center pointer-events-none mt-1">
            <motion.span
              key={displayScore}
              initial={{ scale: 0.85, opacity: 0.6 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ type: 'spring', stiffness: 400, damping: 25 }}
              className={`text-xl font-black font-mono tracking-tight leading-none ${
                isDarkMode ? 'text-white' : 'text-slate-900'
              }`}
            >
              {displayScore}
            </motion.span>
            <span
              className="text-[9px] font-mono font-bold uppercase tracking-widest mt-0.5"
              style={{ color: colors.primary }}
            >
              / 100
            </span>
          </div>
        </div>

        {/* Health Details, Metrics, & Auto-Fix Action */}
        <div className="flex-1 w-full space-y-2">
          {/* Header Row */}
          <div className="flex items-center justify-between gap-2 flex-wrap">
            <div className="flex items-center gap-1.5">
              <Shield className="w-4 h-4 text-obscura-crimson stroke-[2.5]" />
              <h3
                className={`text-xs font-black uppercase tracking-wider font-mono ${
                  isDarkMode ? 'text-white' : 'text-slate-900'
                }`}
              >
                Security Health
              </h3>
            </div>

            {/* Dynamic Grade Chip with Animated Bounce */}
            <motion.div
              key={grade}
              initial={{ scale: 0.8, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ type: 'spring', stiffness: 350, damping: 20 }}
              className={`px-2.5 py-0.5 rounded-full text-[10px] font-mono font-extrabold border flex items-center gap-1.5 shadow-sm ${colors.badgeBg}`}
            >
              <span
                className={`w-1.5 h-1.5 rounded-full ${
                  score >= 85
                    ? 'bg-emerald-400'
                    : score >= 60
                    ? 'bg-amber-400'
                    : 'bg-red-500 animate-pulse'
                }`}
              />
              <span>GRADE {grade}</span>
            </motion.div>
          </div>

          {/* Diagnostic Subtext & Vulnerability Summary */}
          <p
            className={`text-[11px] leading-snug ${
              isDarkMode ? 'text-gray-300' : 'text-slate-600'
            }`}
          >
            {isScanning ? (
              <span className="text-obscura-crimson font-mono font-bold animate-pulse flex items-center gap-1">
                <Activity className="w-3.5 h-3.5 animate-spin" />
                Coroutine scanner inspecting Room DB ({scanProgress}%)...
              </span>
            ) : criticalIssuesCount > 0 ? (
              <span className="text-red-400 font-medium">
                <strong>{criticalIssuesCount} critical vulnerabilities</strong> require remediation in encrypted vault.
              </span>
            ) : (
              <span className="text-emerald-400 font-medium flex items-center gap-1">
                <ShieldCheck className="w-3.5 h-3.5" />
                All cryptographic secrets meet high Shannon Entropy standards.
              </span>
            )}
          </p>

          {/* Quick Metrics Bar in Details Mode */}
          {showDetails && (
            <div className="grid grid-cols-2 gap-1.5 pt-0.5">
              <div
                className={`px-2 py-1 rounded-lg border flex items-center justify-between text-[10px] font-mono ${
                  isDarkMode ? 'bg-[#181818] border-obscura-border' : 'bg-slate-50 border-slate-200'
                }`}
              >
                <span className="text-gray-400">Reused:</span>
                <span className={`font-bold ${reusedCount > 0 ? 'text-red-400' : 'text-emerald-400'}`}>
                  {reusedCount}
                </span>
              </div>
              <div
                className={`px-2 py-1 rounded-lg border flex items-center justify-between text-[10px] font-mono ${
                  isDarkMode ? 'bg-[#181818] border-obscura-border' : 'bg-slate-50 border-slate-200'
                }`}
              >
                <span className="text-gray-400">Weak/Short:</span>
                <span className={`font-bold ${weakCount > 0 ? 'text-amber-400' : 'text-emerald-400'}`}>
                  {weakCount}
                </span>
              </div>
            </div>
          )}

          {/* Action Toolbar */}
          <div className="flex items-center gap-2 pt-1">
            {onRunScan && (
              <button
                type="button"
                onClick={onRunScan}
                disabled={isScanning}
                className={`flex-1 py-1.5 px-2.5 rounded-xl border text-[10px] font-mono font-bold flex items-center justify-center gap-1.5 transition-all cursor-pointer ${
                  isDarkMode
                    ? 'bg-[#1a1a1a] hover:bg-[#252525] border-obscura-border text-gray-300 hover:text-white'
                    : 'bg-slate-100 hover:bg-slate-200 border-slate-200 text-slate-800'
                } disabled:opacity-50`}
              >
                <RefreshCw className={`w-3 h-3 ${isScanning ? 'animate-spin text-obscura-crimson' : ''}`} />
                <span>{isScanning ? 'Scanning...' : 'Re-Scan SSOT'}</span>
              </button>
            )}

            {onAutoFixAll && criticalIssuesCount > 0 && (
              <button
                type="button"
                onClick={onAutoFixAll}
                className="flex-1 py-1.5 px-2.5 bg-obscura-crimson hover:bg-obscura-crimsonHover text-black font-extrabold text-[10px] rounded-xl flex items-center justify-center gap-1.5 transition-all shadow-md shadow-obscura-crimson/25 cursor-pointer active:scale-98"
              >
                <Zap className="w-3 h-3 fill-current" />
                <span>Auto-Fix Weak</span>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
