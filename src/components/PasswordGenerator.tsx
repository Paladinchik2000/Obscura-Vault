import React, { useState, useEffect } from 'react';
import { RefreshCcw, Check, Sparkles, Copy, Sliders, Zap } from 'lucide-react';

export interface PasswordGeneratorOptions {
  length: number;
  useUpper: boolean;
  useLower: boolean;
  useNumbers: boolean;
  useSymbols: boolean;
}

interface PasswordGeneratorProps {
  onApplyPassword: (password: string) => void;
  className?: string;
  isDarkMode?: boolean;
}

export const generatePasswordWithOptions = (opts: PasswordGeneratorOptions): string => {
  const upperChars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  const lowerChars = 'abcdefghijklmnopqrstuvwxyz';
  const numberChars = '0123456789';
  const symbolChars = '!@#$%^&*()_+-=[]{}|;:,.<>?';

  let charSet = '';
  const guaranteedChars: string[] = [];

  if (opts.useUpper) {
    charSet += upperChars;
    guaranteedChars.push(upperChars[Math.floor(Math.random() * upperChars.length)]);
  }
  if (opts.useLower) {
    charSet += lowerChars;
    guaranteedChars.push(lowerChars[Math.floor(Math.random() * lowerChars.length)]);
  }
  if (opts.useNumbers) {
    charSet += numberChars;
    guaranteedChars.push(numberChars[Math.floor(Math.random() * numberChars.length)]);
  }
  if (opts.useSymbols) {
    charSet += symbolChars;
    guaranteedChars.push(symbolChars[Math.floor(Math.random() * symbolChars.length)]);
  }

  if (!charSet) {
    charSet = lowerChars + numberChars;
    guaranteedChars.push(lowerChars[Math.floor(Math.random() * lowerChars.length)]);
  }

  const remainingLength = Math.max(0, opts.length - guaranteedChars.length);
  const result: string[] = [...guaranteedChars];

  for (let i = 0; i < remainingLength; i++) {
    const randomIndex = Math.floor(Math.random() * charSet.length);
    result.push(charSet[randomIndex]);
  }

  // Fisher-Yates shuffle
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }

  return result.join('');
};

