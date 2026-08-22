import React, { useState } from 'react';
import {
  Shield,
  Lock,
  Download,
  CheckCircle2,
  AlertTriangle,
  Eye,
  EyeOff,
  Key,
  X,
  FileJson,
  Layers,
  Sparkles,
  Copy,
  FileCheck
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { VaultItem } from '../types';
import { encryptVaultPayload, EncryptedBackupContainer } from '../utils/backupCrypto';

interface BackupExportModalProps {
  isOpen: boolean;
  onClose: () => void;
  items: VaultItem[];
  onLogEvent: (message: string, type: 'info' | 'success' | 'error') => void;
  isDarkMode?: boolean;
}

export const BackupExportModal: React.FC<BackupExportModalProps> = ({
  isOpen,
  onClose,
  items,
  onLogEvent,
  isDarkMode = true
}) => {
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [formatType, setFormatType] = useState<'json' | 'binary'>('json');
  const [exportState, setExportState] = useState<'form' | 'encrypting' | 'success'>('form');
  const [exportedResult, setExportedResult] = useState<EncryptedBackupContainer | null>(null);
  const [copiedHash, setCopiedHash] = useState(false);
  const [includeMetadata, setIncludeMetadata] = useState(true);

  if (!isOpen) return null;

  const isPasswordMatch = password.length > 0 && password === confirmPassword;
  const isPasswordStrongEnough = password.length >= 8;
  const canExport = isPasswordMatch && isPasswordStrongEnough && exportState === 'form';

  // Compute password entropy score
  const calculateStrength = (pwd: string) => {
    if (!pwd) return { score: 0, label: 'Empty', color: 'text-gray-500', barBg: 'bg-gray-700' };
    let score = 0;
    if (pwd.length >= 8) score += 25;
    if (pwd.length >= 14) score += 25;
    if (/[A-Z]/.test(pwd) && /[a-z]/.test(pwd)) score += 20;
    if (/[0-9]/.test(pwd)) score += 15;
    if (/[^A-Za-z0-9]/.test(pwd)) score += 15;

    if (score < 40) return { score, label: 'Weak', color: 'text-red-400', barBg: 'bg-red-500' };
    if (score < 75) return { score, label: 'Good', color: 'text-amber-400', barBg: 'bg-amber-500' };
    return { score, label: 'Strong (Recommended)', color: 'text-emerald-400', barBg: 'bg-emerald-500' };
  };

  const strength = calculateStrength(password);

  const handleExport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canExport) return;

    setExportState('encrypting');
    onLogEvent(`[BACKUP_EXPORT] Initiating PBKDF2 (120,000 iter) + AES-256-GCM vault encryption`, 'info');

    try {
      // Prepare payload JSON
      const payloadData = {
        version: 2,
        app: 'Obscura Vault',
        timestamp: Date.now(),
        exportedAt: new Date().toISOString(),
        entriesCount: items.length,
        entries: items.map(item => ({
          id: item.id,
          title: item.title,
          category: item.category,
          usernameOrCardholder: item.usernameOrCardholder,
          secretValue: item.secretValue,
          urlOrCardNumber: item.urlOrCardNumber,
          notesOrCvv: item.notesOrCvv,
          expiryDate: item.expiryDate,
          tags: item.tags,
          isFavorite: item.isFavorite,
          createdAt: item.createdAt,
          lastModified: item.lastModified,
          ...(includeMetadata ? { exportedMetadata: { version: '2.0.0', platform: 'Android / Room DB' } } : {})
        }))
      };

      const plainText = JSON.stringify(payloadData, null, 2);

      // Perform real WebCrypto AES-256-GCM encryption
      const encryptedContainer = await encryptVaultPayload(plainText, password, items.length);

      // Prepare file blob for download
      let blob: Blob;
      let filename: string;
      const timestampStr = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);

      if (formatType === 'json') {
        const jsonContent = JSON.stringify(encryptedContainer, null, 2);
        blob = new Blob([jsonContent], { type: 'application/json' });
        filename = `obscura_vault_backup_${timestampStr}.json`;
      } else {
        // Raw binary payload format [Salt 16B] + [IV 12B] + [Ciphertext]
        const binaryStr = atob(encryptedContainer.combinedPayloadBase64);
        const bytes = new Uint8Array(binaryStr.length);
        for (let i = 0; i < binaryStr.length; i++) {
          bytes[i] = binaryStr.charCodeAt(i);
        }
        blob = new Blob([bytes], { type: 'application/octet-stream' });
        filename = `obscura_vault_backup_${timestampStr}.obscurabackup`;
      }

      // Trigger browser download
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);

      setExportedResult(encryptedContainer);
      setExportState('success');

      onLogEvent(
        `[BACKUP_SUCCESS] Exported ${items.length} records into ${filename} (SHA-256: ${encryptedContainer.checksumSha256.slice(0, 12)}...)`,
        'success'
      );
    } catch (err: any) {
      setExportState('form');
      onLogEvent(`[BACKUP_ERROR] Encryption failed: ${err?.message || 'Unknown error'}`, 'error');
    }
  };

  const handleCopyChecksum = () => {
    if (exportedResult?.checksumSha256) {
      navigator.clipboard.writeText(exportedResult.checksumSha256);
      setCopiedHash(true);
      setTimeout(() => setCopiedHash(false), 2000);
    }
  };

  const handleReset = () => {
    setPassword('');
    setConfirmPassword('');
    setExportState('form');
    setExportedResult(null);
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/85 backdrop-blur-md flex items-center justify-center p-4 z-50 animate-fade-in">
      <motion.div
        initial={{ scale: 0.95, opacity: 0, y: 10 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        exit={{ scale: 0.95, opacity: 0, y: 10 }}
        transition={{ duration: 0.2 }}
        className={`w-full max-w-lg rounded-2xl border shadow-2xl overflow-hidden flex flex-col max-h-[90vh] ${
          isDarkMode ? 'bg-[#121212] border-obscura-border text-white' : 'bg-white border-slate-200 text-slate-900'
        }`}
      >
        {/* Header */}
        <div className={`p-4 border-b flex items-center justify-between ${
          isDarkMode ? 'bg-[#161616] border-obscura-border' : 'bg-slate-50 border-slate-200'
        }`}>
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-obscura-crimson/15 border border-obscura-crimson/40 flex items-center justify-center">
              <Lock className="w-4 h-4 text-obscura-crimson" />
            </div>
            <div>
              <h3 className="font-extrabold text-sm tracking-wide">Backup & Export Vault</h3>
              <p className="text-[10px] font-mono text-gray-400">AES-256-GCM • PBKDF2 Password Confirmation</p>
            </div>
          </div>
          <button
            onClick={handleReset}
            className="p-1.5 rounded-lg text-gray-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-5 overflow-y-auto space-y-4">
          {exportState === 'form' && (
            <form onSubmit={handleExport} className="space-y-4">
              {/* Vault Overview Card */}
              <div className={`p-3.5 rounded-xl border space-y-2 ${
                isDarkMode ? 'bg-[#171717] border-obscura-border' : 'bg-slate-50 border-slate-200'
              }`}>
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-gray-300 flex items-center gap-1.5">
                    <Layers className="w-3.5 h-3.5 text-obscura-crimson" />
                    Vault Contents to Export
                  </span>
                  <span className="font-mono font-bold text-obscura-crimson bg-obscura-crimson/10 px-2 py-0.5 rounded border border-obscura-crimson/30">
                    {items.length} ENTRIES
                  </span>
                </div>

                <div className="grid grid-cols-4 gap-2 text-[10px] font-mono text-gray-400 pt-1">
                  <div className="p-1.5 bg-black/40 rounded border border-white/5 text-center">
                    <span className="block text-white font-bold">{items.filter(i => i.category === 'LOGIN').length}</span>
                    <span>Logins</span>
                  </div>
                  <div className="p-1.5 bg-black/40 rounded border border-white/5 text-center">
                    <span className="block text-white font-bold">{items.filter(i => i.category === 'BANK_CARD').length}</span>
                    <span>Cards</span>
                  </div>
                  <div className="p-1.5 bg-black/40 rounded border border-white/5 text-center">
                    <span className="block text-white font-bold">{items.filter(i => i.category === 'API_KEY').length}</span>
                    <span>API Keys</span>
                  </div>
                  <div className="p-1.5 bg-black/40 rounded border border-white/5 text-center">
                    <span className="block text-white font-bold">{items.filter(i => i.category === 'SECURE_NOTE').length}</span>
                    <span>Notes</span>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-1 text-[10px] font-mono text-gray-400 border-t border-white/5">
                  <span>Crypto Algorithm:</span>
                  <span className="text-emerald-400 font-bold">AES-256-GCM / PBKDF2 (120k)</span>
                </div>
              </div>

              {/* Password Protection Step */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-bold text-gray-200 flex items-center gap-1.5 font-mono">
                    <Key className="w-3.5 h-3.5 text-obscura-crimson" />
                    Set Backup Decryption Password
                  </label>
                  <span className={`text-[10px] font-mono font-bold ${strength.color}`}>
                    {strength.label}
                  </span>
                </div>

                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter strong backup password (min 8 chars)"
                    className={`w-full p-2.5 pr-10 text-xs rounded-xl border font-mono focus:outline-none transition-colors ${
                      isDarkMode
                        ? 'bg-[#181818] border-obscura-border text-white focus:border-obscura-crimson'
                        : 'bg-white border-slate-300 text-slate-900 focus:border-red-600'
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white p-0.5"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>

                {/* Strength Meter Bar */}
                {password.length > 0 && (
                  <div className="w-full bg-gray-800 h-1.5 rounded-full overflow-hidden">
                    <div
                      className={`h-full transition-all duration-300 ${strength.barBg}`}
                      style={{ width: `${Math.max(10, strength.score)}%` }}
                    />
                  </div>
                )}

                {/* Confirm Password Field */}
                <div>
                  <label className="text-xs font-bold text-gray-200 flex items-center gap-1.5 font-mono mb-1">
                    Confirm Backup Password
                  </label>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    required
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Re-type backup password to confirm"
                    className={`w-full p-2.5 text-xs rounded-xl border font-mono focus:outline-none transition-colors ${
                      isDarkMode
                        ? 'bg-[#181818] border-obscura-border text-white focus:border-obscura-crimson'
                        : 'bg-white border-slate-300 text-slate-900 focus:border-red-600'
                    } ${confirmPassword && !isPasswordMatch ? 'border-red-500' : ''}`}
                  />
                </div>

                {/* Password Match Status */}
                {confirmPassword.length > 0 && (
                  <div className="flex items-center gap-1.5 text-[11px] font-mono">
                    {isPasswordMatch ? (
                      <span className="text-emerald-400 flex items-center gap-1">
                        <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                        Passwords match perfectly
                      </span>
                    ) : (
                      <span className="text-red-400 flex items-center gap-1">
                        <AlertTriangle className="w-3.5 h-3.5 text-red-400" />
                        Passwords do not match
                      </span>
                    )}
                  </div>
                )}
              </div>

              {/* Format & Options Selection */}
              <div className="space-y-2">
                <label className="text-xs font-bold text-gray-300 font-mono block">
                  Export File Format:
                </label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setFormatType('json')}
                    className={`p-2.5 rounded-xl border text-left flex items-start gap-2.5 transition-all ${
                      formatType === 'json'
                        ? 'bg-obscura-crimson/15 border-obscura-crimson text-white ring-1 ring-obscura-crimson'
                        : isDarkMode
                        ? 'bg-[#161616] border-obscura-border text-gray-400 hover:text-white'
                        : 'bg-slate-50 border-slate-200 text-slate-600'
                    }`}
                  >
                    <FileJson className="w-4 h-4 text-obscura-crimson shrink-0 mt-0.5" />
                    <div>
                      <div className="text-xs font-bold font-mono">Encrypted JSON</div>
                      <div className="text-[9px] text-gray-400 leading-tight">Portable JSON envelope (.json)</div>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setFormatType('binary')}
                    className={`p-2.5 rounded-xl border text-left flex items-start gap-2.5 transition-all ${
                      formatType === 'binary'
                        ? 'bg-obscura-crimson/15 border-obscura-crimson text-white ring-1 ring-obscura-crimson'
                        : isDarkMode
                        ? 'bg-[#161616] border-obscura-border text-gray-400 hover:text-white'
                        : 'bg-slate-50 border-slate-200 text-slate-600'
                    }`}
                  >
                    <Shield className="w-4 h-4 text-cyan-400 shrink-0 mt-0.5" />
                    <div>
                      <div className="text-xs font-bold font-mono">Raw Binary</div>
                      <div className="text-[9px] text-gray-400 leading-tight">Android native (.obscurabackup)</div>
                    </div>
                  </button>
                </div>
              </div>

              {/* Zero-Knowledge Security Notice */}
              <div className={`p-3 rounded-xl border flex items-start gap-2.5 ${
                isDarkMode ? 'bg-amber-950/20 border-amber-800/40 text-amber-300' : 'bg-amber-50 border-amber-200 text-amber-900'
              }`}>
                <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                <p className="text-[10px] leading-relaxed">
                  <strong>Zero-Knowledge Advisory:</strong> Obscura does not store this password. If you lose or forget this password, this backup cannot be restored.
                </p>
              </div>

              {/* Submit Buttons */}
              <div className="flex gap-2.5 pt-2">
                <button
                  type="button"
                  onClick={handleReset}
                  className={`flex-1 py-2.5 text-xs font-bold rounded-xl border transition-colors ${
                    isDarkMode ? 'bg-[#1e1e1e] border-obscura-border text-gray-300 hover:bg-[#252525]' : 'bg-slate-100 border-slate-200 text-slate-700'
                  }`}
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  disabled={!canExport}
                  className={`flex-2 py-2.5 text-xs font-extrabold rounded-xl flex items-center justify-center gap-2 transition-all shadow-lg ${
                    canExport
                      ? 'bg-obscura-crimson hover:bg-obscura-crimsonHover text-black shadow-obscura-crimson/25 cursor-pointer active:scale-98'
                      : 'bg-gray-800 text-gray-500 cursor-not-allowed opacity-60'
                  }`}
                >
                  <Download className="w-4 h-4 stroke-[2.5]" />
                  <span>Confirm & Export Encrypted File</span>
                </button>
              </div>
            </form>
          )}

          {exportState === 'encrypting' && (
            <div className="py-12 flex flex-col items-center justify-center space-y-4 text-center">
              <div className="w-12 h-12 rounded-2xl bg-obscura-crimson/20 border border-obscura-crimson flex items-center justify-center animate-spin">
                <Lock className="w-6 h-6 text-obscura-crimson" />
              </div>
              <div>
                <h4 className="text-sm font-extrabold text-white font-mono">Encrypting Vault Payload...</h4>
                <p className="text-xs text-gray-400 font-mono mt-1">
                  Deriving PBKDF2 key (120,000 iterations) & computing AES-256-GCM cipher...
                </p>
              </div>
            </div>
          )}

          {exportState === 'success' && exportedResult && (
            <div className="space-y-4 animate-fade-in">
              <div className="p-4 rounded-xl bg-emerald-950/30 border border-emerald-500/40 text-center space-y-2">
                <CheckCircle2 className="w-8 h-8 text-emerald-400 mx-auto" />
                <h4 className="text-sm font-extrabold text-white">Vault Successfully Encrypted & Exported!</h4>
                <p className="text-xs text-emerald-300">
                  Your encrypted backup file has been saved to your downloads.
                </p>
              </div>

              {/* Cryptographic Proof Details */}
              <div className={`p-3.5 rounded-xl border font-mono text-[11px] space-y-2 ${
                isDarkMode ? 'bg-[#161616] border-obscura-border' : 'bg-slate-50 border-slate-200'
              }`}>
                <div className="text-gray-400 text-[10px] uppercase tracking-wider font-bold">Cryptographic Summary</div>
                
                <div className="flex justify-between border-b border-white/5 pb-1">
                  <span className="text-gray-400">Total Entries:</span>
                  <span className="text-white font-bold">{exportedResult.itemCount} Vault Entities</span>
                </div>

                <div className="flex justify-between border-b border-white/5 pb-1">
                  <span className="text-gray-400">Cipher / KDF:</span>
                  <span className="text-emerald-400 font-bold">AES-256-GCM / PBKDF2 (120k)</span>
                </div>

                <div className="flex justify-between border-b border-white/5 pb-1">
                  <span className="text-gray-400">Export Timestamp:</span>
                  <span className="text-gray-300">{exportedResult.dateIso}</span>
                </div>

                <div className="space-y-1 pt-1">
                  <div className="flex items-center justify-between text-gray-400">
                    <span>SHA-256 Integrity Checksum:</span>
                    <button
                      onClick={handleCopyChecksum}
                      className="text-obscura-crimson hover:underline text-[10px] flex items-center gap-1"
                    >
                      <Copy className="w-3 h-3" />
                      <span>{copiedHash ? 'Copied!' : 'Copy Hash'}</span>
                    </button>
                  </div>
                  <div className="p-2 bg-black/60 rounded border border-white/5 text-[10px] text-gray-300 break-all select-all">
                    {exportedResult.checksumSha256}
                  </div>
                </div>
              </div>

              <button
                onClick={handleReset}
                className="w-full py-2.5 bg-obscura-crimson hover:bg-obscura-crimsonHover text-black font-extrabold text-xs rounded-xl transition-all shadow-lg shadow-obscura-crimson/25 cursor-pointer"
              >
                Done
              </button>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  );
};
