import React from 'react';
import { motion } from 'motion/react';
import { ShieldCheck, AlertTriangle, ShieldAlert, Sparkles, Check, X } from 'lucide-react';

export interface EntropyAnalysis {
  entropyBits: number;
  poolSize: number;
  length: number;
  hasLower: boolean;
  hasUpper: boolean;
  hasDigits: boolean;
  hasSymbols: boolean;
  score: number; // 0 to 100
  tier: 'VERY_WEAK' | 'WEAK' | 'FAIR' | 'STRONG' | 'VERY_STRONG';
  crackTimeEstimate: string;
  feedback: string[];
}

export function calculatePasswordEntropy(password: string): EntropyAnalysis {
  if (!password || password.length === 0) {
    return {
      entropyBits: 0,
      poolSize: 0,
      length: 0,
      hasLower: false,
      hasUpper: false,
      hasDigits: false,
      hasSymbols: false,
      score: 0,
      tier: 'VERY_WEAK',
      crackTimeEstimate: 'Instant',
      feedback: ['Enter a secret to evaluate cryptographic strength']
    };
  }

  const hasLower = /[a-z]/.test(password);
  const hasUpper = /[A-Z]/.test(password);
  const hasDigits = /[0-9]/.test(password);
  const hasSymbols = /[^a-zA-Z0-9]/.test(password);

  let poolSize = 0;
  if (hasLower) poolSize += 26;
  if (hasUpper) poolSize += 26;
  if (hasDigits) poolSize += 10;
  if (hasSymbols) poolSize += 33; // Standard ASCII symbols & specials

  // Shannon Entropy: E = L * log2(R)
  const entropyBits = poolSize > 0 ? Math.round(password.length * Math.log2(poolSize) * 10) / 10 : 0;

  // Deductions for repetitive or obvious sequences
  let penalty = 0;
  const lowerPwd = password.toLowerCase();
  const commonSequences = ['123', 'abc', 'qwerty', 'password', 'admin', '111', '000', 'pass'];
  commonSequences.forEach(seq => {
    if (lowerPwd.includes(seq)) penalty += 12;
  });

  if (/(.)\1{2,}/.test(password)) {
    // 3 or more repeating characters
    penalty += 10;
  }

  const effectiveBits = Math.max(0, entropyBits - penalty);

  // Determine Tier & Score
  let tier: EntropyAnalysis['tier'] = 'VERY_WEAK';
  let score = 0;
  let crackTimeEstimate = 'Instant';

  if (effectiveBits < 28) {
    tier = 'VERY_WEAK';
    score = Math.min(25, Math.round((effectiveBits / 28) * 25));
    crackTimeEstimate = '< 1 second';
  } else if (effectiveBits < 45) {
    tier = 'WEAK';
    score = Math.round(25 + ((effectiveBits - 28) / (45 - 28)) * 25);
    crackTimeEstimate = 'Few minutes';
  } else if (effectiveBits < 65) {
    tier = 'FAIR';
    score = Math.round(50 + ((effectiveBits - 45) / (65 - 45)) * 25);
    crackTimeEstimate = 'Several months';
  } else if (effectiveBits < 85) {
    tier = 'STRONG';
    score = Math.round(75 + ((effectiveBits - 65) / (85 - 65)) * 18);
    crackTimeEstimate = 'Centuries (10^4 yrs)';
  } else {
    tier = 'VERY_STRONG';
    score = Math.min(100, Math.round(93 + ((effectiveBits - 85) / 40) * 7));
    crackTimeEstimate = 'Undecillion Millennia (10^12+ yrs)';
  }

  // Construct recommendations
  const feedback: string[] = [];
  if (password.length < 12) {
    feedback.push(`Increase length (${password.length}/14+ recommended)`);
  }
  if (!hasUpper) feedback.push('Add uppercase characters (A-Z)');
  if (!hasLower) feedback.push('Add lowercase characters (a-z)');
  if (!hasDigits) feedback.push('Add numeric digits (0-9)');
  if (!hasSymbols) feedback.push('Add special symbols (!@#$%)');
  if (penalty > 0) feedback.push('Avoid sequential or repeating patterns');

  return {
    entropyBits,
    poolSize,
    length: password.length,
    hasLower,
    hasUpper,
    hasDigits,
    hasSymbols,
    score,
    tier,
    crackTimeEstimate,
    feedback
  };
}

