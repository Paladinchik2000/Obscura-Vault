import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { 
  Search, 
  Key, 
  Copy, 
  X, 
  Fingerprint, 
  ScanFace, 
  ShieldCheck, 
  Lock, 
  SlidersHorizontal, 
  Check, 
  Sparkles,
  ExternalLink,
  Shield
} from 'lucide-react';
import { VaultItem } from '../types';
import { HighlightText } from './HighlightText';

interface QuickSearchTileModalProps {
  isOpen: boolean;
  onClose: () => void;
  items: VaultItem[];
  onCopySecret: (text: string, title: string) => void;
  isDarkMode?: boolean;
}

export const QuickSearchTileModal: React.FC<QuickSearchTileModalProps> = ({
  isOpen,
  onClose,
  items,
  onCopySecret,
  isDarkMode = true,
}) => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [authStep, setAuthStep] = useState<'prompt' | 'scanning' | 'success' | 'failed'>('prompt');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Trigger Biometric prompt as soon as the floating activity launches (Requirement #3)
  useEffect(() => {
    if (isOpen) {
      setIsAuthenticated(false);
      setAuthStep('scanning');
      setSearchQuery('');
      setCopiedId(null);

      // Simulate instantaneous BiometricPrompt.authenticate()
      const timer = setTimeout(() => {
        setAuthStep('success');
        const authTimer = setTimeout(() => {
          setIsAuthenticated(true);
        }, 500);
        return () => clearTimeout(authTimer);
      }, 700);

      return () => clearTimeout(timer);
    }
  }, [isOpen]);

  const filteredItems = items.filter((item) => {
    const matchesCategory = selectedCategory === 'ALL' || item.category === selectedCategory;
    const q = searchQuery.toLowerCase().trim();
    if (!q) return matchesCategory;
    const matchesQuery =
      item.title.toLowerCase().includes(q) ||
      item.usernameOrCardholder.toLowerCase().includes(q) ||
      (item.tags && item.tags.some((t) => t.toLowerCase().includes(q)));
    return matchesCategory && matchesQuery;
  });

  const handleSelectAndCopy = (item: VaultItem) => {
    setCopiedId(item.id);
    onCopySecret(item.secretValue, item.title);
    // Instant copy & finish() back to underlying context (Requirement #4)
    setTimeout(() => {
      onClose();
    }, 600);
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="absolute inset-0 z-50 flex items-center justify-center p-4">
          {/* Transparent / Dimmed Floating Overlay (Requirement #2) */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="absolute inset-0 bg-black/75 backdrop-blur-md"
          />

          {/* Floating Dialog Activity Container with FLAG_SECURE visual cue */}
          <motion.div
            initial={{ scale: 0.9, opacity: 0, y: 20 }}
            animate={{ scale: 1, opacity: 1, y: 0 }}
            exit={{ scale: 0.9, opacity: 0, y: 20 }}
            transition={{ type: 'spring', stiffness: 350, damping: 25 }}
            className={`w-full max-w-sm max-h-[85%] rounded-3xl border shadow-2xl overflow-hidden flex flex-col relative z-10 ${
              isDarkMode
                ? 'bg-[#0F0F0F]/95 border-obscura-border text-white'
                : 'bg-white/95 border-slate-200 text-slate-900 shadow-xl'
            }`}
          >
            {/* Window Header with Quick Settings Tile Indicator */}
            <div className="px-4 py-3 border-b border-inherit bg-[#141414]/90 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-xl bg-obscura-crimson flex items-center justify-center shadow-md shadow-obscura-crimson/20">
                  <Key className="w-4 h-4 text-black stroke-[2.5]" />
                </div>
                <div>
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs font-black tracking-wider uppercase font-mono">
                      Quick Search Tile
                    </span>
                    <span className="text-[9px] font-mono bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 px-1.5 py-0.2 rounded font-bold">
                      FLAG_SECURE
                    </span>
                  </div>
                  <p className="text-[10px] text-gray-400">TileService • Floating Dialog Activity</p>
                </div>
              </div>

              <button
                type="button"
                onClick={onClose}
                className="w-7 h-7 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center text-gray-400 hover:text-white transition-all cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Biometric Gate Screen (Before Authentication) */}
            {!isAuthenticated ? (
              <div className="p-8 flex flex-col items-center justify-center text-center space-y-4 my-auto">
                <div className="relative">
                  <motion.div
                    animate={{
                      scale: authStep === 'scanning' ? [1, 1.15, 1] : 1,
                      rotate: authStep === 'scanning' ? [0, 5, -5, 0] : 0,
                    }}
                    transition={{ repeat: Infinity, duration: 1.2 }}
                    className={`w-20 h-20 rounded-2xl flex items-center justify-center border-2 shadow-xl ${
                      authStep === 'success'
                        ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400'
                        : 'bg-obscura-crimson/15 border-obscura-crimson text-obscura-crimson'
                    }`}
                  >
                    {authStep === 'success' ? (
                      <ShieldCheck className="w-10 h-10" />
                    ) : (
                      <Fingerprint className="w-10 h-10 animate-pulse" />
                    )}
                  </motion.div>
                </div>

                <div>
                  <h3 className="text-sm font-bold tracking-wide">
                    {authStep === 'success'
                      ? 'Identity Verified'
                      : 'Biometric Gate Active'}
                  </h3>
                  <p className="text-xs text-gray-400 mt-1 font-mono">
                    {authStep === 'success'
                      ? 'Releasing Room Database SSOT'
                      : 'Verifying Class 3 Strong Biometrics...'}
                  </p>
                </div>

                <div className="text-[10px] font-mono text-gray-500 bg-black/40 px-3 py-1.5 rounded-lg border border-white/5">
                  BiometricPrompt.AuthenticationCallback
                </div>
              </div>
            ) : (
              /* Authenticated Quick Search UI (Requirement #4) */
              <div className="flex-1 flex flex-col overflow-hidden p-3 space-y-2.5">
                {/* Search Input */}
                <div className="relative">
                  <Search className="w-4 h-4 text-obscura-crimson absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    autoFocus
                    placeholder="Quick search passwords..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="w-full pl-9 pr-8 py-2 bg-[#181818] border border-obscura-border rounded-xl text-xs text-white placeholder:text-gray-500 focus:outline-none focus:border-obscura-crimson font-mono transition-all"
                  />
                  {searchQuery && (
                    <button
                      type="button"
                      onClick={() => setSearchQuery('')}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>

                {/* Quick Category Filter Pills */}
                <div className="flex gap-1 overflow-x-auto pb-1 no-scrollbar">
                  {['ALL', 'LOGIN', 'BANK_CARD', 'API_KEY'].map((cat) => (
                    <button
                      key={cat}
                      type="button"
                      onClick={() => setSelectedCategory(cat)}
                      className={`px-2 py-1 rounded-lg text-[9px] font-mono font-bold shrink-0 transition-all cursor-pointer ${
                        selectedCategory === cat
                          ? 'bg-obscura-crimson text-black font-extrabold shadow-sm'
                          : 'bg-[#181818] text-gray-400 hover:text-white border border-white/5'
                      }`}
                    >
                      {cat.replace('_', ' ')}
                    </button>
                  ))}
                </div>

                {/* Results List */}
                <div className="flex-1 overflow-y-auto space-y-1.5 pr-0.5 max-h-[300px]">
                  {filteredItems.length === 0 ? (
                    <div className="py-8 text-center text-gray-500 text-xs font-mono">
                      No matching credentials found in Room DB
                    </div>
                  ) : (
                    filteredItems.map((item) => {
                      const isCopied = copiedId === item.id;
                      return (
                        <motion.div
                          key={item.id}
                          layout
                          initial={{ opacity: 0, y: 6 }}
                          animate={{ opacity: 1, y: 0 }}
                          onClick={() => handleSelectAndCopy(item)}
                          className={`p-2.5 rounded-xl border flex items-center justify-between gap-2 transition-all cursor-pointer group ${
                            isCopied
                              ? 'bg-emerald-950/60 border-emerald-500 text-emerald-300'
                              : 'bg-[#141414] hover:bg-[#1a1a1a] border-obscura-border hover:border-obscura-crimson/50 text-white'
                          }`}
                        >
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-1.5">
                              <span className="font-bold text-xs truncate group-hover:text-obscura-crimson transition-colors">
                                <HighlightText text={item.title} query={searchQuery} isDarkMode={isDarkMode} accentVariant="crimson" />
                              </span>
                              <span className="text-[8px] font-mono px-1.5 py-0.2 bg-white/5 rounded text-gray-400">
                                {item.category}
                              </span>
                            </div>
                            <div className="text-[10px] font-mono text-gray-400 truncate mt-0.5">
                              <HighlightText text={item.usernameOrCardholder} query={searchQuery} isDarkMode={isDarkMode} accentVariant="crimson" />
                            </div>
                            {item.tags && item.tags.length > 0 && searchQuery.trim() && item.tags.some(t => t.toLowerCase().includes(searchQuery.trim().toLowerCase())) && (
                              <div className="flex flex-wrap gap-1 mt-1">
                                {item.tags.filter(t => t.toLowerCase().includes(searchQuery.trim().toLowerCase())).map(tag => (
                                  <span key={tag} className="text-[8px] px-1.5 py-0.2 bg-amber-400/20 text-amber-300 border border-amber-400/50 rounded font-mono font-bold">
                                    #<HighlightText text={tag} query={searchQuery} isDarkMode={isDarkMode} accentVariant="amber" />
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>

                          <button
                            type="button"
                            className={`px-2.5 py-1.5 rounded-lg text-[10px] font-mono font-bold flex items-center gap-1 shrink-0 transition-all ${
                              isCopied
                                ? 'bg-emerald-500 text-black font-extrabold'
                                : 'bg-obscura-crimson/20 text-obscura-crimson group-hover:bg-obscura-crimson group-hover:text-black'
                            }`}
                          >
                            {isCopied ? (
                              <>
                                <Check className="w-3 h-3 stroke-[3]" />
                                <span>Copied!</span>
                              </>
                            ) : (
                              <>
                                <Copy className="w-3 h-3" />
                                <span>Copy</span>
                              </>
                            )}
                          </button>
                        </motion.div>
                      );
                    })
                  )}
                </div>

                {/* Footer Hint */}
                <div className="pt-1 border-t border-inherit flex items-center justify-between text-[9px] font-mono text-gray-500">
                  <span>Auto-clears clipboard in 45s</span>
                  <span>Tap entry to copy & dismiss</span>
                </div>
              </div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};