export const PasswordGenerator: React.FC<PasswordGeneratorProps> = ({
  onApplyPassword,
  className = '',
  isDarkMode = true
}) => {
  const [options, setOptions] = useState<PasswordGeneratorOptions>({
    length: 18,
    useUpper: true,
    useLower: true,
    useNumbers: true,
    useSymbols: true
  });

  const [generatedPassword, setGeneratedPassword] = useState<string>('');
  const [applied, setApplied] = useState<boolean>(false);
  const [copied, setCopied] = useState<boolean>(false);

  const handleGenerate = () => {
    const pwd = generatePasswordWithOptions(options);
    setGeneratedPassword(pwd);
    setApplied(false);
  };

  useEffect(() => {
    handleGenerate();
  }, [options.length, options.useUpper, options.useLower, options.useNumbers, options.useSymbols]);

  const handleApply = () => {
    if (generatedPassword) {
      onApplyPassword(generatedPassword);
      setApplied(true);
      setTimeout(() => setApplied(false), 2000);
    }
  };

  const handleCopy = () => {
    if (generatedPassword) {
      navigator.clipboard.writeText(generatedPassword);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    }
  };

  // Calculate entropy
  let poolSize = 0;
  if (options.useLower) poolSize += 26;
  if (options.useUpper) poolSize += 26;
  if (options.useNumbers) poolSize += 10;
  if (options.useSymbols) poolSize += 26;
  if (poolSize === 0) poolSize = 36;

  const entropyBits = Math.round(options.length * (Math.log2(poolSize) || 1));
  
  let strengthLabel = 'Weak';
  let strengthColor = isDarkMode ? 'text-red-400' : 'text-red-600';
  let barColor = 'bg-red-500';
  let strengthPercent = Math.min(100, Math.round((entropyBits / 128) * 100));

  if (entropyBits >= 90) {
    strengthLabel = 'Ultra High-Entropy';
    strengthColor = isDarkMode ? 'text-emerald-400' : 'text-emerald-600';
    barColor = 'bg-emerald-400';
  } else if (entropyBits >= 65) {
    strengthLabel = 'Strong';
    strengthColor = isDarkMode ? 'text-emerald-400' : 'text-emerald-600';
    barColor = 'bg-emerald-500';
  } else if (entropyBits >= 40) {
    strengthLabel = 'Moderate';
    strengthColor = isDarkMode ? 'text-amber-400' : 'text-amber-600';
    barColor = 'bg-amber-400';
  }

  const setPreset = (length: number, upper: boolean, lower: boolean, nums: boolean, syms: boolean) => {
    setOptions({
      length,
      useUpper: upper,
      useLower: lower,
      useNumbers: nums,
      useSymbols: syms
    });
  };

  return (
    <div className={`border rounded-xl p-3 space-y-3 font-sans text-xs transition-colors duration-200 ${
      isDarkMode 
        ? 'bg-[#0c0c0c] border-obscura-crimson/40 text-white' 
        : 'bg-white border-red-200 text-slate-800 shadow-sm'
    } ${className}`}>
      {/* Header Banner */}
      <div className={`flex items-center justify-between border-b pb-2 ${
        isDarkMode ? 'border-obscura-border/60' : 'border-slate-200'
      }`}>
        <div className={`flex items-center gap-1.5 font-bold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
          <Sparkles className="w-3.5 h-3.5 text-obscura-crimson" />
          <span>High-Entropy Password Generator</span>
        </div>
        <span className={`text-[10px] font-mono border px-1.5 py-0.5 rounded ${
          isDarkMode 
            ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30' 
            : 'text-emerald-700 bg-emerald-50 border-emerald-200 font-semibold'
        }`}>
          {entropyBits} BITS ENTROPY
        </span>
      </div>

      {/* Generated Password Preview Box */}
      <div className={`border rounded-lg p-2.5 space-y-1.5 ${
        isDarkMode ? 'bg-[#141414] border-obscura-border' : 'bg-slate-50 border-slate-200'
      }`}>
        <div className={`flex items-center justify-between text-[10px] font-mono ${
          isDarkMode ? 'text-gray-400' : 'text-slate-500'
        }`}>
          <span>GENERATED SECRET</span>
          <span className={`font-bold ${strengthColor}`}>{strengthLabel}</span>
        </div>

        <div className="flex items-center justify-between gap-2">
          <div className={`font-mono text-xs font-extrabold tracking-wider break-all selection:bg-obscura-crimson selection:text-black ${
            isDarkMode ? 'text-white' : 'text-slate-900'
          }`}>
            {generatedPassword}
          </div>
          <div className="flex items-center gap-1 shrink-0">
            <button
              type="button"
              onClick={handleCopy}
              className={`p-1.5 border rounded transition-all ${
                isDarkMode 
                  ? 'bg-[#222] hover:bg-[#333] border-obscura-border text-gray-300' 
                  : 'bg-white hover:bg-slate-100 border-slate-200 text-slate-700'
              }`}
              title="Copy Password"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
            </button>
            <button
              type="button"
              onClick={handleGenerate}
              className={`p-1.5 border rounded transition-all active:rotate-180 duration-200 ${
                isDarkMode 
                  ? 'bg-[#222] hover:bg-obscura-crimson/30 border-obscura-border text-obscura-crimson' 
                  : 'bg-white hover:bg-red-50 border-slate-200 text-red-600'
              }`}
              title="Regenerate Password"
            >
              <RefreshCcw className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* Strength Progress Bar */}
        <div className={`w-full h-1 rounded-full overflow-hidden ${isDarkMode ? 'bg-[#222]' : 'bg-slate-200'}`}>
          <div className={`h-full ${barColor} transition-all duration-300`} style={{ width: `${strengthPercent}%` }} />
        </div>
      </div>

      {/* Length Slider & Direct Input */}
      <div className="space-y-1">
        <div className="flex items-center justify-between text-[11px] font-mono">
          <span className={`flex items-center gap-1 ${isDarkMode ? 'text-gray-300' : 'text-slate-700'}`}>
            <Sliders className="w-3 h-3 text-obscura-crimson" />
            Password Length:
          </span>
          <span className={`font-bold px-2 py-0.5 rounded border ${
            isDarkMode 
              ? 'text-obscura-crimson bg-obscura-crimson/10 border-obscura-crimson/30' 
              : 'text-red-700 bg-red-50 border-red-200'
          }`}>
            {options.length} characters
          </span>
        </div>
        <input
          type="range"
          min={6}
          max={64}
          value={options.length}
          onChange={(e) => setOptions(prev => ({ ...prev, length: parseInt(e.target.value) || 12 }))}
          className={`w-full accent-obscura-crimson h-1.5 rounded-lg cursor-pointer ${
            isDarkMode ? 'bg-[#1e1e1e]' : 'bg-slate-200'
          }`}
        />
      </div>

      {/* Complexity Toggles Grid */}
      <div className="grid grid-cols-2 gap-1.5">
        {[
          { label: 'Uppercase (A-Z)', key: 'useUpper' as const },
          { label: 'Lowercase (a-z)', key: 'useLower' as const },
          { label: 'Numbers (0-9)', key: 'useNumbers' as const },
          { label: 'Symbols (!@#$)', key: 'useSymbols' as const },
        ].map(item => (
          <label 
            key={item.key}
            className={`flex items-center justify-between border p-2 rounded-lg cursor-pointer text-[11px] transition-all ${
              isDarkMode 
                ? 'bg-[#141414] border-obscura-border hover:border-obscura-crimson/50' 
                : 'bg-slate-50 border-slate-200 hover:border-red-300'
            }`}
          >
            <span className={`font-mono ${isDarkMode ? 'text-gray-300' : 'text-slate-700'}`}>{item.label}</span>
            <input
              type="checkbox"
              checked={options[item.key]}
              onChange={(e) => setOptions(prev => ({ ...prev, [item.key]: e.target.checked }))}
              className="accent-obscura-crimson w-3.5 h-3.5 rounded cursor-pointer"
            />
          </label>
        ))}
      </div>

      {/* Preset Chips */}
      <div className="flex items-center gap-1 overflow-x-auto pb-0.5">
        <span className={`text-[10px] font-mono shrink-0 ${isDarkMode ? 'text-gray-500' : 'text-slate-400'}`}>Presets:</span>
        <button
          type="button"
          onClick={() => setPreset(6, false, false, true, false)}
          className={`px-2 py-0.5 border rounded text-[10px] font-mono shrink-0 transition-all ${
            isDarkMode 
              ? 'bg-[#1a1a1a] hover:bg-[#252525] border-obscura-border text-gray-300' 
              : 'bg-slate-100 hover:bg-slate-200 border-slate-200 text-slate-700'
          }`}
        >
          PIN (6)
        </button>
        <button
          type="button"
          onClick={() => setPreset(16, true, true, true, false)}
          className={`px-2 py-0.5 border rounded text-[10px] font-mono shrink-0 transition-all ${
            isDarkMode 
              ? 'bg-[#1a1a1a] hover:bg-[#252525] border-obscura-border text-gray-300' 
              : 'bg-slate-100 hover:bg-slate-200 border-slate-200 text-slate-700'
          }`}
        >
          Standard (16)
        </button>
        <button
          type="button"
          onClick={() => setPreset(24, true, true, true, true)}
          className={`px-2 py-0.5 border rounded text-[10px] font-mono shrink-0 transition-all ${
            isDarkMode 
              ? 'bg-[#1a1a1a] hover:bg-[#252525] border-obscura-border text-gray-300' 
              : 'bg-slate-100 hover:bg-slate-200 border-slate-200 text-slate-700'
          }`}
        >
          Strong (24)
        </button>
        <button
          type="button"
          onClick={() => setPreset(32, true, true, true, true)}
          className={`px-2 py-0.5 border rounded text-[10px] font-mono shrink-0 transition-all ${
            isDarkMode 
              ? 'bg-[#1a1a1a] hover:bg-[#252525] border-obscura-border text-gray-300' 
              : 'bg-slate-100 hover:bg-slate-200 border-slate-200 text-slate-700'
          }`}
        >
          Ultra Token (32)
        </button>
      </div>

      {/* Apply Button */}
      <button
        type="button"
        onClick={handleApply}
        className={`w-full py-2 font-extrabold text-xs rounded-lg flex items-center justify-center gap-1.5 transition-all shadow-md ${
          applied
            ? 'bg-emerald-500 text-black shadow-emerald-500/20'
            : 'bg-obscura-crimson hover:bg-obscura-crimsonHover text-black shadow-obscura-crimson/20'
        }`}
      >
        {applied ? (
          <>
            <Check className="w-4 h-4" />
            <span>POPULATED IN SECRET VALUE!</span>
          </>
        ) : (
          <>
            <Zap className="w-4 h-4 fill-current" />
            <span>USE GENERATED PASSWORD IN FORM</span>
          </>
        )}
      </button>
    </div>
  );
};