interface PasswordStrengthIndicatorProps {
  secret: string;
  isCompact?: boolean;
  showChecklist?: boolean;
  className?: string;
}

export const PasswordStrengthIndicator: React.FC<PasswordStrengthIndicatorProps> = ({
  secret,
  isCompact = false,
  showChecklist = true,
  className = ''
}) => {
  const analysis = calculatePasswordEntropy(secret);

  if (!secret) {
    return null;
  }

  const getTierDetails = (tier: EntropyAnalysis['tier']) => {
    switch (tier) {
      case 'VERY_WEAK':
        return {
          label: 'VERY WEAK',
          color: 'text-red-500',
          bgColor: 'bg-red-500',
          borderBg: 'border-red-500/30',
          fillSegments: 1,
          icon: ShieldAlert
        };
      case 'WEAK':
        return {
          label: 'WEAK',
          color: 'text-amber-500',
          bgColor: 'bg-amber-500',
          borderBg: 'border-amber-500/30',
          fillSegments: 2,
          icon: AlertTriangle
        };
      case 'FAIR':
        return {
          label: 'MODERATE',
          color: 'text-yellow-400',
          bgColor: 'bg-yellow-400',
          borderBg: 'border-yellow-400/30',
          fillSegments: 3,
          icon: ShieldCheck
        };
      case 'STRONG':
        return {
          label: 'STRONG',
          color: 'text-emerald-400',
          bgColor: 'bg-emerald-400',
          borderBg: 'border-emerald-400/30',
          fillSegments: 4,
          icon: ShieldCheck
        };
      case 'VERY_STRONG':
        return {
          label: 'CRYPTOGRAPHIC FORTRESS',
          color: 'text-cyan-400',
          bgColor: 'bg-cyan-400',
          borderBg: 'border-cyan-400/30',
          fillSegments: 4,
          icon: Sparkles
        };
    }
  };

  const tierInfo = getTierDetails(analysis.tier);
  const TierIcon = tierInfo.icon;

  if (isCompact) {
    return (
      <div className={`mt-1.5 p-2 bg-[#0d0d0d] border border-obscura-border/70 rounded-xl space-y-1.5 ${className}`}>
        <div className="flex items-center justify-between text-[10px] font-mono">
          <div className="flex items-center gap-1.5">
            <TierIcon className={`w-3.5 h-3.5 ${tierInfo.color}`} />
            <span className={`font-bold ${tierInfo.color}`}>{tierInfo.label}</span>
          </div>
          <div className="flex items-center gap-2 text-gray-400">
            <span>{analysis.entropyBits} bits</span>
            <span className="text-gray-600">•</span>
            <span className="text-gray-300 font-semibold">{analysis.score}%</span>
          </div>
        </div>

        {/* 4-Segment Progress Bar */}
        <div className="grid grid-cols-4 gap-1 h-1.5">
          {[1, 2, 3, 4].map((seg) => (
            <motion.div
              key={seg}
              initial={false}
              animate={{
                backgroundColor: seg <= tierInfo.fillSegments ? (seg === 4 && analysis.tier === 'VERY_STRONG' ? '#22d3ee' : seg === 4 ? '#34d399' : seg === 3 ? '#facc15' : seg === 2 ? '#f59e0b' : '#ef4444') : '#222222'
              }}
              transition={{ duration: 0.2 }}
              className="h-full rounded-full"
            />
          ))}
        </div>
      </div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: -4 }}
      animate={{ opacity: 1, y: 0 }}
      className={`mt-2 p-2.5 bg-[#0a0a0a] border border-obscura-border rounded-xl space-y-2 font-mono ${className}`}
    >
      {/* Top Header Row with Live Entropy & Score */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <div className={`p-1 rounded-md bg-white/5 border ${tierInfo.borderBg}`}>
            <TierIcon className={`w-3.5 h-3.5 ${tierInfo.color}`} />
          </div>
          <div>
            <div className="flex items-center gap-1.5">
              <span className={`text-[11px] font-extrabold tracking-wider ${tierInfo.color}`}>
                {tierInfo.label}
              </span>
              <span className="text-[9px] px-1.5 py-0.2 rounded bg-white/10 text-gray-300">
                {analysis.score}/100
              </span>
            </div>
            <span className="text-[9px] text-gray-500 block">
              Shannon Entropy: <strong className="text-gray-300">{analysis.entropyBits} bits</strong> (Pool: {analysis.poolSize})
            </span>
          </div>
        </div>

        <div className="text-right">
          <span className="text-[9px] text-gray-500 block">Crack Time (RTX 4090):</span>
          <span className="text-[10px] text-gray-200 font-bold">{analysis.crackTimeEstimate}</span>
        </div>
      </div>

      {/* 4-Step Animated Strength Bar */}
      <div className="space-y-1">
        <div className="grid grid-cols-4 gap-1.5 h-1.5">
          {[1, 2, 3, 4].map((seg) => {
            const isActive = seg <= tierInfo.fillSegments;
            const segmentColor = seg === 4 && analysis.tier === 'VERY_STRONG' ? '#22d3ee' : seg === 4 ? '#34d399' : seg === 3 ? '#facc15' : seg === 2 ? '#f59e0b' : '#ef4444';
            return (
              <div
                key={seg}
                className="h-full rounded-full overflow-hidden bg-[#1f1f1f]"
              >
                <motion.div
                  initial={false}
                  animate={{
                    width: isActive ? '100%' : '0%',
                    backgroundColor: segmentColor
                  }}
                  transition={{ duration: 0.25, ease: 'easeOut' }}
                  className="h-full"
                />
              </div>
            );
          })}
        </div>
      </div>

      {/* Character Variety Badges */}
      <div className="grid grid-cols-4 gap-1 pt-0.5">
        <div className={`py-0.5 px-1 rounded flex items-center justify-center gap-1 text-[9px] border ${analysis.hasLower ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-400' : 'bg-[#141414] border-gray-800 text-gray-600'}`}>
          {analysis.hasLower ? <Check className="w-2.5 h-2.5 stroke-[3]" /> : <X className="w-2.5 h-2.5" />}
          <span>a-z</span>
        </div>
        <div className={`py-0.5 px-1 rounded flex items-center justify-center gap-1 text-[9px] border ${analysis.hasUpper ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-400' : 'bg-[#141414] border-gray-800 text-gray-600'}`}>
          {analysis.hasUpper ? <Check className="w-2.5 h-2.5 stroke-[3]" /> : <X className="w-2.5 h-2.5" />}
          <span>A-Z</span>
        </div>
        <div className={`py-0.5 px-1 rounded flex items-center justify-center gap-1 text-[9px] border ${analysis.hasDigits ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-400' : 'bg-[#141414] border-gray-800 text-gray-600'}`}>
          {analysis.hasDigits ? <Check className="w-2.5 h-2.5 stroke-[3]" /> : <X className="w-2.5 h-2.5" />}
          <span>0-9</span>
        </div>
        <div className={`py-0.5 px-1 rounded flex items-center justify-center gap-1 text-[9px] border ${analysis.hasSymbols ? 'bg-emerald-950/40 border-emerald-500/40 text-emerald-400' : 'bg-[#141414] border-gray-800 text-gray-600'}`}>
          {analysis.hasSymbols ? <Check className="w-2.5 h-2.5 stroke-[3]" /> : <X className="w-2.5 h-2.5" />}
          <span>#$&!</span>
        </div>
      </div>

      {/* Dynamic Feedback Suggestions */}
      {showChecklist && analysis.feedback.length > 0 && analysis.tier !== 'VERY_STRONG' && (
        <div className="pt-1 border-t border-obscura-border/50">
          <p className="text-[9px] text-gray-400 leading-tight">
            💡 {analysis.feedback[0]}
          </p>
        </div>
      )}
    </motion.div>
  );
};
