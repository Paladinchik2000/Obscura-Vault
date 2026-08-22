import React, { useState, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import {
  FileSpreadsheet,
  Upload,
  CheckCircle2,
  AlertTriangle,
  FileCheck,
  ArrowRight,
  Shield,
  Database,
  Layers,
  Sparkles,
  HelpCircle,
  Copy,
  X,
  FileText,
  Lock,
  RefreshCcw,
  Check,
  ChevronRight,
  Eye,
  EyeOff,
  ArrowRightLeft,
  CopyPlus,
  RefreshCw,
  Ban,
  Search,
  Filter,
  CheckCheck,
  Info
} from 'lucide-react';

export type CsvSchemaFormat = 'CHROME' | 'BITWARDEN' | 'KEEPASS' | 'GENERIC';
export type DuplicateConflictPolicy = 'SKIP' | 'OVERWRITE' | 'KEEP_BOTH';

export interface ExistingVaultSummary {
  id: string;
  title: string;
  usernameOrCardholder: string;
  secretValue?: string;
  urlOrCardNumber?: string;
  notesOrCvv?: string;
  category?: 'LOGIN' | 'BANK_CARD' | 'SECURE_NOTE' | 'API_KEY';
  tags?: string[];
  lastModified?: string;
  createdAt?: string;
}

export interface ParsedCsvRecord {
  id: string;
  title: string;
  category: 'LOGIN' | 'BANK_CARD' | 'SECURE_NOTE' | 'API_KEY';
  usernameOrCardholder: string;
  secretValue: string;
  urlOrCardNumber: string;
  notesOrCvv: string;
  tags: string[];
  isDuplicate?: boolean;
  matchedExistingItem?: ExistingVaultSummary;
}

export interface CsvParseSummary {
  format: CsvSchemaFormat;
  formatName: string;
  totalRows: number;
  validRecords: ParsedCsvRecord[];
  skippedMalformedRows: number;
  detectedHeaders: string[];
  rawFileSizeFormatted: string;
  fileName: string;
}

export interface CsvImportWizardModalProps {
  isOpen: boolean;
  onClose: () => void;
  existingItems: ExistingVaultSummary[];
  onExecuteImport: (
    records: ParsedCsvRecord[],
    conflictPolicy: DuplicateConflictPolicy,
    onProgress: (percent: number, current: number, total: number) => void,
    customResolutions?: Record<string, DuplicateConflictPolicy>
  ) => Promise<{ inserted: number; overwritten: number; skipped: number }>;
  isDarkMode?: boolean;
}

// Sample Templates for User Testing & Demonstrations (with realistic duplicates of sample vault items)
export const SAMPLE_BITWARDEN_CSV = `folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
Personal,1,login,Netflix HD Streaming,Updated to 8K Family Tier via Bitwarden,,0,https://netflix.com,cinephile@obscura.app,K9#mX$8pL!zQ2wE_Updated2026,JBSWY3DPEHPK3PXP
Finance,1,card,Primary Visa Black Card,New CVV and limits updated,,0,4532 8921 7731 9012,ALEXANDER VAULT,994,
Cloud,0,login,AWS Console Root,Production IAM Admin,,0,https://aws.amazon.com,admin@obscura.app,AwsSuperK3y#991,
Streaming,0,login,Spotify Family,Monthly recurring billing,,0,https://spotify.com,music.lover@obscura.app,Sp0tify!Rock2026,`;

export const SAMPLE_CHROME_CSV = `name,url,username,password
Netflix HD Streaming,https://netflix.com,cinephile@obscura.app,NewChromePass!2026
Google Account,https://accounts.google.com,alexander@gmail.com,G00gle!Secur3P@ss
Twitter / X,https://x.com,cinephile_obscura,X_Tvv33t!Mast3r
Discord Developer,https://discord.com,discord_mod@obscura.app,D1sc0rd#Token99
Steam Vault,https://store.steampowered.com,gaben_fan26,SteamP@ssword!2026`;

export const SAMPLE_KEEPASS_CSV = `"Account","Login Name","Password","Web Site","Comments"
"Obscura TMDB Production API","System Admin","obs_live_99f381a0_KeePassNewKey","https://api.themoviedb.org/3","Updated production API token"
"ProtonMail Encrypted","proton_user@pm.me","Pr0t0n_S3cur3!99","https://mail.proton.me","PGP Key backup stored in safe"
"Docker Hub Production","dockermaster","D0ck3r_Hub_P@ss!","https://hub.docker.com","Automated CI/CD build deployment"
"Reddit Moderator","obscura_dev","R3dd1t_Sup3r!26","https://reddit.com","Developer community hub"`;

/**
 * Universal CSV Parser Engine
 * Handles RFC-4180 streaming parse, quoted multi-line fields, escaped quotes, and schema auto-detection.
 */
export function parseCsvContent(
  csvText: string,
  fileName: string = 'export.csv',
  fileSizeBytes: number = 0
): CsvParseSummary {
  const rows: string[][] = [];
  let currentField = '';
  let currentRow: string[] = [];
  let inQuotes = false;

  for (let i = 0; i < csvText.length; i++) {
    const char = csvText[i];
    const nextChar = csvText[i + 1];

    if (char === '"') {
      if (inQuotes && nextChar === '"') {
        currentField += '"';
        i++; // Skip escaped double quote
      } else {
        inQuotes = !inQuotes;
      }
    } else if (char === ',' && !inQuotes) {
      currentRow.push(currentField.trim());
      currentField = '';
    } else if ((char === '\r' || char === '\n') && !inQuotes) {
      if (char === '\r' && nextChar === '\n') {
        i++; // Skip CRLF
      }
      currentRow.push(currentField.trim());
      currentField = '';

      if (currentRow.some(field => field.length > 0)) {
        rows.push(currentRow);
      }
      currentRow = [];
    } else {
      currentField += char;
    }
  }

  // Push last remaining field
  if (currentField || currentRow.length > 0) {
    currentRow.push(currentField.trim());
    if (currentRow.some(field => field.length > 0)) {
      rows.push(currentRow);
    }
  }

  if (rows.length === 0) {
    return {
      format: 'GENERIC',
      formatName: 'Generic / Custom CSV',
      totalRows: 0,
      validRecords: [],
      skippedMalformedRows: 0,
      detectedHeaders: [],
      rawFileSizeFormatted: '0 KB',
      fileName
    };
  }

  const rawHeaders = rows[0].map(h => h.toLowerCase().trim());
  const headerSet = new Set(rawHeaders);
  const dataRows = rows.slice(1);

  // Auto-detect format schema
  let format: CsvSchemaFormat = 'GENERIC';
  let formatName = 'Generic / Custom CSV';

  if (
    headerSet.has('login_password') ||
    (headerSet.has('login_uri') && headerSet.has('login_username')) ||
    headerSet.has('reprompt') ||
    headerSet.has('login_totp')
  ) {
    format = 'BITWARDEN';
    formatName = 'Bitwarden Vault Export';
  } else if (
    headerSet.has('name') &&
    headerSet.has('url') &&
    headerSet.has('username') &&
    headerSet.has('password')
  ) {
    format = 'CHROME';
    formatName = 'Google Chrome Password Export';
  } else if (
    (headerSet.has('account') && headerSet.has('password')) ||
    (headerSet.has('login name') && headerSet.has('web site')) ||
    (headerSet.has('title') && headerSet.has('user name'))
  ) {
    format = 'KEEPASS';
    formatName = 'KeePass CSV Database';
  }

  const getCol = (row: string[], colName: string): string => {
    const idx = rawHeaders.indexOf(colName.toLowerCase().trim());
    return idx !== -1 && idx < row.length ? row[idx] : '';
  };

  const validRecords: ParsedCsvRecord[] = [];
  let skippedMalformedRows = 0;

  dataRows.forEach((row, index) => {
    let title = '';
    let username = '';
    let secret = '';
    let url = '';
    let notes = '';
    let category: ParsedCsvRecord['category'] = 'LOGIN';
    const tags: string[] = ['Imported', formatName.split(' ')[0]];

    if (format === 'BITWARDEN') {
      title = getCol(row, 'name') || getCol(row, 'login_uri') || `Bitwarden Item #${index + 1}`;
      username = getCol(row, 'login_username') || getCol(row, 'username');
      secret = getCol(row, 'login_password') || getCol(row, 'password');
      url = getCol(row, 'login_uri') || getCol(row, 'url');
      notes = getCol(row, 'notes');
      const totp = getCol(row, 'login_totp');
      if (totp) notes = notes ? `${notes}\nTOTP Secret: ${totp}` : `TOTP Secret: ${totp}`;
      const folder = getCol(row, 'folder');
      if (folder) tags.push(folder);

      const typeCol = getCol(row, 'type').toLowerCase();
      if (typeCol === 'card' || typeCol === '2') {
        category = 'BANK_CARD';
      } else if (typeCol === 'note' || typeCol === '3') {
        category = 'SECURE_NOTE';
      }
    } else if (format === 'CHROME') {
      title = getCol(row, 'name') || getCol(row, 'url') || `Chrome Login #${index + 1}`;
      url = getCol(row, 'url');
      username = getCol(row, 'username');
      secret = getCol(row, 'password');
      category = 'LOGIN';
    } else if (format === 'KEEPASS') {
      title = getCol(row, 'account') || getCol(row, 'title') || `KeePass Item #${index + 1}`;
      username = getCol(row, 'login name') || getCol(row, 'user name') || getCol(row, 'username');
      secret = getCol(row, 'password');
      url = getCol(row, 'web site') || getCol(row, 'url');
      notes = getCol(row, 'comments') || getCol(row, 'notes');
      category = 'LOGIN';
    } else {
      // Generic / Heuristic Fallback
      const titleIdx = rawHeaders.findIndex(h => h.includes('title') || h.includes('name') || h.includes('account') || h.includes('service'));
      const userIdx = rawHeaders.findIndex(h => h.includes('user') || h.includes('email') || h.includes('login'));
      const passIdx = rawHeaders.findIndex(h => h.includes('pass') || h.includes('secret') || h.includes('key'));
      const urlIdx = rawHeaders.findIndex(h => h.includes('url') || h.includes('link') || h.includes('site') || h.includes('host'));
      const notesIdx = rawHeaders.findIndex(h => h.includes('note') || h.includes('comment') || h.includes('desc'));

      title = titleIdx !== -1 && row[titleIdx] ? row[titleIdx] : `Imported Secret #${index + 1}`;
      username = userIdx !== -1 && row[userIdx] ? row[userIdx] : '';
      secret = passIdx !== -1 && row[passIdx] ? row[passIdx] : '';
      url = urlIdx !== -1 && row[urlIdx] ? row[urlIdx] : '';
      notes = notesIdx !== -1 && row[notesIdx] ? row[notesIdx] : '';
      category = 'LOGIN';
    }

    // Clean up URLs
    if (url.startsWith('http://') || url.startsWith('https://')) {
      // good
    } else if (url.includes('.') && !url.includes(' ')) {
      url = `https://${url}`;
    }

    if (secret && secret.trim().length > 0) {
      validRecords.push({
        id: `csv_import_${Date.now()}_${index}`,
        title: title.trim(),
        category,
        usernameOrCardholder: username.trim(),
        secretValue: secret.trim(),
        urlOrCardNumber: url.trim(),
        notesOrCvv: notes.trim(),
        tags: Array.from(new Set(tags))
      });
    } else {
      skippedMalformedRows++;
    }
  });

  const sizeKb = fileSizeBytes > 0 ? (fileSizeBytes / 1024).toFixed(1) : (new Blob([csvText]).size / 1024).toFixed(1);

  return {
    format,
    formatName,
    totalRows: dataRows.length,
    validRecords,
    skippedMalformedRows,
    detectedHeaders: rows[0] || [],
    rawFileSizeFormatted: `${sizeKb} KB`,
    fileName
  };
}

export const CsvImportWizardModal: React.FC<CsvImportWizardModalProps> = ({
  isOpen,
  onClose,
  existingItems,
  onExecuteImport,
  isDarkMode = true
}) => {
  const [step, setStep] = useState<'upload' | 'preview' | 'importing' | 'complete'>('upload');
  const [parseSummary, setParseSummary] = useState<CsvParseSummary | null>(null);
  const [conflictPolicy, setConflictPolicy] = useState<DuplicateConflictPolicy>('OVERWRITE');
  const [customResolutions, setCustomResolutions] = useState<Record<string, DuplicateConflictPolicy>>({});
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [searchFilter, setSearchFilter] = useState<string>('');
  const [importProgress, setImportProgress] = useState<{ percent: number; current: number; total: number }>({
    percent: 0,
    current: 0,
    total: 0
  });
  const [importResult, setImportResult] = useState<{
    inserted: number;
    overwritten: number;
    skipped: number;
  } | null>(null);
  const [showPlainSecrets, setShowPlainSecrets] = useState<boolean>(false);
  const [selectedPreviewTab, setSelectedPreviewTab] = useState<'conflicts' | 'all' | 'new'>('conflicts');
  const [dragActive, setDragActive] = useState<boolean>(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  if (!isOpen) return null;

  // Build lookup map of existing Room items
  const matchExistingItem = (title: string, username: string): ExistingVaultSummary | undefined => {
    const cleanTitle = title.trim().toLowerCase();
    const cleanUser = username.trim().toLowerCase();
    return existingItems.find(item => {
      const matchTitle = item.title.trim().toLowerCase() === cleanTitle;
      const matchUser = item.usernameOrCardholder.trim().toLowerCase() === cleanUser;
      return matchTitle && matchUser;
    });
  };

  const processAndEnrichSummary = (rawSummary: CsvParseSummary): CsvParseSummary => {
    const enrichedRecords = rawSummary.validRecords.map(rec => {
      const existingMatch = matchExistingItem(rec.title, rec.usernameOrCardholder);
      return {
        ...rec,
        isDuplicate: !!existingMatch,
        matchedExistingItem: existingMatch
      };
    });

    return {
      ...rawSummary,
      validRecords: enrichedRecords
    };
  };

  const handleFile = (file: File) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const text = e.target?.result as string;
      if (text) {
        const rawSummary = parseCsvContent(text, file.name, file.size);
        const enriched = processAndEnrichSummary(rawSummary);
        setParseSummary(enriched);
        setCustomResolutions({});
        const hasConflicts = enriched.validRecords.some(r => r.isDuplicate);
        setSelectedPreviewTab(hasConflicts ? 'conflicts' : 'all');
        setStep('preview');
      }
    };
    reader.readAsText(file);
  };

  const handleDrag = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFile(e.dataTransfer.files[0]);
    }
  };

  const handleLoadSample = (sampleCsv: string, name: string) => {
    const rawSummary = parseCsvContent(sampleCsv, name, new Blob([sampleCsv]).size);
    const enriched = processAndEnrichSummary(rawSummary);
    setParseSummary(enriched);
    setCustomResolutions({});
    const hasConflicts = enriched.validRecords.some(r => r.isDuplicate);
    setSelectedPreviewTab(hasConflicts ? 'conflicts' : 'all');
    setStep('preview');
  };

  const handleSetGlobalPolicy = (policy: DuplicateConflictPolicy) => {
    setConflictPolicy(policy);
    // Clear per-row overrides so all follow global policy
    setCustomResolutions({});
  };

  const handleSetRowResolution = (recordId: string, policy: DuplicateConflictPolicy) => {
    setCustomResolutions(prev => ({
      ...prev,
      [recordId]: policy
    }));
  };

  const getEffectiveResolution = (record: ParsedCsvRecord): DuplicateConflictPolicy => {
    if (!record.isDuplicate) return 'KEEP_BOTH'; // New records are always inserted
    return customResolutions[record.id] || conflictPolicy;
  };

  const startBatchImport = async () => {
    if (!parseSummary || parseSummary.validRecords.length === 0) return;

    setStep('importing');
    setIsProcessing(true);

    try {
      const result = await onExecuteImport(
        parseSummary.validRecords,
        conflictPolicy,
        (percent, current, total) => {
          setImportProgress({ percent, current, total });
        },
        customResolutions
      );

      setImportResult(result);
      setIsProcessing(false);
      setStep('complete');
    } catch (err) {
      console.error('Import failed', err);
      setIsProcessing(false);
    }
  };

  const resetWizard = () => {
    setStep('upload');
    setParseSummary(null);
    setImportResult(null);
    setCustomResolutions({});
    setSearchFilter('');
    setImportProgress({ percent: 0, current: 0, total: 0 });
  };

  const duplicateRecords = parseSummary?.validRecords.filter(r => r.isDuplicate) || [];
  const duplicateCount = duplicateRecords.length;
  const newCount = (parseSummary?.validRecords.length || 0) - duplicateCount;

  // Compute live breakdown based on current resolution policies
  const overwriteCount = duplicateRecords.filter(r => getEffectiveResolution(r) === 'OVERWRITE').length;
  const keepBothConflictCount = duplicateRecords.filter(r => getEffectiveResolution(r) === 'KEEP_BOTH').length;
  const skipCount = duplicateRecords.filter(r => getEffectiveResolution(r) === 'SKIP').length;
  const totalIncomingInserts = newCount + keepBothConflictCount;

  // Filter records for table preview
  const filteredPreviewRecords = (parseSummary?.validRecords || []).filter(rec => {
    if (selectedPreviewTab === 'conflicts' && !rec.isDuplicate) return false;
    if (selectedPreviewTab === 'new' && rec.isDuplicate) return false;

    if (searchFilter.trim()) {
      const q = searchFilter.toLowerCase();
      const matchTitle = rec.title.toLowerCase().includes(q);
      const matchUser = rec.usernameOrCardholder.toLowerCase().includes(q);
      const matchExisting = rec.matchedExistingItem?.title.toLowerCase().includes(q);
      return matchTitle || matchUser || matchExisting;
    }
    return true;
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/85 backdrop-blur-md animate-fade-in">
      <motion.div
        initial={{ opacity: 0, scale: 0.96, y: 12 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.96, y: 12 }}
        className={`w-full max-w-4xl max-h-[94vh] flex flex-col rounded-2xl border shadow-2xl overflow-hidden ${
          isDarkMode
            ? 'bg-[#0a0a0a] border-obscura-border text-white'
            : 'bg-white border-slate-200 text-slate-900'
        }`}
      >
        {/* Wizard Header */}
        <div className={`p-4 border-b flex items-center justify-between transition-colors ${
          isDarkMode ? 'bg-[#101010] border-obscura-border' : 'bg-slate-50 border-slate-200'
        }`}>
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-obscura-crimson/15 border border-obscura-crimson/30">
              <FileSpreadsheet className="w-5 h-5 text-obscura-crimson" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-extrabold text-sm sm:text-base tracking-wide">
                  Universal CSV Migration Wizard
                </h3>
                <span className="text-[9px] font-mono font-bold px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                  Room DB SSOT
                </span>
              </div>
              <p className="text-[11px] text-gray-400 font-mono">
                SAF Streaming • Automatic Schema Detection • Visual Conflict Resolution
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            disabled={isProcessing}
            className={`p-2 rounded-xl border transition-all cursor-pointer ${
              isDarkMode
                ? 'bg-[#181818] border-obscura-border text-gray-400 hover:text-white hover:bg-[#222]'
                : 'bg-slate-100 border-slate-200 text-slate-600 hover:text-slate-900'
            }`}
            title="Close Wizard"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Step Indicator Bar */}
        <div className={`px-4 py-2 border-b grid grid-cols-4 gap-2 text-[10px] font-mono font-bold ${
          isDarkMode ? 'bg-[#0e0e0e] border-obscura-border' : 'bg-slate-100 border-slate-200'
        }`}>
          {[
            { id: 'upload', label: '1. Select CSV' },
            { id: 'preview', label: '2. Conflict Resolution' },
            { id: 'importing', label: '3. Encrypt & Batch' },
            { id: 'complete', label: '4. Summary' }
          ].map((s, idx) => {
            const isCurrent = step === s.id;
            const isDone = 
              (step === 'preview' && idx === 0) ||
              (step === 'importing' && idx <= 1) ||
              (step === 'complete' && idx <= 2);

            return (
              <div
                key={s.id}
                className={`py-1.5 px-2 rounded-lg flex items-center justify-center gap-1.5 transition-all ${
                  isCurrent
                    ? 'bg-obscura-crimson text-black font-extrabold shadow-sm'
                    : isDone
                    ? isDarkMode ? 'bg-emerald-950/50 text-emerald-400 border border-emerald-500/30' : 'bg-emerald-100 text-emerald-800'
                    : isDarkMode ? 'bg-white/5 text-gray-500' : 'bg-white text-slate-400'
                }`}
              >
                {isDone ? <Check className="w-3 h-3 stroke-[3]" /> : <span>{idx + 1}.</span>}
                <span className="truncate">{s.label.split('. ')[1]}</span>
              </div>
            );
          })}
        </div>

        {/* Main Wizard Content Body */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {/* STEP 1: UPLOAD / SAF FILE SELECTION */}
          {step === 'upload' && (
            <div className="space-y-4 animate-fade-in">
              <input
                type="file"
                ref={fileInputRef}
                accept=".csv,text/csv,text/plain"
                onChange={(e) => {
                  if (e.target.files && e.target.files[0]) {
                    handleFile(e.target.files[0]);
                  }
                }}
                className="hidden"
              />

              {/* SAF Drag & Drop Zone */}
              <div
                onDragEnter={handleDrag}
                onDragLeave={handleDrag}
                onDragOver={handleDrag}
                onDrop={handleDrop}
                onClick={() => fileInputRef.current?.click()}
                className={`border-2 border-dashed rounded-2xl p-8 text-center cursor-pointer transition-all ${
                  dragActive
                    ? 'border-obscura-crimson bg-obscura-crimson/10 scale-[1.01]'
                    : isDarkMode
                    ? 'border-obscura-border hover:border-obscura-crimson/60 bg-[#121212] hover:bg-[#161616]'
                    : 'border-slate-300 hover:border-red-500 bg-slate-50 hover:bg-slate-100'
                }`}
              >
                <div className="w-14 h-14 mx-auto mb-3 rounded-2xl bg-obscura-crimson/15 border border-obscura-crimson/30 flex items-center justify-center">
                  <Upload className="w-7 h-7 text-obscura-crimson" />
                </div>
                <h4 className="font-extrabold text-sm mb-1">
                  Click to open Android SAF File Picker
                </h4>
                <p className="text-xs text-gray-400 max-w-sm mx-auto mb-3 font-mono">
                  Drag and drop your password manager .CSV export file here
                </p>
                <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/5 border border-obscura-border text-[10px] font-mono text-gray-400">
                  <Lock className="w-3 h-3 text-emerald-400" />
                  <span>Streams purely in RAM • Never saved as plaintext to disk</span>
                </div>
              </div>

              {/* Instant Test Sample Templates */}
              <div className={`p-3.5 rounded-xl border space-y-2.5 ${
                isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-slate-50 border-slate-200'
              }`}>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5">
                    <Sparkles className="w-4 h-4 text-obscura-crimson" />
                    <span className="text-xs font-bold font-mono">Try with Instant Sample Exports:</span>
                  </div>
                  <span className="text-[9px] text-gray-500 font-mono">Includes duplicate conflicts for demo</span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                  <button
                    onClick={() => handleLoadSample(SAMPLE_BITWARDEN_CSV, 'bitwarden_export_2026.csv')}
                    className={`p-3 rounded-xl border text-left transition-all cursor-pointer ${
                      isDarkMode
                        ? 'bg-[#181818] border-obscura-border hover:border-obscura-crimson/50 hover:bg-[#202020]'
                        : 'bg-white border-slate-200 hover:border-red-400'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <div className="flex items-center gap-1.5">
                        <span className="w-2.5 h-2.5 rounded-full bg-blue-500" />
                        <span className="text-xs font-bold">Bitwarden</span>
                      </div>
                      <span className="text-[9px] font-mono font-bold text-amber-400 bg-amber-400/10 px-1.5 py-0.5 rounded border border-amber-400/20">
                        2 Conflicts
                      </span>
                    </div>
                    <p className="text-[10px] text-gray-400 font-mono leading-tight">
                      Netflix & Visa Black Card conflicts + 2FA TOTP
                    </p>
                  </button>

                  <button
                    onClick={() => handleLoadSample(SAMPLE_CHROME_CSV, 'chrome_passwords.csv')}
                    className={`p-3 rounded-xl border text-left transition-all cursor-pointer ${
                      isDarkMode
                        ? 'bg-[#181818] border-obscura-border hover:border-obscura-crimson/50 hover:bg-[#202020]'
                        : 'bg-white border-slate-200 hover:border-red-400'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <div className="flex items-center gap-1.5">
                        <span className="w-2.5 h-2.5 rounded-full bg-amber-500" />
                        <span className="text-xs font-bold">Google Chrome</span>
                      </div>
                      <span className="text-[9px] font-mono font-bold text-amber-400 bg-amber-400/10 px-1.5 py-0.5 rounded border border-amber-400/20">
                        1 Conflict
                      </span>
                    </div>
                    <p className="text-[10px] text-gray-400 font-mono leading-tight">
                      Standard Chrome CSV (Netflix match + 4 new)
                    </p>
                  </button>

                  <button
                    onClick={() => handleLoadSample(SAMPLE_KEEPASS_CSV, 'keepass_database.csv')}
                    className={`p-3 rounded-xl border text-left transition-all cursor-pointer ${
                      isDarkMode
                        ? 'bg-[#181818] border-obscura-border hover:border-obscura-crimson/50 hover:bg-[#202020]'
                        : 'bg-white border-slate-200 hover:border-red-400'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <div className="flex items-center gap-1.5">
                        <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
                        <span className="text-xs font-bold">KeePass CSV</span>
                      </div>
                      <span className="text-[9px] font-mono font-bold text-amber-400 bg-amber-400/10 px-1.5 py-0.5 rounded border border-amber-400/20">
                        1 Conflict
                      </span>
                    </div>
                    <p className="text-[10px] text-gray-400 font-mono leading-tight">
                      TMDB Production API match + comments
                    </p>
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* STEP 2: SCHEMA PREVIEW & INTERACTIVE CONFLICT RESOLUTION TABLE */}
          {step === 'preview' && parseSummary && (
            <div className="space-y-4 animate-fade-in">
              {/* Detected Schema & File Summary Banner */}
              <div className={`p-3.5 rounded-xl border flex flex-wrap items-center justify-between gap-3 ${
                isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-slate-50 border-slate-200'
              }`}>
                <div className="flex items-center gap-3">
                  <div className="p-2.5 rounded-xl bg-obscura-crimson/15 border border-obscura-crimson/30">
                    <FileCheck className="w-5 h-5 text-obscura-crimson" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h4 className="text-xs font-bold text-white">{parseSummary.fileName}</h4>
                      <span className="text-[9px] font-mono font-bold px-2 py-0.5 rounded bg-obscura-crimson/20 text-obscura-crimson border border-obscura-crimson/30">
                        {parseSummary.formatName}
                      </span>
                    </div>
                    <p className="text-[11px] text-gray-400 font-mono mt-0.5">
                      File Size: {parseSummary.rawFileSizeFormatted} • {parseSummary.validRecords.length} records parsed
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setShowPlainSecrets(!showPlainSecrets)}
                    className="px-2.5 py-1.5 rounded-lg bg-[#1a1a1a] hover:bg-[#222] border border-obscura-border text-[11px] font-mono font-bold text-gray-300 hover:text-white flex items-center gap-1.5 transition-all cursor-pointer"
                  >
                    {showPlainSecrets ? <EyeOff className="w-3.5 h-3.5 text-obscura-crimson" /> : <Eye className="w-3.5 h-3.5 text-gray-400" />}
                    <span>{showPlainSecrets ? 'Mask Secrets' : 'Reveal Secrets'}</span>
                  </button>

                  <button
                    onClick={resetWizard}
                    className="px-2.5 py-1.5 rounded-lg border border-obscura-border text-xs font-mono text-gray-400 hover:text-white hover:bg-[#1c1c1c] transition-all cursor-pointer"
                  >
                    Change File
                  </button>
                </div>
              </div>

              {/* Conflict Status Bar & Batch Policy Selector */}
              <div className={`p-3.5 rounded-xl border space-y-3 ${
                isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200'
              }`}>
                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-inherit pb-2.5">
                  <div className="flex items-center gap-2">
                    <Layers className="w-4 h-4 text-obscura-crimson" />
                    <div>
                      <span className="text-xs font-extrabold font-mono text-white">
                        Duplicate Conflict Resolution Engine
                      </span>
                      <p className="text-[10px] text-gray-400 font-mono">
                        Matching key: <code className="text-gray-300 font-bold">title.trim() + username.trim()</code>
                      </p>
                    </div>
                  </div>

                  {/* Summary Metric Badges */}
                  <div className="flex items-center gap-2 font-mono text-[10px]">
                    <span className="px-2 py-0.5 rounded-md bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-bold">
                      {newCount} Clean New
                    </span>
                    {duplicateCount > 0 ? (
                      <span className="px-2 py-0.5 rounded-md bg-amber-500/15 border border-amber-500/40 text-amber-300 font-bold flex items-center gap-1">
                        <AlertTriangle className="w-3 h-3 text-amber-400" />
                        <span>{duplicateCount} Conflicts</span>
                      </span>
                    ) : (
                      <span className="px-2 py-0.5 rounded-md bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-bold">
                        0 Conflicts
                      </span>
                    )}
                  </div>
                </div>

                {/* Conflict Strategy Preset Selector */}
                <div className="space-y-1.5">
                  <label className="text-[11px] font-bold font-mono text-gray-300 flex items-center justify-between">
                    <span>Default Batch Action for Conflicting Records:</span>
                    {Object.keys(customResolutions).length > 0 && (
                      <button
                        onClick={() => setCustomResolutions({})}
                        className="text-[10px] text-obscura-crimson hover:underline font-mono"
                      >
                        Reset {Object.keys(customResolutions).length} custom overrides to default
                      </button>
                    )}
                  </label>

                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                    {/* OVERWRITE Preset Card */}
                    <button
                      onClick={() => handleSetGlobalPolicy('OVERWRITE')}
                      className={`p-2.5 rounded-xl border text-left transition-all cursor-pointer ${
                        conflictPolicy === 'OVERWRITE'
                          ? 'border-amber-500 ring-1 ring-amber-500 bg-amber-500/10'
                          : isDarkMode
                          ? 'bg-[#181818] border-obscura-border text-gray-400 hover:text-white'
                          : 'bg-slate-50 border-slate-200 text-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1">
                        <div className="flex items-center gap-1.5">
                          <input
                            type="radio"
                            checked={conflictPolicy === 'OVERWRITE'}
                            onChange={() => handleSetGlobalPolicy('OVERWRITE')}
                            className="accent-amber-500"
                          />
                          <span className={`text-xs font-extrabold ${conflictPolicy === 'OVERWRITE' ? 'text-amber-400' : 'text-gray-300'}`}>
                            OVERWRITE
                          </span>
                        </div>
                        <span className="text-[9px] font-mono font-bold px-1.5 py-0.2 rounded bg-amber-500/20 text-amber-300 border border-amber-500/30">
                          {overwriteCount} items
                        </span>
                      </div>
                      <p className="text-[9.5px] text-gray-400 font-mono leading-tight">
                        Updates existing Room DB secrets, tags, and notes.
                      </p>
                    </button>

                    {/* KEEP_BOTH Preset Card */}
                    <button
                      onClick={() => handleSetGlobalPolicy('KEEP_BOTH')}
                      className={`p-2.5 rounded-xl border text-left transition-all cursor-pointer ${
                        conflictPolicy === 'KEEP_BOTH'
                          ? 'border-cyan-500 ring-1 ring-cyan-500 bg-cyan-500/10'
                          : isDarkMode
                          ? 'bg-[#181818] border-obscura-border text-gray-400 hover:text-white'
                          : 'bg-slate-50 border-slate-200 text-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1">
                        <div className="flex items-center gap-1.5">
                          <input
                            type="radio"
                            checked={conflictPolicy === 'KEEP_BOTH'}
                            onChange={() => handleSetGlobalPolicy('KEEP_BOTH')}
                            className="accent-cyan-400"
                          />
                          <span className={`text-xs font-extrabold ${conflictPolicy === 'KEEP_BOTH' ? 'text-cyan-400' : 'text-gray-300'}`}>
                            KEEP BOTH
                          </span>
                        </div>
                        <span className="text-[9px] font-mono font-bold px-1.5 py-0.2 rounded bg-cyan-500/20 text-cyan-300 border border-cyan-500/30">
                          {keepBothConflictCount} items
                        </span>
                      </div>
                      <p className="text-[9.5px] text-gray-400 font-mono leading-tight">
                        Inserts incoming items as new copies with unique UUIDs.
                      </p>
                    </button>

                    {/* SKIP Preset Card */}
                    <button
                      onClick={() => handleSetGlobalPolicy('SKIP')}
                      className={`p-2.5 rounded-xl border text-left transition-all cursor-pointer ${
                        conflictPolicy === 'SKIP'
                          ? 'border-zinc-500 ring-1 ring-zinc-500 bg-zinc-500/10'
                          : isDarkMode
                          ? 'bg-[#181818] border-obscura-border text-gray-400 hover:text-white'
                          : 'bg-slate-50 border-slate-200 text-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1">
                        <div className="flex items-center gap-1.5">
                          <input
                            type="radio"
                            checked={conflictPolicy === 'SKIP'}
                            onChange={() => handleSetGlobalPolicy('SKIP')}
                            className="accent-zinc-400"
                          />
                          <span className={`text-xs font-extrabold ${conflictPolicy === 'SKIP' ? 'text-zinc-200' : 'text-gray-300'}`}>
                            SKIP MATCHES
                          </span>
                        </div>
                        <span className="text-[9px] font-mono font-bold px-1.5 py-0.2 rounded bg-zinc-700/40 text-zinc-300 border border-zinc-600/40">
                          {skipCount} items
                        </span>
                      </div>
                      <p className="text-[9.5px] text-gray-400 font-mono leading-tight">
                        Ignores imported duplicates; keeps existing DB entries intact.
                      </p>
                    </button>
                  </div>
                </div>
              </div>

              {/* CONFLICT RESOLUTION & CREDENTIAL COMPARISON TABLE */}
              <div className={`border rounded-xl overflow-hidden shadow-lg ${
                isDarkMode ? 'bg-[#101010] border-obscura-border' : 'bg-white border-slate-200'
              }`}>
                {/* Table Control Bar */}
                <div className={`p-3 border-b flex flex-wrap items-center justify-between gap-2.5 text-xs font-mono ${
                  isDarkMode ? 'bg-[#141414] border-obscura-border' : 'bg-slate-100 border-slate-200'
                }`}>
                  {/* Tabbed View Filters */}
                  <div className="flex items-center gap-1.5">
                    {duplicateCount > 0 && (
                      <button
                        onClick={() => setSelectedPreviewTab('conflicts')}
                        className={`px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer ${
                          selectedPreviewTab === 'conflicts'
                            ? 'bg-amber-500 text-black font-extrabold shadow-sm'
                            : 'bg-[#1e1e1e] text-amber-300 hover:bg-[#252525]'
                        }`}
                      >
                        <AlertTriangle className="w-3.5 h-3.5" />
                        <span>Conflict Comparisons ({duplicateCount})</span>
                      </button>
                    )}

                    <button
                      onClick={() => setSelectedPreviewTab('all')}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                        selectedPreviewTab === 'all'
                          ? 'bg-obscura-crimson text-black font-extrabold shadow-sm'
                          : 'bg-[#1e1e1e] text-gray-400 hover:text-white'
                      }`}
                    >
                      All Records ({parseSummary.validRecords.length})
                    </button>

                    <button
                      onClick={() => setSelectedPreviewTab('new')}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                        selectedPreviewTab === 'new'
                          ? 'bg-emerald-500 text-black font-extrabold shadow-sm'
                          : 'bg-[#1e1e1e] text-gray-400 hover:text-white'
                      }`}
                    >
                      New Unique ({newCount})
                    </button>
                  </div>

                  {/* Filter Search Input */}
                  <div className="relative min-w-[200px]">
                    <Search className="w-3.5 h-3.5 absolute left-2.5 top-2.5 text-gray-500 pointer-events-none" />
                    <input
                      type="text"
                      placeholder="Filter title or username..."
                      value={searchFilter}
                      onChange={(e) => setSearchFilter(e.target.value)}
                      className="w-full pl-8 pr-3 py-1.5 bg-[#0a0a0a] border border-obscura-border rounded-lg text-xs text-white placeholder:text-gray-600 focus:outline-none focus:border-obscura-crimson font-mono"
                    />
                  </div>
                </div>

                {/* Table Header */}
                <div className="hidden md:grid grid-cols-12 gap-2 px-3 py-2 bg-[#0c0c0c] border-b border-obscura-border/60 text-[10px] font-mono font-bold text-gray-400 uppercase tracking-wider">
                  <div className="col-span-5 flex items-center gap-1.5">
                    <Database className="w-3 h-3 text-gray-500" />
                    <span>Existing Database Record</span>
                  </div>
                  <div className="col-span-4 flex items-center gap-1.5">
                    <Upload className="w-3 h-3 text-sky-400" />
                    <span>Incoming CSV Record</span>
                  </div>
                  <div className="col-span-3 text-right">
                    <span>Conflict Resolution Status</span>
                  </div>
                </div>

                {/* Table Body Rows */}
                <div className="max-h-72 overflow-y-auto divide-y divide-obscura-border/40 font-mono text-xs">
                  {filteredPreviewRecords.length === 0 ? (
                    <div className="py-8 text-center text-gray-500">
                      <Filter className="w-6 h-6 mx-auto mb-2 opacity-40" />
                      <p>No records match the active filter</p>
                    </div>
                  ) : (
                    filteredPreviewRecords.map((item, idx) => {
                      const effectiveStatus = getEffectiveResolution(item);
                      const isConflict = item.isDuplicate && item.matchedExistingItem;
                      const existing = item.matchedExistingItem;
                      const isPasswordDifferent = existing && existing.secretValue !== item.secretValue;

                      return (
                        <div
                          key={item.id}
                          className={`p-3 transition-colors ${
                            isConflict
                              ? effectiveStatus === 'OVERWRITE'
                                ? 'bg-amber-500/[0.04] hover:bg-amber-500/[0.08]'
                                : effectiveStatus === 'KEEP_BOTH'
                                ? 'bg-cyan-500/[0.04] hover:bg-cyan-500/[0.08]'
                                : 'bg-zinc-800/20 hover:bg-zinc-800/30'
                              : 'hover:bg-white/5'
                          }`}
                        >
                          <div className="grid grid-cols-1 md:grid-cols-12 gap-3 items-center">
                            
                            {/* COLUMN 1: Existing Record in Room DB */}
                            <div className="md:col-span-5 min-w-0">
                              {existing ? (
                                <div className="space-y-1 p-2 rounded-lg bg-[#0e0e0e] border border-obscura-border/80">
                                  <div className="flex items-center justify-between gap-1.5">
                                    <div className="flex items-center gap-1.5 truncate">
                                      <span className="w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" />
                                      <span className="font-extrabold text-white truncate text-xs">
                                        {existing.title}
                                      </span>
                                    </div>
                                    <span className="text-[9px] px-1.5 py-0.2 rounded bg-white/10 text-gray-300 font-bold shrink-0">
                                      {existing.category || 'LOGIN'}
                                    </span>
                                  </div>

                                  <div className="text-[11px] text-gray-400 truncate">
                                    👤 {existing.usernameOrCardholder || 'No Username'}
                                  </div>

                                  <div className="flex items-center justify-between text-[10px] text-gray-500 pt-0.5">
                                    <span className="truncate">
                                      🔑 {showPlainSecrets ? existing.secretValue || '••••••••' : '••••••••••••'}
                                    </span>
                                    <span className="text-[9px] opacity-70 shrink-0">Current DB</span>
                                  </div>
                                </div>
                              ) : (
                                <div className="p-2 rounded-lg bg-[#0a0a0a] border border-dashed border-obscura-border/50 text-gray-500 text-[11px] flex items-center gap-1.5">
                                  <CheckCheck className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                                  <span>No existing conflict • Clean new insertion</span>
                                </div>
                              )}
                            </div>

                            {/* COLUMN 2: Incoming CSV Record */}
                            <div className="md:col-span-4 min-w-0">
                              <div className={`space-y-1 p-2 rounded-lg border ${
                                isConflict 
                                  ? 'bg-[#121212] border-sky-500/30' 
                                  : 'bg-[#0e0e0e] border-emerald-500/30'
                              }`}>
                                <div className="flex items-center justify-between gap-1.5">
                                  <div className="flex items-center gap-1.5 truncate">
                                    <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${isConflict ? 'bg-sky-400' : 'bg-emerald-400'}`} />
                                    <span className="font-extrabold text-white truncate text-xs">
                                      {item.title}
                                    </span>
                                  </div>
                                  <span className="text-[9px] px-1.5 py-0.2 rounded bg-sky-500/20 text-sky-300 font-bold shrink-0">
                                    {item.category}
                                  </span>
                                </div>

                                <div className="text-[11px] text-gray-300 truncate">
                                  👤 {item.usernameOrCardholder || 'No Username'}
                                </div>

                                <div className="flex items-center justify-between text-[10px] pt-0.5">
                                  <span className="font-mono text-emerald-400 truncate">
                                    🔑 {showPlainSecrets ? item.secretValue : '••••••••••••'}
                                  </span>
                                  {isPasswordDifferent && (
                                    <span className="text-[8.5px] px-1 rounded bg-amber-500/20 text-amber-300 font-bold border border-amber-500/40">
                                      New Pass
                                    </span>
                                  )}
                                </div>
                              </div>
                            </div>

                            {/* COLUMN 3: Visual Resolution Chips & Interactive Toggles */}
                            <div className="md:col-span-3 flex flex-col items-end gap-1.5">
                              {isConflict ? (
                                <div className="w-full flex flex-col items-end gap-1">
                                  {/* Color-Coded Active Status Chip */}
                                  <div className="flex items-center gap-1">
                                    {effectiveStatus === 'OVERWRITE' && (
                                      <span className="px-2.5 py-1 rounded-full text-[10px] font-extrabold bg-amber-500/20 text-amber-300 border border-amber-500/50 flex items-center gap-1 shadow-sm">
                                        <RefreshCw className="w-3 h-3 text-amber-400" />
                                        <span>OVERWRITE</span>
                                      </span>
                                    )}

                                    {effectiveStatus === 'KEEP_BOTH' && (
                                      <span className="px-2.5 py-1 rounded-full text-[10px] font-extrabold bg-cyan-500/20 text-cyan-300 border border-cyan-500/50 flex items-center gap-1 shadow-sm">
                                        <CopyPlus className="w-3 h-3 text-cyan-400" />
                                        <span>KEEP BOTH</span>
                                      </span>
                                    )}

                                    {effectiveStatus === 'SKIP' && (
                                      <span className="px-2.5 py-1 rounded-full text-[10px] font-extrabold bg-zinc-700/40 text-zinc-300 border border-zinc-600/50 flex items-center gap-1 shadow-sm">
                                        <Ban className="w-3 h-3 text-zinc-400" />
                                        <span>SKIP</span>
                                      </span>
                                    )}
                                  </div>

                                  {/* Per-Row Quick Action Toggle Pills */}
                                  <div className="flex items-center gap-1 bg-[#181818] p-0.5 rounded-lg border border-obscura-border">
                                    <button
                                      type="button"
                                      onClick={() => handleSetRowResolution(item.id, 'OVERWRITE')}
                                      className={`px-1.5 py-0.5 rounded text-[9px] font-bold transition-all cursor-pointer ${
                                        effectiveStatus === 'OVERWRITE'
                                          ? 'bg-amber-500 text-black font-extrabold'
                                          : 'text-gray-400 hover:text-amber-300'
                                      }`}
                                      title="Overwrite database entry"
                                    >
                                      Ovr
                                    </button>

                                    <button
                                      type="button"
                                      onClick={() => handleSetRowResolution(item.id, 'KEEP_BOTH')}
                                      className={`px-1.5 py-0.5 rounded text-[9px] font-bold transition-all cursor-pointer ${
                                        effectiveStatus === 'KEEP_BOTH'
                                          ? 'bg-cyan-400 text-black font-extrabold'
                                          : 'text-gray-400 hover:text-cyan-300'
                                      }`}
                                      title="Keep both as separate records"
                                    >
                                      Dual
                                    </button>

                                    <button
                                      type="button"
                                      onClick={() => handleSetRowResolution(item.id, 'SKIP')}
                                      className={`px-1.5 py-0.5 rounded text-[9px] font-bold transition-all cursor-pointer ${
                                        effectiveStatus === 'SKIP'
                                          ? 'bg-zinc-400 text-black font-extrabold'
                                          : 'text-gray-400 hover:text-white'
                                      }`}
                                      title="Skip this import record"
                                    >
                                      Skip
                                    </button>
                                  </div>
                                </div>
                              ) : (
                                <div className="flex items-center gap-1 text-right">
                                  <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/40 flex items-center gap-1">
                                    <Check className="w-3 h-3 stroke-[3]" />
                                    <span>INSERT NEW</span>
                                  </span>
                                </div>
                              )}
                            </div>

                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>

              {/* Real-time Pre-Flight Batch Commit Projection */}
              <div className="p-3 rounded-xl bg-gradient-to-r from-obscura-crimson/10 via-[#141414] to-obscura-crimson/10 border border-obscura-crimson/30 flex flex-wrap items-center justify-between gap-3 text-xs font-mono">
                <div className="flex items-center gap-2">
                  <Shield className="w-4 h-4 text-obscura-crimson" />
                  <span className="text-gray-200">
                    Projected Batch Action: 
                    <strong className="text-emerald-400 ml-1">+{totalIncomingInserts} Inserted</strong>
                    {overwriteCount > 0 && <strong className="text-amber-400 ml-1.5">⚡ {overwriteCount} Overwritten</strong>}
                    {skipCount > 0 && <strong className="text-gray-400 ml-1.5">⊘ {skipCount} Skipped</strong>}
                  </span>
                </div>

                <span className="text-[10px] text-gray-400 font-mono">
                  Room SQLite @Transaction Guaranteed
                </span>
              </div>
            </div>
          )}

          {/* STEP 3: ATOMIC BATCH TRANSACTION PROGRESS */}
          {step === 'importing' && (
            <div className="py-8 text-center space-y-4 animate-fade-in">
              <div className="relative w-20 h-20 mx-auto flex items-center justify-center">
                <motion.div
                  animate={{ rotate: 360 }}
                  transition={{ repeat: Infinity, duration: 1.5, ease: 'linear' }}
                  className="w-full h-full rounded-full border-4 border-obscura-crimson/20 border-t-obscura-crimson"
                />
                <Database className="w-8 h-8 text-obscura-crimson absolute" />
              </div>

              <div className="space-y-1">
                <h4 className="font-extrabold text-sm text-white">
                  Executing Room SQLite @Transaction
                </h4>
                <p className="text-xs text-gray-400 font-mono">
                  Encrypting payloads with AES-256 GCM into SQLCipher database...
                </p>
              </div>

              {/* Progress Bar */}
              <div className="max-w-md mx-auto space-y-1.5">
                <div className="flex justify-between text-[11px] font-mono text-gray-400">
                  <span>Record {importProgress.current} of {importProgress.total}</span>
                  <span className="font-bold text-white">{importProgress.percent}%</span>
                </div>
                <div className="w-full h-2.5 bg-[#181818] rounded-full overflow-hidden border border-obscura-border">
                  <motion.div
                    className="h-full bg-obscura-crimson"
                    initial={{ width: '0%' }}
                    animate={{ width: `${importProgress.percent}%` }}
                    transition={{ duration: 0.1 }}
                  />
                </div>
              </div>
            </div>
          )}

          {/* STEP 4: COMPLETION SUMMARY */}
          {step === 'complete' && importResult && (
            <div className="py-6 text-center space-y-5 animate-fade-in">
              <div className="w-16 h-16 mx-auto rounded-2xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center">
                <CheckCircle2 className="w-8 h-8 text-emerald-400" />
              </div>

              <div>
                <h4 className="font-extrabold text-base text-white">
                  Batch Import Transaction Committed!
                </h4>
                <p className="text-xs text-gray-400 font-mono mt-1">
                  All resolved secrets have been encrypted and synchronized to Room SSOT.
                </p>
              </div>

              {/* Metrics Summary Badges */}
              <div className="grid grid-cols-3 gap-2.5 max-w-md mx-auto font-mono">
                <div className="p-3 rounded-xl bg-[#121212] border border-emerald-500/30">
                  <span className="text-[10px] text-gray-400 block font-bold">INSERTED</span>
                  <span className="text-lg font-extrabold text-emerald-400">
                    +{importResult.inserted}
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-[#121212] border border-amber-500/30">
                  <span className="text-[10px] text-gray-400 block font-bold">OVERWRITTEN</span>
                  <span className="text-lg font-extrabold text-amber-400">
                    {importResult.overwritten}
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-[#121212] border border-zinc-500/30">
                  <span className="text-[10px] text-gray-400 block font-bold">SKIPPED</span>
                  <span className="text-lg font-extrabold text-zinc-400">
                    {importResult.skipped}
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Wizard Footer Controls */}
        <div className={`p-4 border-t flex items-center justify-between transition-colors ${
          isDarkMode ? 'bg-[#101010] border-obscura-border' : 'bg-slate-50 border-slate-200'
        }`}>
          {step === 'preview' ? (
            <>
              <button
                onClick={() => setStep('upload')}
                className={`px-4 py-2 rounded-xl border text-xs font-bold transition-all cursor-pointer ${
                  isDarkMode ? 'bg-[#181818] border-obscura-border text-gray-300 hover:text-white hover:bg-[#222]' : 'bg-white border-slate-200'
                }`}
              >
                Back
              </button>

              <div className="flex items-center gap-2">
                <button
                  onClick={startBatchImport}
                  className="px-5 py-2.5 rounded-xl bg-obscura-crimson text-black font-extrabold text-xs flex items-center gap-1.5 shadow-lg shadow-obscura-crimson/20 hover:brightness-110 active:scale-95 transition-all cursor-pointer"
                >
                  <Database className="w-4 h-4" />
                  <span>
                    Commit Batch ({totalIncomingInserts} Inserted, {overwriteCount} Overwritten)
                  </span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            </>
          ) : step === 'complete' ? (
            <button
              onClick={onClose}
              className="w-full py-2.5 rounded-xl bg-obscura-crimson text-black font-extrabold text-xs flex items-center justify-center gap-2 shadow-lg shadow-obscura-crimson/20 hover:brightness-110 transition-all cursor-pointer"
            >
              <Check className="w-4 h-4 stroke-[3]" />
              <span>Finish & Return to Vault</span>
            </button>
          ) : step === 'upload' ? (
            <button
              onClick={onClose}
              className={`w-full py-2 rounded-xl border text-xs font-bold transition-all cursor-pointer ${
                isDarkMode ? 'bg-[#181818] border-obscura-border text-gray-400 hover:text-white hover:bg-[#222]' : 'bg-white border-slate-200'
              }`}
            >
              Cancel
            </button>
          ) : null}
        </div>
      </motion.div>
    </div>
  );
};
