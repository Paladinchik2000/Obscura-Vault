import React, { useState, useRef } from 'react';
import {
  Upload,
  Key,
  ShieldCheck,
  AlertTriangle,
  FileJson,
  X,
  Eye,
  EyeOff,
  CheckCircle2,
  Lock,
  Layers,
  ArrowRight
} from 'lucide-react';
import { motion } from 'motion/react';
import { VaultItem } from '../types';
import { decryptVaultPayload } from '../utils/backupCrypto';

interface BackupRestoreModalProps {
  isOpen: boolean;
  onClose: () => void;
  onRestoreEntries: (entries: VaultItem[]) => void;
  onLogEvent: (message: string, type: 'info' | 'success' | 'error') => void;
  isDarkMode?: boolean;
}

export const BackupRestoreModal: React.FC<BackupRestoreModalProps> = ({
  isOpen,
  onClose,
  onRestoreEntries,
  onLogEvent,
  isDarkMode = true
}) => {
  const [fileContent, setFileContent] = useState<string | null>(null);
  const [fileName, setFileName] = useState<string>('');
  const [fileSize, setFileSize] = useState<string>('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [decryptState, setDecryptState] = useState<'upload' | 'password' | 'decrypting' | 'preview'>('upload');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [decryptedEntries, setDecryptedEntries] = useState<VaultItem[]>([]);
  const [overwriteConflict, setOverwriteConflict] = useState(true);

  const fileInputRef = useRef<HTMLInputElement>(null);

  if (!isOpen) return null;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setFileName(file.name);
    setFileSize((file.size / 1024).toFixed(1) + ' KB');
    setErrorMsg(null);

    const reader = new FileReader();
    reader.onload = (evt) => {
      const text = evt.target?.result as string;
      setFileContent(text);
      setDecryptState('password');
      onLogEvent(`[BACKUP_LOAD] Loaded backup file "${file.name}" (${file.size} bytes)`, 'info');
    };
    reader.readAsText(file);
  };

  const handleDecrypt = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fileContent || !password) return;

    setErrorMsg(null);
    setDecryptState('decrypting');

    try {
      const { plainText, metadata } = await decryptVaultPayload(fileContent, password);
      const parsed = JSON.parse(plainText);
      const entries: any[] = parsed.entries || [];

      if (!Array.isArray(entries)) {
        throw new Error('Malformed backup JSON: "entries" array not found.');
      }

      // Convert to VaultItem
      const formattedEntries: VaultItem[] = entries.map((item, idx) => ({
        id: item.id || Date.now().toString() + '_' + idx,
        title: item.title || 'Untitled Secret',
        category: item.category || 'LOGIN',
        usernameOrCardholder: item.usernameOrCardholder || '',
        secretValue: item.secretValue || '',
        urlOrCardNumber: item.urlOrCardNumber || '',
        notesOrCvv: item.notesOrCvv || '',
        expiryDate: item.expiryDate || 'Never',
        tags: Array.isArray(item.tags) ? item.tags : (item.tags ? [item.tags] : []),
        isFavorite: Boolean(item.isFavorite),
        createdAt: item.createdAt || new Date().toISOString().substring(0, 10),
        lastModified: item.lastModified || new Date().toISOString().substring(0, 16)
      }));

      setDecryptedEntries(formattedEntries);
      setDecryptState('preview');
      onLogEvent(
        `[DECRYPT_SUCCESS] Verified & decrypted ${formattedEntries.length} entries with AES-256-GCM auth tag`,
        'success'
      );
    } catch (err: any) {
      setDecryptState('password');
      setErrorMsg(err.message || 'Incorrect decryption password or damaged file.');
      onLogEvent(`[DECRYPT_FAILED] ${err.message}`, 'error');
    }
  };

  const handleConfirmRestore = () => {
    onRestoreEntries(decryptedEntries);
    onLogEvent(`[RESTORE_SUCCESS] Restored ${decryptedEntries.length} entries to Room DB SSOT`, 'success');
    handleReset();
  };

  const handleReset = () => {
    setFileContent(null);
    setFileName('');
    setPassword('');
    setDecryptState('upload');
    setErrorMsg(null);
    setDecryptedEntries([]);
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/85 backdrop-blur-md flex items-center justify-center p-4 z-50 animate-fade-in">
      <motion.div
        initial={{ scale: 0.95, opacity: 0, y: 10 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        exit={{ scale: 0.95, opacity: 0, y: 10 }}
        className={`w-full max-w-lg rounded-2xl border shadow-2xl overflow-hidden flex flex-col max-h-[90vh] ${
          isDarkMode ? 'bg-[#121212] border-obscura-border text-white' : 'bg-white border-slate-200 text-slate-900'
        }`}
      >
        {/* Header */}
        <div className={`p-4 border-b flex items-center justify-between ${
          isDarkMode ? 'bg-[#161616] border-obscura-border' : 'bg-slate-50 border-slate-200'
        }`}>
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-cyan-500/15 border border-cyan-500/40 flex items-center justify-center">
              <Upload className="w-4 h-4 text-cyan-400" />
            </div>
            <div>
              <h3 className="font-extrabold text-sm tracking-wide">Restore Encrypted Backup</h3>
              <p className="text-[10px] font-mono text-gray-400">AES-256-GCM Authenticated Decryption</p>
            </div>
          </div>
          <button
            onClick={handleReset}
            className="p-1.5 rounded-lg text-gray-400 hover:text-white hover:bg-white/10"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Modal Content */}
        <div className="p-5 overflow-y-auto space-y-4">
          {/* Step 1: Upload File */}
          {decryptState === 'upload' && (
            <div className="space-y-4">
              <div
                onClick={() => fileInputRef.current?.click()}
                className={`border-2 border-dashed rounded-2xl p-8 text-center cursor-pointer transition-all hover:border-cyan-400/80 ${
                  isDarkMode ? 'border-obscura-border bg-[#161616]' : 'border-slate-300 bg-slate-50'
                }`}
              >
                <input
                  type="file"
                  ref={fileInputRef}
                  onChange={handleFileUpload}
                  accept=".json,.obscurabackup"
                  className="hidden"
                />
                <FileJson className="w-10 h-10 text-cyan-400 mx-auto mb-3" />
                <h4 className="text-sm font-bold text-white mb-1">Select or Drop Encrypted Backup File</h4>
                <p className="text-xs text-gray-400">Supports .json and .obscurabackup files</p>
              </div>

              <div className={`p-3 rounded-xl border flex items-center gap-2 text-[10px] text-gray-400 font-mono ${
                isDarkMode ? 'bg-[#181818] border-obscura-border' : 'bg-slate-50 border-slate-200'
              }`}>
                <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Zero-knowledge client-side decryption using WebCrypto AES-GCM (256-bit).</span>
              </div>
            </div>
          )}

          {/* Step 2: Password Input */}
          {decryptState === 'password' && (
            <form onSubmit={handleDecrypt} className="space-y-4">
              <div className={`p-3 rounded-xl border flex items-center justify-between text-xs font-mono ${
                isDarkMode ? 'bg-[#171717] border-obscura-border' : 'bg-slate-50 border-slate-200'
              }`}>
                <div className="flex items-center gap-2 truncate">
                  <FileJson className="w-4 h-4 text-cyan-400" />
                  <span className="truncate text-white font-bold">{fileName}</span>
                </div>
                <span className="text-gray-400">{fileSize}</span>
              </div>

              <div className="space-y-2">
                <label className="text-xs font-bold text-gray-200 flex items-center gap-1.5 font-mono">
                  <Key className="w-3.5 h-3.5 text-cyan-400" />
                  Enter Decryption Password
                </label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter backup encryption password"
                    className={`w-full p-2.5 pr-10 text-xs rounded-xl border font-mono focus:outline-none ${
                      isDarkMode ? 'bg-[#181818] border-obscura-border text-white focus:border-cyan-400' : 'bg-white border-slate-300'
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {errorMsg && (
                <div className="p-3 rounded-xl bg-red-950/30 border border-red-500/40 text-red-400 text-xs flex items-center gap-2 font-mono">
                  <AlertTriangle className="w-4 h-4 shrink-0" />
                  <span>{errorMsg}</span>
                </div>
              )}

              <div className="flex gap-2.5 pt-2">
                <button
                  type="button"
                  onClick={() => setDecryptState('upload')}
                  className="flex-1 py-2.5 text-xs font-bold rounded-xl border border-obscura-border bg-[#1e1e1e] text-gray-300"
                >
                  Back
                </button>
                <button
                  type="submit"
                  disabled={!password}
                  className="flex-2 py-2.5 text-xs font-extrabold rounded-xl bg-cyan-500 hover:bg-cyan-400 text-black flex items-center justify-center gap-2 cursor-pointer shadow-lg shadow-cyan-500/25"
                >
                  <Lock className="w-4 h-4" />
                  <span>Verify & Decrypt Vault</span>
                </button>
              </div>
            </form>
          )}

          {/* Step 3: Decrypting State */}
          {decryptState === 'decrypting' && (
            <div className="py-12 text-center space-y-3">
              <div className="w-12 h-12 rounded-2xl bg-cyan-500/20 border border-cyan-400 flex items-center justify-center mx-auto animate-spin">
                <Lock className="w-6 h-6 text-cyan-400" />
              </div>
              <h4 className="text-sm font-bold text-white font-mono">Verifying AES-GCM Auth Tag...</h4>
              <p className="text-xs text-gray-400">PBKDF2 key derivation in progress (120,000 iterations)</p>
            </div>
          )}

          {/* Step 4: Preview Decrypted Data & Confirm */}
          {decryptState === 'preview' && (
            <div className="space-y-4">
              <div className="p-3 rounded-xl bg-emerald-950/30 border border-emerald-500/40 text-emerald-300 flex items-center justify-between text-xs font-mono">
                <div className="flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  <span>Authentication Tag Verified</span>
                </div>
                <span className="font-bold">{decryptedEntries.length} ENTRIES FOUND</span>
              </div>

              <div className="max-h-48 overflow-y-auto space-y-1.5 pr-1 font-mono">
                {decryptedEntries.map((item, idx) => (
                  <div
                    key={item.id || idx}
                    className={`p-2.5 rounded-lg border flex items-center justify-between text-xs ${
                      isDarkMode ? 'bg-[#181818] border-obscura-border' : 'bg-slate-50 border-slate-200'
                    }`}
                  >
                    <div>
                      <div className="font-bold text-white">{item.title}</div>
                      <div className="text-[10px] text-gray-400">{item.usernameOrCardholder || 'Secret'}</div>
                    </div>
                    <span className="text-[9px] bg-obscura-crimson/20 text-obscura-crimson px-2 py-0.5 rounded border border-obscura-crimson/30">
                      {item.category}
                    </span>
                  </div>
                ))}
              </div>

              <div className="flex gap-2.5 pt-2">
                <button
                  type="button"
                  onClick={handleReset}
                  className="flex-1 py-2.5 text-xs font-bold rounded-xl border border-obscura-border bg-[#1e1e1e] text-gray-300"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleConfirmRestore}
                  className="flex-2 py-2.5 text-xs font-extrabold rounded-xl bg-emerald-500 hover:bg-emerald-400 text-black flex items-center justify-center gap-2 cursor-pointer shadow-lg shadow-emerald-500/25"
                >
                  <ShieldCheck className="w-4 h-4" />
                  <span>Restore {decryptedEntries.length} Items to Room DB</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  );
};
