import React, { useEffect } from 'react';
import {
  Trash2,
  AlertTriangle,
  X,
  ShieldAlert,
  Database,
  Lock
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { VaultItem } from '../types';

interface DeleteConfirmationModalProps {
  isOpen: boolean;
  item: VaultItem | null;
  onConfirm: () => void;
  onCancel: () => void;
  isDarkMode?: boolean;
}

export const DeleteConfirmationModal: React.FC<DeleteConfirmationModalProps> = ({
  isOpen,
  item,
  onConfirm,
  onCancel,
  isDarkMode = true
}) => {
  // Handle keyboard shortcut (Escape to cancel)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!isOpen) return;
      if (e.key === 'Escape') {
        onCancel();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onCancel]);

  if (!isOpen || !item) return null;

  return (
    <AnimatePresence>
      <div className="fixed inset-0 bg-black/85 backdrop-blur-md flex items-center justify-center p-4 z-50 animate-fade-in">
        <motion.div
          initial={{ scale: 0.92, opacity: 0, y: 15 }}
          animate={{ scale: 1, opacity: 1, y: 0 }}
          exit={{ scale: 0.92, opacity: 0, y: 15 }}
          transition={{ type: 'spring', damping: 25, stiffness: 350 }}
          className={`w-full max-w-md rounded-2xl border shadow-2xl overflow-hidden flex flex-col ${
            isDarkMode
              ? 'bg-[#121212] border-red-900/40 text-white shadow-red-950/20'
              : 'bg-white border-red-200 text-slate-900 shadow-xl'
          }`}
          role="dialog"
          aria-modal="true"
          aria-labelledby="delete-dialog-title"
        >
          {/* Top Warning Accent Bar */}
          <div className="h-1 bg-gradient-to-r from-red-600 via-obscura-crimson to-red-600" />

          {/* Header */}
          <div
            className={`p-4 border-b flex items-center justify-between ${
              isDarkMode ? 'bg-[#161616] border-obscura-border' : 'bg-red-50/50 border-slate-200'
            }`}
          >
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-xl bg-red-500/15 border border-red-500/30 flex items-center justify-center">
                <Trash2 className="w-4 h-4 text-red-400 stroke-[2.2]" />
              </div>
              <div>
                <h3 id="delete-dialog-title" className="font-extrabold text-sm tracking-wide text-red-400">
                  Delete Vault Entry
                </h3>
                <p className="text-[10px] font-mono text-gray-400">Permanent Record Removal</p>
              </div>
            </div>
            <button
              onClick={onCancel}
              className={`p-1.5 rounded-lg text-gray-400 hover:text-white transition-colors ${
                isDarkMode ? 'hover:bg-white/10' : 'hover:bg-slate-200'
              }`}
              aria-label="Close dialog"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Body Content */}
          <div className="p-5 space-y-4">
            {/* Target Entry Preview Box */}
            <div
              className={`p-3.5 rounded-xl border space-y-2 ${
                isDarkMode ? 'bg-[#171717] border-obscura-border' : 'bg-slate-50 border-slate-200'
              }`}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="space-y-0.5 min-w-0">
                  <span className="text-[10px] uppercase font-mono font-bold text-gray-400 tracking-wider block">
                    Target Secret Record:
                  </span>
                  <h4 className="text-sm font-extrabold text-white truncate font-sans">
                    {item.title}
                  </h4>
                  {item.usernameOrCardholder && (
                    <p className="text-xs font-mono text-gray-300 truncate">
                      {item.usernameOrCardholder}
                    </p>
                  )}
                </div>

                <span
                  className={`text-[9px] font-mono font-bold px-2 py-0.5 rounded border shrink-0 ${
                    item.category === 'BANK_CARD'
                      ? 'bg-amber-500/10 text-amber-400 border-amber-500/30'
                      : item.category === 'API_KEY'
                      ? 'bg-purple-500/10 text-purple-400 border-purple-500/30'
                      : item.category === 'SECURE_NOTE'
                      ? 'bg-blue-500/10 text-blue-400 border-blue-500/30'
                      : 'bg-obscura-crimson/10 text-obscura-crimson border-obscura-crimson/30'
                  }`}
                >
                  {item.category}
                </span>
              </div>

              {item.urlOrCardNumber && (
                <div className="text-[10px] font-mono text-gray-400 truncate pt-1 border-t border-white/5">
                  <span className="text-gray-500">Resource: </span>
                  <span className="text-gray-300">{item.urlOrCardNumber}</span>
                </div>
              )}
            </div>

            {/* Permanent Destruction Warning */}
            <div
              className={`p-3.5 rounded-xl border flex items-start gap-3 ${
                isDarkMode
                  ? 'bg-red-950/25 border-red-800/40 text-red-300'
                  : 'bg-red-50 border-red-200 text-red-900'
              }`}
            >
              <AlertTriangle className="w-5 h-5 text-red-400 shrink-0 mt-0.5" />
              <div className="space-y-1 text-xs leading-relaxed">
                <p className="font-extrabold text-red-400">
                  This action is permanent and cannot be undone.
                </p>
                <p className="text-[11px] text-gray-300/90 leading-normal">
                  The AES-256 encrypted cryptographic payload for <strong className="text-white">"{item.title}"</strong> will be irreversibly erased from your local SQLCipher Room database.
                </p>
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex gap-2.5 pt-2">
              <button
                type="button"
                onClick={onCancel}
                className={`flex-1 py-2.5 text-xs font-bold rounded-xl border transition-colors cursor-pointer ${
                  isDarkMode
                    ? 'bg-[#1e1e1e] border-obscura-border text-gray-300 hover:bg-[#252525] hover:text-white'
                    : 'bg-slate-100 border-slate-200 text-slate-700 hover:bg-slate-200'
                }`}
              >
                Keep Entry
              </button>

              <button
                type="button"
                onClick={onConfirm}
                className="flex-1 py-2.5 text-xs font-extrabold rounded-xl bg-red-600 hover:bg-red-500 text-white flex items-center justify-center gap-2 transition-all shadow-lg shadow-red-900/30 cursor-pointer active:scale-98"
              >
                <Trash2 className="w-4 h-4 stroke-[2.5]" />
                <span>Permanently Delete</span>
              </button>
            </div>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  );
};
