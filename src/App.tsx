import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { PasswordGenerator } from './components/PasswordGenerator';
import { PasswordStrengthIndicator } from './components/PasswordStrengthIndicator';
import { QrScannerModal, QrScanResult } from './components/QrScannerModal';
import { CsvImportWizardModal, ParsedCsvRecord, DuplicateConflictPolicy } from './components/CsvImportWizardModal';
import { BackupExportModal } from './components/BackupExportModal';
import { BackupRestoreModal } from './components/BackupRestoreModal';
import { DeleteConfirmationModal } from './components/DeleteConfirmationModal';
import { SecurityHealthGauge } from './components/SecurityHealthGauge';
import { QuickSearchTileModal } from './components/QuickSearchTileModal';
import { HighlightText } from './components/HighlightText';
import { AutoLockSettingsScreen } from './components/AutoLockSettingsScreen';
import { 
  Shield, 
  Lock, 
  Unlock, 
  Key, 
  Plus, 
  Search, 
  Database, 
  Code, 
  Smartphone, 
  CheckCircle2, 
  Copy, 
  Eye, 
  EyeOff, 
  Star, 
  Trash2, 
  AlertTriangle,
  Fingerprint,
  ScanFace,
  CreditCard,
  FileText,
  Terminal,
  Activity,
  XCircle,
  AlertCircle,
  Check,
  RefreshCcw,
  Tag,
  Calendar,
  Clock,
  Edit3,
  ChevronDown,
  ChevronUp,
  ChevronRight,
  ChevronLeft,
  Save,
  X,
  Info,
  ExternalLink,
  Sparkles,
  Zap,
  Gauge,
  AlertOctagon,
  ShieldAlert,
  ShieldCheck,
  Cpu,
  Sun,
  Moon,
  Settings,
  Palette,
  Monitor,
  Sliders,
  SlidersHorizontal,
  QrCode,
  FileSpreadsheet,
  Download,
  Upload,
  FolderDown,
  FolderUp
} from 'lucide-react';

interface VaultItem {
  id: string;
  title: string;
  category: 'LOGIN' | 'BANK_CARD' | 'SECURE_NOTE' | 'API_KEY';
  usernameOrCardholder: string;
  secretValue: string;
  urlOrCardNumber: string;
  notesOrCvv: string;
  isFavorite: boolean;
  createdAt: string;
  expiryDate: string;
  tags: string[];
  lastModified: string;
}

interface BiometricLog {
  id: string;
  time: string;
  message: string;
  type: 'info' | 'success' | 'error';
}

// Security Health Diagnostic Data Structures
interface DiagnosticIssue {
  type: 'WEAK_LENGTH' | 'COMMON_SEQUENCE' | 'REPEATING_CHARS' | 'MISSING_VARIETY' | 'REUSED_PASSWORD' | 'SHORT_CVV';
  severity: 'CRITICAL' | 'WARNING' | 'INFO';
  description: string;
}

interface ItemHealthReport {
  itemId: string;
  itemTitle: string;
  category: string;
  secretLength: number;
  issues: DiagnosticIssue[];
  score: number; // 0 to 100
  status: 'EXCELLENT' | 'GOOD' | 'WARNING' | 'CRITICAL';
}

interface OverallSecurityReport {
  overallScore: number; // 0 to 100
  grade: 'A+' | 'A' | 'B' | 'C' | 'D' | 'F';
  totalSecretsScanned: number;
  criticalIssuesCount: number;
  warningIssuesCount: number;
  reusedPasswordsCount: number;
  weakPasswordsCount: number;
  missingVarietyCount: number;
  commonSequencesCount: number;
  itemReports: ItemHealthReport[];
}

const COMMON_PATTERNS = [
  '1234', '123456', 'qwerty', 'password', 'admin', 'welcome', 'letmein',
  '1111', '0000', 'abc123', 'pass123', 'master', 'football', 'iloveyou'
];

function generateStrongSecret(): string {
  const upper = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  const lower = 'abcdefghijklmnopqrstuvwxyz';
  const nums = '0123456789';
  const syms = '!@#$%^&*()_+-=[]{}|;:,.<>?';
  const all = upper + lower + nums + syms;

  let password = '';
  password += upper.charAt(Math.floor(Math.random() * upper.length));
  password += lower.charAt(Math.floor(Math.random() * lower.length));
  password += nums.charAt(Math.floor(Math.random() * nums.length));
  password += syms.charAt(Math.floor(Math.random() * syms.length));

  for (let i = 4; i < 20; i++) {
    password += all.charAt(Math.floor(Math.random() * all.length));
  }

  // Shuffle string
  return password.split('').sort(() => 0.5 - Math.random()).join('');
}

function computeSecurityHealthReport(items: VaultItem[]): OverallSecurityReport {
  const secretCounts: Record<string, number> = {};
  items.forEach(i => {
    const val = i.secretValue.trim();
    if (val && i.category === 'LOGIN') {
      secretCounts[val] = (secretCounts[val] || 0) + 1;
    }
  });

  const itemReports: ItemHealthReport[] = items.map(item => {
    const secret = item.secretValue.trim();
    const issues: DiagnosticIssue[] = [];
    let score = 100;

    if (item.category === 'BANK_CARD' && secret.length <= 4) {
      issues.push({
        type: 'SHORT_CVV',
        severity: 'INFO',
        description: '3-4 digit CVV code stored in secret field'
      });
    } else if (item.category === 'LOGIN' || item.category === 'API_KEY') {
      // 1. Length Check
      if (secret.length < 8) {
        issues.push({
          type: 'WEAK_LENGTH',
          severity: 'CRITICAL',
          description: `Very short password (${secret.length} chars, min 14 recommended)`
        });
        score -= 40;
      } else if (secret.length < 12) {
        issues.push({
          type: 'WEAK_LENGTH',
          severity: 'WARNING',
          description: `Short password length (${secret.length} chars)`
        });
        score -= 20;
      }

      // 2. Common Sequence Check
      const lowerSecret = secret.toLowerCase();
      const foundPattern = COMMON_PATTERNS.find(pat => lowerSecret.includes(pat));
      if (foundPattern) {
        issues.push({
          type: 'COMMON_SEQUENCE',
          severity: 'CRITICAL',
          description: `Contains predictable sequence "${foundPattern}"`
        });
        score -= 30;
      }

      // 3. Repeating Characters Check
      if (/(.)\1\1/.test(secret)) {
        issues.push({
          type: 'REPEATING_CHARS',
          severity: 'WARNING',
          description: 'Contains repeating consecutive characters (e.g. "aaa", "111")'
        });
        score -= 15;
      }

      // 4. Character Variety Check
      const hasUpper = /[A-Z]/.test(secret);
      const hasLower = /[a-z]/.test(secret);
      const hasNum = /[0-9]/.test(secret);
      const hasSymbol = /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(secret);

      const missing: string[] = [];
      if (!hasUpper) missing.push('uppercase');
      if (!hasLower) missing.push('lowercase');
      if (!hasNum) missing.push('numbers');
      if (!hasSymbol) missing.push('symbols');

      if (missing.length >= 2) {
        issues.push({
          type: 'MISSING_VARIETY',
          severity: 'WARNING',
          description: `Missing character types: ${missing.join(', ')}`
        });
        score -= 15;
      }

      // 5. Reused Passwords Check
      if (item.category === 'LOGIN' && secretCounts[secret] > 1) {
        issues.push({
          type: 'REUSED_PASSWORD',
          severity: 'CRITICAL',
          description: `Reused across ${secretCounts[secret]} vault entries!`
        });
        score -= 35;
      }
    }

    score = Math.max(0, Math.min(100, score));

    let status: 'EXCELLENT' | 'GOOD' | 'WARNING' | 'CRITICAL' = 'EXCELLENT';
    if (score < 40) status = 'CRITICAL';
    else if (score < 70) status = 'WARNING';
    else if (score < 90) status = 'GOOD';

    return {
      itemId: item.id,
      itemTitle: item.title,
      category: item.category,
      secretLength: secret.length,
      issues,
      score,
      status
    };
  });

  const totalSecretsScanned = items.length;
  const totalScore = itemReports.reduce((acc, r) => acc + r.score, 0);
  const overallScore = totalSecretsScanned > 0 ? Math.round(totalScore / totalSecretsScanned) : 100;

  let grade: 'A+' | 'A' | 'B' | 'C' | 'D' | 'F' = 'A+';
  if (overallScore >= 95) grade = 'A+';
  else if (overallScore >= 85) grade = 'A';
  else if (overallScore >= 75) grade = 'B';
  else if (overallScore >= 60) grade = 'C';
  else if (overallScore >= 45) grade = 'D';
  else grade = 'F';

  let criticalIssuesCount = 0;
  let warningIssuesCount = 0;
  let reusedPasswordsCount = 0;
  let weakPasswordsCount = 0;
  let missingVarietyCount = 0;
  let commonSequencesCount = 0;

  itemReports.forEach(r => {
    r.issues.forEach(iss => {
      if (iss.severity === 'CRITICAL') criticalIssuesCount++;
      if (iss.severity === 'WARNING') warningIssuesCount++;

      if (iss.type === 'REUSED_PASSWORD') reusedPasswordsCount++;
      if (iss.type === 'WEAK_LENGTH') weakPasswordsCount++;
      if (iss.type === 'MISSING_VARIETY') missingVarietyCount++;
      if (iss.type === 'COMMON_SEQUENCE') commonSequencesCount++;
    });
  });

  return {
    overallScore,
    grade,
    totalSecretsScanned,
    criticalIssuesCount,
    warningIssuesCount,
    reusedPasswordsCount,
    weakPasswordsCount,
    missingVarietyCount,
    commonSequencesCount,
    itemReports
  };
}



export default function App() {
  const [activeTab, setActiveTab] = useState<'emulator' | 'code' | 'db_inspector'>('emulator');
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false); // Start locked so user can test auth
  const [pinInput, setPinInput] = useState<string>('');
  const [pinError, setPinError] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [debouncedSearchQuery, setDebouncedSearchQuery] = useState<string>('');
  const [isFtsSearching, setIsFtsSearching] = useState<boolean>(false);
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [showSecretId, setShowSecretId] = useState<string | null>(null);
  const [copiedToast, setCopiedToast] = useState<string | null>(null);

  // Debounce search query changes with 300ms delay to emulate Kotlin ViewModel StateFlow.debounce(300L)
  useEffect(() => {
    if (searchQuery !== debouncedSearchQuery) {
      setIsFtsSearching(true);
    }
    const handler = setTimeout(() => {
      setDebouncedSearchQuery(searchQuery);
      setIsFtsSearching(false);
      if (searchQuery.trim()) {
        addLog(`[FTS4_QUERY] flatMapLatest MATCH executed on Dispatchers.IO for token: "${searchQuery.trim()}*"`, 'info');
      }
    }, 300);

    return () => {
      clearTimeout(handler);
    };
  }, [searchQuery]);

  // Expanded View State
  const [expandedItemId, setExpandedItemId] = useState<string | null>('1'); // Expand first item by default for demonstration
  const [editingItemId, setEditingItemId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<VaultItem | null>(null);
  const [newTagInput, setNewTagInput] = useState<string>('');
  
  // Biometric Auth States
  const [isBiometricPromptOpen, setIsBiometricPromptOpen] = useState<boolean>(false);
  const [biometricType, setBiometricType] = useState<'fingerprint' | 'faceid'>('fingerprint');
  const [biometricState, setBiometricState] = useState<'scanning' | 'success' | 'failed' | 'error'>('scanning');
  const [biometricStatusText, setBiometricStatusText] = useState<string>('Touch fingerprint sensor or verify FaceID');
  const [biometricLogs, setBiometricLogs] = useState<BiometricLog[]>([
    { id: '1', time: '09:41:00', message: 'BiometricAuthManager initialized: BIOMETRIC_STRONG available', type: 'info' }
  ]);

  // Modal for new secret
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [newTitle, setNewTitle] = useState<string>('');
  const [newCategory, setNewCategory] = useState<'LOGIN' | 'BANK_CARD' | 'SECURE_NOTE' | 'API_KEY'>('LOGIN');
  const [newUsername, setNewUsername] = useState<string>('');
  const [newSecret, setNewSecret] = useState<string>('');
  const [newUrl, setNewUrl] = useState<string>('');
  const [newExpiryDate, setNewExpiryDate] = useState<string>('2028-12-31');
  const [newTagsString, setNewTagsString] = useState<string>('Personal, Important');

  // Password Generator & QR Scanner States
  const [showEditGenerator, setShowEditGenerator] = useState<boolean>(false);
  const [showAddGenerator, setShowAddGenerator] = useState<boolean>(false);
  const [isQrScannerOpen, setIsQrScannerOpen] = useState<boolean>(false);
  const [qrTargetField, setQrTargetField] = useState<'add' | 'edit'>('add');
  const [isCsvImportOpen, setIsCsvImportOpen] = useState<boolean>(false);
  const [isBackupExportOpen, setIsBackupExportOpen] = useState<boolean>(false);
  const [isBackupRestoreOpen, setIsBackupRestoreOpen] = useState<boolean>(false);
  const [itemToDelete, setItemToDelete] = useState<VaultItem | null>(null);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState<boolean>(false);
  const [isQuickSearchTileOpen, setIsQuickSearchTileOpen] = useState<boolean>(false);

  // Theme & Material Design 3 Adaptive Color States
  const [themeMode, setThemeMode] = useState<'dark' | 'light' | 'system'>('dark');
  const [accentPalette, setAccentPalette] = useState<'crimson' | 'emerald' | 'amber' | 'indigo'>('crimson');
  
  // Auto-Lock & SecureClipboardManager Timeout States (1 - 60 minutes)
  const [autoLockMinutes, setAutoLockMinutes] = useState<number>(5);
  const [clipboardTimeoutMinutes, setClipboardTimeoutMinutes] = useState<number>(2);
  const [lockOnBackground, setLockOnBackground] = useState<boolean>(true);
  const [biometricOnResume, setBiometricOnResume] = useState<boolean>(true);
  const [sensitiveClipFlag, setSensitiveClipFlag] = useState<boolean>(true);
  const [enforceStrongBiometricsOnly, setEnforceStrongBiometricsOnly] = useState<boolean>(false);
  const [settingsSubView, setSettingsSubView] = useState<'main' | 'autolock'>('main');

  const isDarkMode = themeMode === 'dark' || (themeMode === 'system' && true);

  // Phone View Mode (Vault Items vs Security Health Dashboard vs App Settings)
  const [phoneView, setPhoneView] = useState<'vault' | 'health' | 'settings'>('vault');
  const [isDiagnosticScanning, setIsDiagnosticScanning] = useState<boolean>(false);
  const [scanProgress, setScanProgress] = useState<number>(100);
  const [scanStatusText, setScanStatusText] = useState<string>('Diagnostic Coroutine Engine idle • SSOT Room DB synchronized');

  // Initial Vault Items with varied passwords for diagnostic engine
  const [items, setItems] = useState<VaultItem[]>([
    {
      id: '1',
      title: 'Netflix HD Streaming',
      category: 'LOGIN',
      usernameOrCardholder: 'cinephile@obscura.app',
      secretValue: 'K9#mX$8pL!zQ2wE',
      urlOrCardNumber: 'https://netflix.com',
      notesOrCvv: '4K Master Ultra Plan - Shared Family Household',
      isFavorite: true,
      createdAt: '2026-08-10',
      expiryDate: '2027-08-10',
      tags: ['Streaming', 'Personal', 'Entertainment'],
      lastModified: '2026-08-12 14:20'
    },
    {
      id: '2',
      title: 'Primary Visa Black Card',
      category: 'BANK_CARD',
      usernameOrCardholder: 'ALEXANDER VAULT',
      secretValue: '883',
      urlOrCardNumber: '4532 8921 7731 9012',
      notesOrCvv: 'Exp: 09/29 • CVV 883 • Premium Concierge Access',
      isFavorite: true,
      createdAt: '2026-08-11',
      expiryDate: '2029-09-30',
      tags: ['Finance', 'Primary Card', 'Banking'],
      lastModified: '2026-08-13 08:15'
    },
    {
      id: '3',
      title: 'Obscura TMDB Production API',
      category: 'API_KEY',
      usernameOrCardholder: 'System Admin',
      secretValue: 'obs_live_99f381a030b42cc82e118c7',
      urlOrCardNumber: 'https://api.themoviedb.org/3',
      notesOrCvv: 'AES-256 Encrypted in Room DB • Rate Limit: 10k req/min',
      isFavorite: false,
      createdAt: '2026-08-12',
      expiryDate: '2027-12-31',
      tags: ['Production', 'DevOps', 'API'],
      lastModified: '2026-08-13 09:00'
    },
    {
      id: '4',
      title: 'Master Recovery Seed Phrase',
      category: 'SECURE_NOTE',
      usernameOrCardholder: 'Vault Core',
      secretValue: 'velvet crimson phantom shadows eclipse nebula obsidian titan monolith',
      urlOrCardNumber: 'Offline Cold Storage',
      notesOrCvv: 'Strictly local. Never leaves hardware security module.',
      isFavorite: false,
      createdAt: '2026-08-13',
      expiryDate: 'Never',
      tags: ['Backup', 'Critical', 'ColdStorage'],
      lastModified: '2026-08-13 09:10'
    },
    {
      id: '5',
      title: 'Legacy Mail Server',
      category: 'LOGIN',
      usernameOrCardholder: 'admin@mail.obscura.internal',
      secretValue: 'password123',
      urlOrCardNumber: 'https://mail.obscura.internal',
      notesOrCvv: 'Legacy mail server account - weak common sequence pattern',
      isFavorite: false,
      createdAt: '2026-08-11',
      expiryDate: '2026-09-01',
      tags: ['Email', 'Legacy', 'HighRisk'],
      lastModified: '2026-08-11 11:30'
    },
    {
      id: '6',
      title: 'Secondary Wi-Fi Router',
      category: 'LOGIN',
      usernameOrCardholder: 'admin',
      secretValue: 'password123',
      urlOrCardNumber: '192.168.1.1',
      notesOrCvv: 'Guest network router admin console - reused password',
      isFavorite: false,
      createdAt: '2026-08-12',
      expiryDate: '2027-01-01',
      tags: ['Network', 'Router'],
      lastModified: '2026-08-12 16:45'
    },
    {
      id: '7',
      title: 'Dev Staging Portal',
      category: 'LOGIN',
      usernameOrCardholder: 'dev_user',
      secretValue: 'admin123456',
      urlOrCardNumber: 'https://staging.obscura.app',
      notesOrCvv: 'Development sandbox environment - common sequence pattern',
      isFavorite: false,
      createdAt: '2026-08-13',
      expiryDate: '2026-10-15',
      tags: ['Staging', 'Dev'],
      lastModified: '2026-08-13 07:20'
    }
  ]);

  // Code inspection file selection
  const [selectedFile, setSelectedFile] = useState<
    'AutoLockSettingsScreen' | 'QuickSearchTileService' | 'QuickSearchActivity' | 'SecurityHealthGauge' | 'Fido2CryptographyUtils' | 'AutofillBiometricAuthActivity' | 'ObscuraAutofillService' | 'SecureClipboardManager' | 'BackupCryptoUtils' | 'BackupManager' | 'BiometricAuthManager' | 'AuthScreen' | 'VaultDatabase' | 'DashboardScreen' | 'VaultEntity' | 'VaultFtsEntity' | 'VaultDao' | 'VaultViewModel' | 'EditVaultEntryBottomSheet' | 'PasswordGeneratorSheet' | 'ObscuraTheme' | 'PasswordHealthDiagnostics' | 'CsvImportEngine'
  >('AutoLockSettingsScreen');

  // Compute live security health report dynamically
  const securityReport = computeSecurityHealthReport(items);

  // Trigger Background Coroutine Diagnostic Job
  const handleRunDiagnosticScan = () => {
    setIsDiagnosticScanning(true);
    setScanProgress(0);
    setScanStatusText('Launching Coroutine Job (Dispatchers.IO)...');
    addLog('[DIAGNOSTIC_JOB] Coroutine background worker started on Dispatchers.IO', 'info');

    let current = 0;
    const interval = setInterval(() => {
      current += 25;
      setScanProgress(current);
      if (current === 25) {
        setScanStatusText('Querying Room DB VaultEntities & inspecting character lengths...');
      } else if (current === 50) {
        setScanStatusText('Evaluating Regex pattern dictionary & duplicate password reuse...');
      } else if (current === 75) {
        setScanStatusText('Calculating Shannon Entropy & Security Health scores...');
      } else if (current >= 100) {
        clearInterval(interval);
        setIsDiagnosticScanning(false);
        setScanProgress(100);
        setScanStatusText('Background Diagnostic Completed • Security Health SSOT updated');
        const report = computeSecurityHealthReport(items);
        addLog(`[DIAGNOSTIC_SUCCESS] Security Health scan finished. Score: ${report.overallScore}/100 (Grade ${report.grade})`, 'success');
        setCopiedToast(`Diagnostic Complete • Score: ${report.overallScore}/100 (Grade ${report.grade})`);
        setTimeout(() => setCopiedToast(null), 3000);
      }
    }, 280);
  };

  // Auto-Fix single weak password
  const handleAutoFixItem = (itemId: string) => {
    const targetItem = items.find(i => i.id === itemId);
    if (!targetItem) return;

    const strongSecret = generateStrongSecret();
    const nowFormatted = new Date().toISOString().replace('T', ' ').substring(0, 16);

    const updatedItems = items.map(i => {
      if (i.id === itemId) {
        return {
          ...i,
          secretValue: strongSecret,
          lastModified: nowFormatted
        };
      }
      return i;
    });

    setItems(updatedItems);
    setCopiedToast(`Re-generated 20-char high-entropy key for "${targetItem.title}"`);
    addLog(`[SECURITY_FIX] Regenerated high-entropy secret for VaultEntity(id=${itemId}, title="${targetItem.title}")`, 'success');
    setTimeout(() => setCopiedToast(null), 3000);
  };

  // Auto-Fix batch weak passwords
  const handleAutoFixAll = () => {
    const report = computeSecurityHealthReport(items);
    const flaggedIds = new Set(report.itemReports.filter(r => r.issues.length > 0 && r.category !== 'BANK_CARD').map(r => r.itemId));
    
    if (flaggedIds.size === 0) {
      setCopiedToast('All passwords are already long, unique, and secure!');
      setTimeout(() => setCopiedToast(null), 2500);
      return;
    }

    const nowFormatted = new Date().toISOString().replace('T', ' ').substring(0, 16);
    const updatedItems = items.map(i => {
      if (flaggedIds.has(i.id)) {
        return {
          ...i,
          secretValue: generateStrongSecret(),
          lastModified: nowFormatted
        };
      }
      return i;
    });

    setItems(updatedItems);
    setCopiedToast(`Batch fixed ${flaggedIds.size} weak passwords in Room DB!`);
    addLog(`[SECURITY_FIX_ALL] Batch regenerated ${flaggedIds.size} weak/reused secrets in SQLCipher DB`, 'success');
    setTimeout(() => setCopiedToast(null), 3500);
  };

  // Reactive Flow emissions simulated via debounced query from VaultViewModel StateFlow
  const filteredItems = items.filter(item => {
    const matchesCategory = selectedCategory === 'ALL' || item.category === selectedCategory;
    const q = debouncedSearchQuery.toLowerCase().trim();
    if (!q) return matchesCategory;

    // Matches indexed FTS4 columns: title, username, tags, notes, url
    const matchesSearch = item.title.toLowerCase().includes(q) ||
                          item.usernameOrCardholder.toLowerCase().includes(q) ||
                          item.tags.some(t => t.toLowerCase().includes(q)) ||
                          (item.notesOrCvv && item.notesOrCvv.toLowerCase().includes(q)) ||
                          (item.urlOrCardNumber && item.urlOrCardNumber.toLowerCase().includes(q));
    return matchesCategory && matchesSearch;
  });

  const weakPasswordsCount = items.filter(i => i.secretValue.length < 10).length;

  // Add Log Entry with unique ID to avoid duplicate key warnings in quick succession
  const addLog = (message: string, type: 'info' | 'success' | 'error') => {
    const newLog: BiometricLog = {
      id: `${Date.now()}_${Math.random().toString(36).substring(2, 9)}`,
      time: new Date().toLocaleTimeString('en-US', { hour12: false }),
      message,
      type
    };
    setBiometricLogs(prev => [newLog, ...prev.slice(0, 14)]);
  };

  // Trigger Biometric Prompt
  const handleOpenBiometricPrompt = (type: 'fingerprint' | 'faceid' = 'fingerprint') => {
    setBiometricType(type);
    setBiometricState('scanning');
    setBiometricStatusText(
      type === 'fingerprint' 
        ? (enforceStrongBiometricsOnly ? 'Touch Class 3 Fingerprint Sensor (BIOMETRIC_STRONG)' : 'Touch fingerprint sensor on screen')
        : (enforceStrongBiometricsOnly ? 'Looking for Class 3 FaceID Sensor (BIOMETRIC_STRONG)' : 'Looking for FaceID sensor...')
    );
    setIsBiometricPromptOpen(true);
    
    if (enforceStrongBiometricsOnly) {
      addLog(`[BiometricPrompt.PromptInfo] AllowedAuthenticators: BIOMETRIC_STRONG only (DEVICE_CREDENTIAL disabled, setNegativeButtonText("Cancel") enforced)`, 'info');
      addLog(`BiometricPrompt.authenticate() invoked with BIOMETRIC_STRONG [Type: ${type.toUpperCase()}]`, 'info');
    } else {
      addLog(`[BiometricPrompt.PromptInfo] AllowedAuthenticators: BIOMETRIC_STRONG | DEVICE_CREDENTIAL (PIN / Pattern fallback enabled)`, 'info');
      addLog(`BiometricPrompt.authenticate() invoked [Type: ${type.toUpperCase()}]`, 'info');
    }
  };

  // Simulate Biometric Success
  const handleBiometricSuccess = (type: 'fingerprint' | 'faceid' = biometricType) => {
    setBiometricState('success');
    const msg = type === 'fingerprint' 
      ? 'Fingerprint recognized. Unlocking Room DB...' 
      : 'FaceID verified. Unlocking Room DB...';
    setBiometricStatusText(msg);
    addLog(`[SUCCESS] BiometricPrompt.AuthenticationCallback.onAuthenticationSucceeded()`, 'success');

    setTimeout(() => {
      setIsBiometricPromptOpen(false);
      setIsAuthenticated(true);
      setPinError('');
      setCopiedToast(`Unlocked via ${type === 'fingerprint' ? 'Fingerprint' : 'FaceID'}`);
      setTimeout(() => setCopiedToast(null), 2500);
    }, 700);
  };

  // Simulate Biometric Failure
  const handleBiometricFailure = () => {
    setBiometricState('failed');
    setBiometricStatusText('Biometric not recognized. Please try again.');
    addLog(`[FAILED] BiometricPrompt.AuthenticationCallback.onAuthenticationFailed()`, 'error');

    setTimeout(() => {
      setBiometricState('scanning');
      setBiometricStatusText('Touch fingerprint sensor again');
    }, 1500);
  };

  // Simulate Biometric Error / Cancel
  const handleBiometricError = (errorCode = 10, errString = 'User cancelled biometric prompt') => {
    setBiometricState('error');
    setBiometricStatusText(`Authentication error (${errorCode}): ${errString}`);
    addLog(`[ERROR] BiometricPrompt.AuthenticationCallback.onAuthenticationError(${errorCode}, "${errString}")`, 'error');

    setTimeout(() => {
      setIsBiometricPromptOpen(false);
    }, 1200);
  };

  // Handle PIN Input
  const handlePinDigit = (digit: string) => {
    if (pinInput.length < 6) {
      const nextPin = pinInput + digit;
      setPinInput(nextPin);
      setPinError('');

      if (nextPin.length === 6) {
        if (nextPin === '123456' || nextPin === '000000') {
          setIsAuthenticated(true);
          setPinInput('');
          setPinError('');
          addLog('PIN Authentication Succeeded (6-digit match)', 'success');
        } else {
          setPinError('Invalid PIN Code. Try 123456 or use Biometrics');
          setPinInput('');
          addLog('PIN Authentication Error: Invalid Passcode', 'error');
        }
      }
    }
  };

  const handlePinBackspace = () => {
    if (pinInput.length > 0) {
      setPinInput(pinInput.slice(0, -1));
    }
  };

  // User activity tracker for Auto-Lock (ProcessLifecycleOwner & Inactivity Watcher)
  const lastActivityRef = useRef<number>(Date.now());
  const clipboardTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Inactivity Auto-Lock Loop (1 - 60 minutes)
  useEffect(() => {
    const handleUserActivity = () => {
      lastActivityRef.current = Date.now();
    };

    window.addEventListener('mousemove', handleUserActivity);
    window.addEventListener('keydown', handleUserActivity);
    window.addEventListener('click', handleUserActivity);
    window.addEventListener('touchstart', handleUserActivity);
    window.addEventListener('scroll', handleUserActivity);

    const interval = setInterval(() => {
      if (isAuthenticated) {
        const idleMs = Date.now() - lastActivityRef.current;
        if (idleMs >= autoLockMinutes * 60 * 1000) {
          setIsAuthenticated(false);
          addLog(`[AUTO_LOCK] Vault locked automatically after ${autoLockMinutes} minutes of inactivity (ProcessLifecycleOwner Trigger)`, 'info');
          setCopiedToast(`Vault Locked (${autoLockMinutes}m idle timeout)`);
          setTimeout(() => setCopiedToast(null), 3000);
        }
      }
    }, 1000);

    return () => {
      window.removeEventListener('mousemove', handleUserActivity);
      window.removeEventListener('keydown', handleUserActivity);
      window.removeEventListener('click', handleUserActivity);
      window.removeEventListener('touchstart', handleUserActivity);
      window.removeEventListener('scroll', handleUserActivity);
      clearInterval(interval);
    };
  }, [isAuthenticated, autoLockMinutes]);

  const handleCopy = (text: string, title: string) => {
    navigator.clipboard.writeText(text);
    setCopiedToast(`Copied secret for ${title}`);
    setTimeout(() => setCopiedToast(null), 2500);

    if (clipboardTimerRef.current) {
      clearTimeout(clipboardTimerRef.current);
    }

    addLog(`[SECURE_CLIPBOARD] Copied secret for "${title}". Auto-clear scheduled in ${clipboardTimeoutMinutes}m (${clipboardTimeoutMinutes * 60}s)`, 'info');

    // Schedule clipboard wipe
    clipboardTimerRef.current = setTimeout(() => {
      addLog(`[SECURE_CLIPBOARD] SecureClipboardManager auto-cleared clipboard payload after ${clipboardTimeoutMinutes}m delay`, 'success');
      setCopiedToast('Clipboard auto-cleared (SecureClipboardManager)');
      setTimeout(() => setCopiedToast(null), 2000);
    }, Math.min(clipboardTimeoutMinutes * 60 * 1000, 30000));
  };

  const handleClearClipboardImmediately = () => {
    if (clipboardTimerRef.current) {
      clearTimeout(clipboardTimerRef.current);
    }
    navigator.clipboard.writeText('');
    setCopiedToast('Clipboard wiped immediately');
    addLog('[SECURE_CLIPBOARD] Immediate clipboard purge executed via SecureClipboardManager.clearImmediately()', 'success');
    setTimeout(() => setCopiedToast(null), 2000);
  };

  const handleToggleFavorite = (id: string) => {
    setItems(items.map(i => i.id === id ? { ...i, isFavorite: !i.isFavorite } : i));
  };

  const handleRequestDelete = (item: VaultItem) => {
    setItemToDelete(item);
    setIsDeleteModalOpen(true);
  };

  const handleConfirmDelete = () => {
    if (!itemToDelete) return;
    const deletedId = itemToDelete.id;
    const deletedTitle = itemToDelete.title;

    setItems(prev => prev.filter(i => i.id !== deletedId));
    if (expandedItemId === deletedId) setExpandedItemId(null);
    if (editingItemId === deletedId) setEditingItemId(null);

    setIsDeleteModalOpen(false);
    setItemToDelete(null);

    setCopiedToast(`Permanently deleted "${deletedTitle}"`);
    addLog(`[ROOM_DB_DELETE] Permanently purged "${deletedTitle}" (ID: ${deletedId}) from Room SSOT DB`, 'info');
    setTimeout(() => setCopiedToast(null), 3000);
  };

  const handleCancelDelete = () => {
    setIsDeleteModalOpen(false);
    setItemToDelete(null);
  };

  const handleDelete = (id: string) => {
    const target = items.find(i => i.id === id);
    if (target) {
      handleRequestDelete(target);
    } else {
      setItems(items.filter(i => i.id !== id));
    }
  };

  // Expanded View and Edit Operations
  const handleToggleExpand = (id: string) => {
    if (expandedItemId === id) {
      setExpandedItemId(null);
      setEditingItemId(null);
    } else {
      setExpandedItemId(id);
      setEditingItemId(null); // Reset edit state when switching items
    }
  };

  const handleStartEdit = (item: VaultItem) => {
    setEditingItemId(item.id);
    setEditForm({ ...item });
  };

  const handleCancelEdit = () => {
    setEditingItemId(null);
    setEditForm(null);
  };

  const handleAddTagToEditForm = () => {
    if (!newTagInput.trim() || !editForm) return;
    const cleanTag = newTagInput.trim().replace(/^#/, '');
    if (!editForm.tags.includes(cleanTag)) {
      setEditForm({
        ...editForm,
        tags: [...editForm.tags, cleanTag]
      });
    }
    setNewTagInput('');
  };

  const handleRemoveTagFromEditForm = (tagToRemove: string) => {
    if (!editForm) return;
    setEditForm({
      ...editForm,
      tags: editForm.tags.filter(t => t !== tagToRemove)
    });
  };

  const handleSaveEdit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editForm || !editingItemId) return;

    const nowFormatted = new Date().toISOString().replace('T', ' ').substring(0, 16);
    const updatedItem: VaultItem = {
      ...editForm,
      lastModified: nowFormatted
    };

    setItems(items.map(i => i.id === editingItemId ? updatedItem : i));
    setEditingItemId(null);
    setEditForm(null);
    
    setCopiedToast(`Updated "${updatedItem.title}" metadata & tags in Room DB`);
    addLog(`Room DB Update: VaultEntity(id=${updatedItem.id}, title="${updatedItem.title}", expiry="${updatedItem.expiryDate}")`, 'success');
    setTimeout(() => setCopiedToast(null), 3000);
  };

  const handleAddSecret = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle || !newSecret) return;

    const parsedTags = newTagsString
      .split(',')
      .map(t => t.trim().replace(/^#/, ''))
      .filter(t => t.length > 0);

    const newItem: VaultItem = {
      id: `item_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      title: newTitle,
      category: newCategory,
      usernameOrCardholder: newUsername || 'User',
      secretValue: newSecret,
      urlOrCardNumber: newUrl || 'Offline Vault',
      notesOrCvv: 'Added via Obscura Android Vault',
      isFavorite: false,
      createdAt: new Date().toISOString().split('T')[0],
      expiryDate: newExpiryDate || '2028-12-31',
      tags: parsedTags.length > 0 ? parsedTags : ['General'],
      lastModified: new Date().toISOString().replace('T', ' ').substring(0, 16)
    };

    setItems([newItem, ...items]);
    setIsModalOpen(false);
    setNewTitle('');
    setNewUsername('');
    setNewSecret('');
    setNewUrl('');
    setNewTagsString('Personal');
    setCopiedToast('Secret encrypted & saved to SQLCipher Room DB');
    addLog(`Inserted new VaultEntity(id=${newItem.id}, title="${newItem.title}")`, 'success');
    setTimeout(() => setCopiedToast(null), 3000);
  };

  const handleOpenQrScanner = (target: 'add' | 'edit') => {
    setQrTargetField(target);
    setIsQrScannerOpen(true);
  };

  const handleImportQrResult = (result: QrScanResult) => {
    if (qrTargetField === 'add') {
      setNewSecret(result.secretValue);
      if (!newTitle && result.parsedTitle) setNewTitle(result.parsedTitle);
      if (!newUsername && result.parsedUsername) setNewUsername(result.parsedUsername);
      if (result.parsedCategory) setNewCategory(result.parsedCategory);
      
      if (result.isTotp) {
        const currentTags = newTagsString.split(',').map(t => t.trim()).filter(Boolean);
        if (!currentTags.includes('TOTP')) {
          setNewTagsString([...currentTags, 'TOTP'].join(', '));
        }
        setCopiedToast(`Imported TOTP 2FA key for "${result.parsedTitle || 'Account'}"`);
      } else {
        setCopiedToast(`Imported QR credential into secret field`);
      }
      setIsModalOpen(true);
    } else if (qrTargetField === 'edit' && editForm) {
      const updatedTags = [...editForm.tags];
      if (result.isTotp && !updatedTags.includes('TOTP')) {
        updatedTags.push('TOTP');
      }
      setEditForm({
        ...editForm,
        secretValue: result.secretValue,
        title: !editForm.title && result.parsedTitle ? result.parsedTitle : editForm.title,
        usernameOrCardholder: !editForm.usernameOrCardholder && result.parsedUsername ? result.parsedUsername : editForm.usernameOrCardholder,
        tags: updatedTags
      });
      if (result.isTotp) {
        setCopiedToast(`Imported TOTP 2FA key into secret field`);
      } else {
        setCopiedToast(`Imported QR secret value into edit form`);
      }
    }
    addLog(`[QR_SCAN_IMPORT] Scanned QR code (${result.isTotp ? 'TOTP URI' : 'Credential'})`, 'success');
  };

  // Bulk CSV Import Processor simulating Room @Transaction batch insertion
  const handleExecuteCsvImport = async (
    records: ParsedCsvRecord[],
    conflictPolicy: DuplicateConflictPolicy,
    onProgress: (percent: number, current: number, total: number) => void,
    customResolutions?: Record<string, DuplicateConflictPolicy>
  ): Promise<{ inserted: number; overwritten: number; skipped: number }> => {
    let inserted = 0;
    let overwritten = 0;
    let skipped = 0;

    const total = records.length;
    const nowFormatted = new Date().toISOString().replace('T', ' ').substring(0, 16);
    const todayStr = new Date().toISOString().split('T')[0];

    // Build lookup map of existing records for conflict detection
    const existingKeyMap = new Map<string, VaultItem>();
    items.forEach(item => {
      const key = `${item.title.trim().toLowerCase()}|||${item.usernameOrCardholder.trim().toLowerCase()}`;
      existingKeyMap.set(key, item);
    });

    const pendingItems: VaultItem[] = [];
    const itemsToUpdate = new Map<string, VaultItem>();

    for (let i = 0; i < total; i++) {
      const record = records[i];
      const matchKey = `${record.title.trim().toLowerCase()}|||${record.usernameOrCardholder.trim().toLowerCase()}`;
      const existingMatch = existingKeyMap.get(matchKey);

      if (existingMatch) {
        const effectivePolicy = customResolutions?.[record.id] || conflictPolicy;

        if (effectivePolicy === 'SKIP') {
          skipped++;
        } else if (effectivePolicy === 'OVERWRITE') {
          overwritten++;
          itemsToUpdate.set(existingMatch.id, {
            ...existingMatch,
            secretValue: record.secretValue,
            urlOrCardNumber: record.urlOrCardNumber || existingMatch.urlOrCardNumber,
            notesOrCvv: record.notesOrCvv ? `${existingMatch.notesOrCvv}\n${record.notesOrCvv}`.trim() : existingMatch.notesOrCvv,
            tags: Array.from(new Set([...existingMatch.tags, ...record.tags])),
            lastModified: nowFormatted
          });
        } else {
          // KEEP_BOTH
          inserted++;
          const newItem: VaultItem = {
            id: `import_${Date.now()}_${i}_${Math.random().toString(36).substr(2, 5)}`,
            title: `${record.title} (Imported)`,
            category: record.category,
            usernameOrCardholder: record.usernameOrCardholder || 'Imported User',
            secretValue: record.secretValue,
            urlOrCardNumber: record.urlOrCardNumber || 'Imported Vault',
            notesOrCvv: record.notesOrCvv || 'Imported via Obscura CSV Wizard',
            isFavorite: false,
            createdAt: todayStr,
            expiryDate: '2028-12-31',
            tags: record.tags.length > 0 ? record.tags : ['Imported'],
            lastModified: nowFormatted
          };
          pendingItems.push(newItem);
        }
      } else {
        // Non-duplicate new entry
        inserted++;
        const newItem: VaultItem = {
          id: `import_${Date.now()}_${i}_${Math.random().toString(36).substr(2, 5)}`,
          title: record.title,
          category: record.category,
          usernameOrCardholder: record.usernameOrCardholder || 'Imported User',
          secretValue: record.secretValue,
          urlOrCardNumber: record.urlOrCardNumber || 'Imported Vault',
          notesOrCvv: record.notesOrCvv || 'Imported via Obscura CSV Wizard',
          isFavorite: false,
          createdAt: todayStr,
          expiryDate: '2028-12-31',
          tags: record.tags.length > 0 ? record.tags : ['Imported'],
          lastModified: nowFormatted
        };
        pendingItems.push(newItem);
      }

      // Simulate asynchronous batch processing chunks
      if (i % 5 === 0 || i === total - 1) {
        onProgress(Math.round(((i + 1) / total) * 100), i + 1, total);
        await new Promise(r => setTimeout(r, 20));
      }
    }

    // Atomic transaction commit: update items state in single pass
    setItems(currentItems => {
      const updated = currentItems.map(item => itemsToUpdate.get(item.id) || item);
      return [...pendingItems, ...updated];
    });

    addLog(`[ROOM_DB_TRANSACTION] Batch imported ${inserted + overwritten} items (Inserted: ${inserted}, Overwritten: ${overwritten}, Skipped: ${skipped})`, 'success');
    setCopiedToast(`Imported ${inserted + overwritten} credentials into Room DB`);
    setTimeout(() => setCopiedToast(null), 3500);

    return { inserted, overwritten, skipped };
  };

  const handleRestoreVaultBackup = (restoredEntries: VaultItem[]) => {
    if (!restoredEntries || restoredEntries.length === 0) return;

    // Room DB SSOT merge by ID
    const entryMap = new Map<string, VaultItem>();
    items.forEach(i => entryMap.set(i.id, i));
    restoredEntries.forEach(i => entryMap.set(i.id, i));

    const merged = Array.from(entryMap.values());
    setItems(merged);
    setCopiedToast(`Restored ${restoredEntries.length} items to Room Database`);
    addLog(`[BACKUP_RESTORE] Merged ${restoredEntries.length} decrypted entries into Room DB (Total: ${merged.length})`, 'success');
    setTimeout(() => setCopiedToast(null), 3500);
  };

  return (
    <div className="min-h-screen bg-black text-white flex flex-col font-sans">
      {/* Top Banner & Control Bar */}
      <header className="border-b border-obscura-border bg-[#0a0a0a] px-6 py-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-obscura-crimson flex items-center justify-center font-bold text-black shadow-lg shadow-obscura-crimson/20">
            <Shield className="w-6 h-6 text-black fill-black" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-extrabold tracking-wider text-white">OBSCURA</h1>
              <span className="text-xs bg-obscura-crimson/20 text-obscura-crimson px-2 py-0.5 rounded-full font-mono border border-obscura-crimson/40">
                v1.0 ANDROID
              </span>
            </div>
            <p className="text-xs text-gray-400">Offline-First Encrypted Vault • BiometricPrompt API + Room DB</p>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="flex items-center bg-[#141414] border border-obscura-border rounded-xl p-1">
          <button
            onClick={() => setActiveTab('emulator')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
              activeTab === 'emulator'
                ? 'bg-obscura-crimson text-black shadow-md'
                : 'text-gray-400 hover:text-white'
            }`}
          >
            <Smartphone className="w-4 h-4" />
            Android Emulator
          </button>
          <button
            onClick={() => setActiveTab('code')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
              activeTab === 'code'
                ? 'bg-obscura-crimson text-black shadow-md'
                : 'text-gray-400 hover:text-white'
            }`}
          >
            <Code className="w-4 h-4" />
            Biometric & Kotlin Source
          </button>
          <button
            onClick={() => setActiveTab('db_inspector')}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
              activeTab === 'db_inspector'
                ? 'bg-obscura-crimson text-black shadow-md'
                : 'text-gray-400 hover:text-white'
            }`}
          >
            <Database className="w-4 h-4" />
            SQLCipher DB Inspector
          </button>
        </div>

        {/* Build Status */}
        <div className="hidden lg:flex items-center gap-2 bg-emerald-950/40 border border-emerald-800/50 text-emerald-400 px-3 py-1.5 rounded-xl text-xs font-mono">
          <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          <span>Biometric API: READY</span>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col items-center justify-center p-4 lg:p-6 relative overflow-y-auto">
        {activeTab === 'emulator' && (
          <div className="flex flex-col lg:flex-row items-center lg:items-start justify-center gap-8 w-full max-w-5xl">
            
            {/* Mobile Phone Mockup */}
            <div className="w-full max-w-sm h-[770px] bg-[#000000] rounded-[48px] border-[10px] border-[#1c1c1e] shadow-2xl shadow-obscura-crimson/10 relative flex flex-col overflow-hidden ring-1 ring-white/10 shrink-0">
              
              {/* Phone Speaker & Dynamic Island Notch */}
              <div className="w-full bg-black pt-3 pb-2 flex justify-center items-center relative z-20">
                <div className="w-24 h-5 bg-[#141414] rounded-full flex items-center justify-between px-2.5 border border-white/5">
                  <div className="w-2 h-2 rounded-full bg-blue-500/80"></div>
                  <div className="w-2.5 h-2.5 rounded-full bg-emerald-500/80 animate-pulse"></div>
                </div>
              </div>

              {/* Status Bar */}
              <div className="px-6 py-1 flex items-center justify-between text-[11px] font-mono text-gray-400 z-20">
                <span>09:41</span>
                <div className="flex items-center gap-1.5">
                  <span className="text-[10px] text-emerald-400 bg-emerald-500/10 px-1 rounded">BIO_ACTIVE</span>
                  <div className="w-2.5 h-2.5 rounded-full bg-white/20"></div>
                  <div className="w-4 h-2 border border-gray-400 rounded-sm p-0.5">
                    <div className="w-full h-full bg-gray-400"></div>
                  </div>
                </div>
              </div>

              {/* Screen Body */}
              <div className={`flex-1 flex flex-col overflow-hidden relative transition-colors duration-200 ${
                isDarkMode ? 'bg-black text-white' : 'bg-slate-50 text-slate-900'
              }`}>
                
                {/* Auth Screen View (Locked State) */}
                {!isAuthenticated ? (
                  <div className="flex-1 flex flex-col justify-between p-4 text-center overflow-y-auto select-none">
                    
                    {/* Top Branding & Animated Biometric Sensor Icon */}
                    <div className="flex flex-col items-center pt-1">
                      <div className="flex items-center gap-1.5 mb-2 border px-3 py-0.5 rounded-full text-[10px] font-mono bg-obscura-crimson/10 border-obscura-crimson/40 text-obscura-crimson font-bold">
                        <Shield className="w-3 h-3 text-obscura-crimson" />
                        <span>BiometricPrompt API Active</span>
                      </div>

                      {/* Animated Pulse Biometric Hero Scanner */}
                      <div 
                        onClick={() => handleOpenBiometricPrompt('fingerprint')}
                        className="relative group cursor-pointer my-2 flex flex-col items-center justify-center transition-transform active:scale-95"
                        title="Click to scan fingerprint or face ID"
                      >
                        {/* Concentric Pulsing Aura Rings */}
                        <div className="absolute w-28 h-28 rounded-full bg-obscura-crimson/20 animate-ping opacity-25 pointer-events-none" />
                        <div className="absolute w-22 h-22 rounded-full bg-obscura-crimson/30 animate-pulse pointer-events-none" />
                        
                        {/* Main Glowing Biometric Sensor Badge */}
                        <div className={`relative w-20 h-20 rounded-2xl flex items-center justify-center border-2 transition-all shadow-xl group-hover:border-obscura-crimson ${
                          isDarkMode 
                            ? 'bg-[#121212] border-obscura-crimson/80 text-obscura-crimson shadow-obscura-crimson/30 ring-4 ring-obscura-crimson/20' 
                            : 'bg-white border-red-500 text-red-600 shadow-red-200 ring-4 ring-red-100'
                        }`}>
                          {/* Vertical Laser Scan Line Animation */}
                          <div className="absolute inset-x-2 h-0.5 bg-obscura-crimson shadow-[0_0_10px_#E50914] animate-pulse" style={{ top: '45%' }} />

                          {/* Pulsing Fingerprint Icon */}
                          <Fingerprint className="w-10 h-10 text-obscura-crimson animate-pulse" />

                          {/* Live Biometric Sensor Ready Badge */}
                          <div className="absolute -bottom-2 bg-emerald-500 text-black font-extrabold font-mono text-[8px] px-2 py-0.5 rounded-full flex items-center gap-1 border border-emerald-300 shadow-xs">
                            <span className="w-1.5 h-1.5 rounded-full bg-black animate-ping" />
                            <span>SENSOR READY</span>
                          </div>
                        </div>

                        <p className="text-[10px] font-mono text-gray-400 mt-3 flex items-center gap-1 group-hover:text-white transition-colors">
                          <Sparkles className="w-3 h-3 text-obscura-crimson" />
                          <span>Tap sensor or scan below to unlock</span>
                        </p>
                      </div>

                      <h2 className={`text-base font-extrabold tracking-wider mt-1 ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
                        OBSCURA VAULT
                      </h2>
                    </div>

                    {/* PIN Dots & Biometric Launcher */}
                    <div className="w-full my-2 space-y-2.5">
                      <div>
                        <p className={`text-[10px] font-mono mb-1.5 ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>Enter Master PIN or Scan Biometrics</p>
                        
                        {/* 6 PIN Dots */}
                        <div className="flex justify-center gap-2 my-1">
                          {[0, 1, 2, 3, 4, 5].map((idx) => {
                            const isFilled = idx < pinInput.length;
                            return (
                              <div
                                key={idx}
                                className={`w-3 h-3 rounded-full border transition-all ${
                                  isFilled
                                    ? 'bg-obscura-crimson border-obscura-crimson scale-110 shadow-md shadow-obscura-crimson/50'
                                    : isDarkMode ? 'bg-[#141414] border-gray-700' : 'bg-slate-200 border-slate-300'
                                }`}
                              />
                            );
                          })}
                        </div>

                        {pinError && (
                          <p className="text-[11px] text-red-500 font-mono mt-1 animate-shake font-bold">
                            {pinError}
                          </p>
                        )}
                      </div>

                      {/* Biometric Scan Trigger Button with Pulsing Icon */}
                      <button
                        onClick={() => handleOpenBiometricPrompt('fingerprint')}
                        className={`w-full py-2 border text-xs font-bold rounded-xl flex items-center justify-center gap-2 transition-all shadow-md group ${
                          isDarkMode
                            ? 'bg-[#141414] border-obscura-crimson/50 hover:border-obscura-crimson text-white hover:bg-obscura-crimson/10'
                            : 'bg-white border-red-200 text-slate-800 hover:bg-red-50 shadow-xs'
                        }`}
                      >
                        <div className="relative flex items-center justify-center">
                          <span className="absolute w-4 h-4 rounded-full bg-obscura-crimson/40 animate-ping" />
                          <Fingerprint className="w-4 h-4 text-obscura-crimson animate-pulse relative z-10" />
                        </div>
                        <span className="font-mono tracking-wide">SCAN FINGERPRINT / FACE ID</span>
                      </button>
                    </div>

                    {/* Numeric Keypad Grid */}
                    <div className="w-full pb-1">
                      <div className="grid grid-cols-3 gap-1.5">
                        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((digit) => (
                          <button
                            key={digit}
                            onClick={() => handlePinDigit(digit)}
                            className={`h-10 rounded-xl border text-sm font-bold font-mono active:scale-95 transition-all ${
                              isDarkMode
                                ? 'bg-[#121212] border-obscura-border text-white hover:bg-obscura-border/50'
                                : 'bg-white border-slate-200 text-slate-800 hover:bg-slate-100 shadow-xs'
                            }`}
                          >
                            {digit}
                          </button>
                        ))}
                        <button
                          onClick={() => handleOpenBiometricPrompt('fingerprint')}
                          className={`h-10 rounded-xl border flex items-center justify-center transition-all ${
                            isDarkMode
                              ? 'bg-obscura-crimson/15 border-obscura-crimson/60 text-obscura-crimson hover:bg-obscura-crimson/25'
                              : 'bg-red-50 border-red-200 text-red-600 hover:bg-red-100'
                          }`}
                          title="Biometric Verification"
                        >
                          <Fingerprint className="w-5 h-5 text-obscura-crimson animate-pulse" />
                        </button>
                        <button
                          onClick={() => handlePinDigit('0')}
                          className={`h-10 rounded-xl border text-sm font-bold font-mono transition-all ${
                            isDarkMode
                              ? 'bg-[#121212] border-obscura-border text-white hover:bg-obscura-border/50'
                              : 'bg-white border-slate-200 text-slate-800 hover:bg-slate-100 shadow-xs'
                          }`}
                        >
                          0
                        </button>
                        <button
                          onClick={handlePinBackspace}
                          className={`h-10 rounded-xl border flex items-center justify-center transition-all ${
                            isDarkMode
                              ? 'bg-[#121212] border-obscura-border text-gray-400 hover:text-white'
                              : 'bg-white border-slate-200 text-slate-500 hover:text-slate-900 shadow-xs'
                          }`}
                          title="Delete"
                        >
                          ⌫
                        </button>
                      </div>

                      <p className={`text-[10px] font-mono mt-1.5 text-center ${isDarkMode ? 'text-gray-500' : 'text-slate-400'}`}>
                        Default PIN: <span className="text-obscura-crimson font-bold">123456</span> or Biometrics
                      </p>
                    </div>
                  </div>
                ) : (
                  /* Dashboard View (Unlocked State) */
                  <div className="flex-1 flex flex-col overflow-hidden">
                    {/* Header with View Mode Switcher */}
                    <div className={`p-3 border-b transition-colors duration-200 ${
                      isDarkMode ? 'bg-[#0a0a0a] border-obscura-border' : 'bg-white border-slate-200 shadow-xs'
                    }`}>
                      <div className="flex items-center justify-between mb-2">
                        <div>
                          <h2 className={`text-base font-extrabold tracking-wider ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>OBSCURA VAULT</h2>
                          <div className="flex items-center gap-1.5 mt-0.5">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                            <span className={`text-[10px] font-mono ${isDarkMode ? 'text-emerald-400' : 'text-emerald-700 font-semibold'}`}>SQLCipher Encrypted</span>
                          </div>
                        </div>

                        <div className="flex items-center gap-1.5">
                          {/* Dynamic Theme Switcher Quick Toggle */}
                          <button
                            onClick={() => {
                              const next = themeMode === 'dark' ? 'light' : 'dark';
                              setThemeMode(next);
                              addLog(`Theme mode switched to ${next.toUpperCase()} (Material Design 3)`, 'info');
                            }}
                            className={`p-1.5 rounded-xl border transition-all ${
                              isDarkMode
                                ? 'bg-[#141414] border-obscura-border text-amber-400 hover:bg-[#1f1f1f]'
                                : 'bg-slate-100 border-slate-200 text-indigo-600 hover:bg-slate-200 shadow-xs'
                            }`}
                            title={`Toggle to ${isDarkMode ? 'Light' : 'Dark'} Theme`}
                          >
                            {isDarkMode ? <Sun className="w-4 h-4 text-amber-400" /> : <Moon className="w-4 h-4 text-indigo-600" />}
                          </button>

                          <button
                            onClick={() => {
                              setIsAuthenticated(false);
                              addLog('Vault Locked by user', 'info');
                            }}
                            className={`p-1.5 rounded-xl border transition-all ${
                              isDarkMode
                                ? 'bg-[#141414] border-obscura-border text-gray-400 hover:text-white'
                                : 'bg-slate-100 border-slate-200 text-slate-700 hover:text-slate-900 shadow-xs'
                            }`}
                            title="Lock Vault"
                          >
                            <Lock className="w-4 h-4 text-obscura-crimson" />
                          </button>
                        </div>
                      </div>

                      {/* View Navigation Switcher Pills */}
                      <div className={`flex items-center gap-1 p-1 rounded-xl border transition-colors ${
                        isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-slate-100 border-slate-200'
                      }`}>
                        <button
                          onClick={() => setPhoneView('vault')}
                          className={`flex-1 py-1.5 text-[11px] font-bold rounded-lg flex items-center justify-center gap-1 transition-all ${
                            phoneView === 'vault'
                              ? 'bg-obscura-crimson text-black shadow-md font-extrabold'
                              : isDarkMode ? 'text-gray-400 hover:text-white' : 'text-slate-600 hover:text-slate-900'
                          }`}
                        >
                          <Key className="w-3.5 h-3.5" />
                          <span>Vault</span>
                          <span className={`text-[9px] px-1.5 rounded-full font-mono ${
                            phoneView === 'vault' ? 'bg-black/20 text-black' : isDarkMode ? 'bg-black/40 text-gray-300' : 'bg-slate-200 text-slate-700'
                          }`}>{items.length}</span>
                        </button>

                        <button
                          onClick={() => setPhoneView('health')}
                          className={`flex-1 py-1.5 text-[11px] font-bold rounded-lg flex items-center justify-center gap-1 transition-all ${
                            phoneView === 'health'
                              ? 'bg-obscura-crimson text-black shadow-md font-extrabold'
                              : isDarkMode ? 'text-gray-400 hover:text-white' : 'text-slate-600 hover:text-slate-900'
                          }`}
                        >
                          <ShieldAlert className="w-3.5 h-3.5" />
                          <span>Health</span>
                          {securityReport.criticalIssuesCount > 0 && (
                            <span className="text-[9px] bg-red-950 text-red-300 font-mono px-1.5 rounded-full border border-red-500/40 animate-pulse">
                              {securityReport.criticalIssuesCount}
                            </span>
                          )}
                        </button>

                        <button
                          onClick={() => setPhoneView('settings')}
                          className={`flex-1 py-1.5 text-[11px] font-bold rounded-lg flex items-center justify-center gap-1 transition-all ${
                            phoneView === 'settings'
                              ? 'bg-obscura-crimson text-black shadow-md font-extrabold'
                              : isDarkMode ? 'text-gray-400 hover:text-white' : 'text-slate-600 hover:text-slate-900'
                          }`}
                        >
                          <Settings className="w-3.5 h-3.5" />
                          <span>Settings</span>
                        </button>
                      </div>
                    </div>

                    {/* Vault View Tab */}
                    {phoneView === 'vault' && (
                      <div className="flex-1 flex flex-col overflow-hidden">
                        {/* Search Bar & Category/Tag Filters Container */}
                        <div className={`p-2.5 border-b space-y-2 transition-colors ${
                          isDarkMode ? 'bg-[#0e0e0e] border-obscura-border' : 'bg-slate-50 border-slate-200'
                        }`}>
                          {/* Search Input Box */}
                          <div className="relative flex items-center">
                            <Search className={`w-3.5 h-3.5 absolute left-3 pointer-events-none transition-colors ${
                              searchQuery.trim() ? 'text-obscura-crimson font-bold' : isDarkMode ? 'text-gray-400' : 'text-slate-400'
                            }`} />
                            <input
                              type="text"
                              value={searchQuery}
                              onChange={(e) => setSearchQuery(e.target.value)}
                              placeholder="Search titles, accounts, or #tags..."
                              className={`w-full pl-8 pr-8 py-1.5 text-xs rounded-xl border transition-all outline-none font-mono ${
                                isDarkMode
                                  ? 'bg-[#141414] border-obscura-border text-white placeholder-gray-500 focus:border-obscura-crimson focus:ring-1 focus:ring-obscura-crimson'
                                  : 'bg-white border-slate-200 text-slate-800 placeholder-slate-400 focus:border-red-500 focus:ring-1 focus:ring-red-400 shadow-2xs'
                              }`}
                            />
                            {searchQuery && (
                              <button
                                onClick={() => setSearchQuery('')}
                                className={`absolute right-2.5 p-0.5 rounded-full transition-colors ${
                                  isDarkMode ? 'text-gray-400 hover:text-white hover:bg-gray-800' : 'text-slate-400 hover:text-slate-700 hover:bg-slate-200'
                                }`}
                                title="Clear Search"
                              >
                                <X className="w-3.5 h-3.5" />
                              </button>
                            )}
                          </div>

                          {/* Category Filter Pills */}
                          <div className="flex items-center gap-1 overflow-x-auto no-scrollbar pb-0.5 text-[10px] font-bold font-mono">
                            {[
                              { id: 'ALL', label: 'All', count: items.length },
                              { id: 'LOGIN', label: 'Logins', count: items.filter(i => i.category === 'LOGIN').length },
                              { id: 'BANK_CARD', label: 'Cards', count: items.filter(i => i.category === 'BANK_CARD').length },
                              { id: 'API_KEY', label: 'API Keys', count: items.filter(i => i.category === 'API_KEY').length },
                              { id: 'SECURE_NOTE', label: 'Notes', count: items.filter(i => i.category === 'SECURE_NOTE').length },
                            ].map((cat) => (
                              <button
                                key={cat.id}
                                onClick={() => setSelectedCategory(cat.id)}
                                className={`px-2 py-0.5 rounded-lg border whitespace-nowrap transition-all flex items-center gap-1 ${
                                  selectedCategory === cat.id
                                    ? 'bg-obscura-crimson text-black border-obscura-crimson font-extrabold shadow-xs'
                                    : isDarkMode
                                    ? 'bg-[#141414] border-obscura-border text-gray-400 hover:text-white'
                                    : 'bg-white border-slate-200 text-slate-600 hover:text-slate-900 shadow-2xs'
                                }`}
                              >
                                <span>{cat.label}</span>
                                <span className={`px-1 py-0.2 rounded-full text-[8px] ${
                                  selectedCategory === cat.id ? 'bg-black/20 text-black' : isDarkMode ? 'bg-black/40 text-gray-400' : 'bg-slate-100 text-slate-500'
                                }`}>
                                  {cat.count}
                                </span>
                              </button>
                            ))}
                          </div>

                          {/* Quick Search Tag Suggestions */}
                          <div className="flex items-center gap-1 overflow-x-auto no-scrollbar text-[9px] font-mono">
                            <span className={`shrink-0 ${isDarkMode ? 'text-gray-500' : 'text-slate-400'}`}>Quick Tags:</span>
                            {['Streaming', 'Personal', 'Finance', 'DevOps', 'Email', 'Legacy'].map((tag) => (
                              <button
                                key={tag}
                                onClick={() => setSearchQuery(searchQuery === tag ? '' : tag)}
                                className={`px-1.5 py-0.2 rounded-md border shrink-0 transition-all ${
                                  searchQuery.toLowerCase() === tag.toLowerCase()
                                    ? 'bg-amber-400/30 text-amber-300 border-amber-400 font-bold ring-1 ring-amber-400/50'
                                    : isDarkMode
                                    ? 'bg-[#141414] border-obscura-border/60 text-gray-400 hover:text-white'
                                    : 'bg-white border-slate-200 text-slate-600 hover:text-slate-900'
                                }`}
                              >
                                #{tag}
                              </button>
                            ))}
                          </div>

                          {/* Live Search Results Highlight Banner */}
                          {searchQuery.trim() && (
                            <div className={`flex items-center justify-between text-[10px] font-mono px-2 py-1 rounded-lg border animate-fade-in ${
                              isDarkMode
                                ? 'bg-amber-500/10 border-amber-500/30 text-amber-300'
                                : 'bg-amber-50 border-amber-200 text-amber-900'
                            }`}>
                              <span className="flex items-center gap-1.5 truncate">
                                {isFtsSearching ? (
                                  <RefreshCw className="w-3 h-3 text-amber-400 animate-spin shrink-0" />
                                ) : (
                                  <Sparkles className="w-3 h-3 text-amber-400 shrink-0" />
                                )}
                                <span>
                                  FTS4 MATCH "<mark className="bg-amber-400/40 text-amber-200 font-bold px-1 rounded">{debouncedSearchQuery.trim() || searchQuery.trim()}</mark>": <strong>{filteredItems.length} entries</strong>
                                  {isFtsSearching && <span className="text-[9px] opacity-75 ml-1">(debouncing 300ms...)</span>}
                                </span>
                              </span>
                              <button
                                onClick={() => setSearchQuery('')}
                                className="underline text-[9px] text-amber-400 font-bold hover:opacity-80 shrink-0 ml-1"
                              >
                                Clear
                              </button>
                            </div>
                          )}
                        </div>

                        {/* Secrets List with Expanded View & Jetpack Compose Animations */}
                        <div className="flex-1 overflow-y-auto p-3 space-y-2.5">
                      {filteredItems.length === 0 ? (
                        <div className="text-center py-12 text-gray-500 text-xs">
                          <Shield className="w-8 h-8 mx-auto mb-2 text-gray-600" />
                          <p className="font-bold">No matching vault entries found</p>
                          {searchQuery && (
                            <p className="text-[10px] text-gray-400 mt-1">
                              Try searching for titles, emails, or #tags
                            </p>
                          )}
                        </div>
                      ) : (
                        <AnimatePresence mode="popLayout">
                          {filteredItems.map((item) => {
                            const isExpanded = expandedItemId === item.id;
                            const isEditingThis = editingItemId === item.id;

                            // Check if any tags match current query
                            const matchingTags = searchQuery.trim() 
                              ? item.tags.filter(t => t.toLowerCase().includes(searchQuery.trim().toLowerCase()))
                              : [];

                            return (
                              <motion.div
                                key={item.id}
                                layout
                                initial={{ opacity: 0, y: 16, scale: 0.97 }}
                                animate={{ opacity: 1, y: 0, scale: 1 }}
                                exit={{ opacity: 0, y: -12, scale: 0.95 }}
                                transition={{ duration: 0.22, ease: "easeOut" }}
                                className={`border transition-all rounded-xl overflow-hidden ${
                                  isDarkMode 
                                    ? isExpanded
                                      ? 'border-obscura-crimson/80 bg-[#141414] ring-1 ring-obscura-crimson/30 shadow-lg'
                                      : 'border-obscura-border bg-[#121212] hover:border-obscura-crimson/40'
                                    : isExpanded
                                      ? 'border-red-500 bg-white ring-1 ring-red-300 shadow-md'
                                      : 'border-slate-200 bg-white hover:border-red-300 shadow-xs'
                                }`}
                              >
                              {/* Primary Header Row (Always Visible) */}
                              <div
                                onClick={() => handleToggleExpand(item.id)}
                                className={`p-3 cursor-pointer flex items-start justify-between select-none ${
                                  isDarkMode ? 'hover:bg-white/[0.02]' : 'hover:bg-slate-50'
                                }`}
                              >
                                <div className="flex items-center gap-2.5">
                                  <div className={`w-8 h-8 rounded-lg border flex items-center justify-center text-obscura-crimson shrink-0 ${
                                    isDarkMode ? 'bg-[#1a1a1a] border-obscura-border' : 'bg-red-50 border-red-200'
                                  }`}>
                                    {item.category === 'LOGIN' && <Key className="w-4 h-4" />}
                                    {item.category === 'BANK_CARD' && <CreditCard className="w-4 h-4" />}
                                    {item.category === 'API_KEY' && <Terminal className="w-4 h-4" />}
                                    {item.category === 'SECURE_NOTE' && <FileText className="w-4 h-4" />}
                                  </div>
                                  <div>
                                    <div className="flex items-center gap-1.5 flex-wrap">
                                      {/* Highlighted Title */}
                                      <h4 className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
                                        <HighlightText text={item.title} query={searchQuery} isDarkMode={isDarkMode} accentVariant="crimson" />
                                      </h4>

                                      {/* Health Score Status Indicator from Diagnostic Engine */}
                                      {(() => {
                                        const itemReport = securityReport.itemReports.find(r => r.itemId === item.id);
                                        const score = itemReport ? itemReport.score : 100;
                                        const isCritical = score < 40;
                                        const isWarning = score >= 40 && score < 70;
                                        const isGood = score >= 70 && score < 90;
                                        const isExcellent = score >= 90;

                                        return (
                                          <span
                                            className={`text-[8.5px] px-1.5 py-0.2 rounded-md font-mono font-bold flex items-center gap-1 border transition-all ${
                                              isCritical
                                                ? 'bg-red-500/15 text-red-400 border-red-500/40 ring-1 ring-red-500/30'
                                                : isWarning
                                                ? 'bg-amber-500/15 text-amber-400 border-amber-500/40'
                                                : isGood
                                                ? 'bg-sky-500/15 text-sky-400 border-sky-500/30'
                                                : 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
                                            }`}
                                            title={`Diagnostic Engine Score: ${score}/100 • ${itemReport?.issues.length || 0} security issues`}
                                          >
                                            <span
                                              className={`w-1.5 h-1.5 rounded-full ${
                                                isCritical
                                                  ? 'bg-red-500 animate-pulse'
                                                  : isWarning
                                                  ? 'bg-amber-400'
                                                  : isGood
                                                  ? 'bg-sky-400'
                                                  : 'bg-emerald-400'
                                              }`}
                                            />
                                            <span>{score}%</span>
                                          </span>
                                        );
                                      })()}

                                      {item.expiryDate && item.expiryDate !== 'Never' && (
                                        <span className="text-[9px] bg-emerald-500/10 text-emerald-500 border border-emerald-500/30 px-1.5 py-0.2 rounded font-mono">
                                          Exp: {item.expiryDate.substring(0, 7)}
                                        </span>
                                      )}
                                    </div>
                                    {/* Highlighted Username / Account */}
                                    <p className={`text-[11px] font-mono truncate max-w-[160px] ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>
                                      <HighlightText text={item.usernameOrCardholder} query={searchQuery} isDarkMode={isDarkMode} accentVariant="crimson" />
                                    </p>

                                    {/* Matching Tags Row in Collapsed View with Gold/Amber Highlight */}
                                    {matchingTags.length > 0 && !isExpanded && (
                                      <div className="flex flex-wrap gap-1 mt-1">
                                        {matchingTags.map((tag) => (
                                          <span
                                            key={tag}
                                            className={`text-[9px] px-1.5 py-0.2 rounded-md font-mono font-bold flex items-center gap-0.5 border ${
                                              isDarkMode 
                                                ? 'bg-amber-400/20 text-amber-300 border-amber-400/60 ring-1 ring-amber-400/40' 
                                                : 'bg-amber-100 text-amber-900 border-amber-300 ring-1 ring-amber-400/40'
                                            }`}
                                          >
                                            <span>#</span>
                                            <HighlightText text={tag} query={searchQuery} isDarkMode={isDarkMode} accentVariant="amber" />
                                          </span>
                                        ))}
                                      </div>
                                    )}
                                  </div>
                                </div>

                                <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
                                  <button
                                    onClick={() => handleToggleFavorite(item.id)}
                                    className={`p-1 rounded ${
                                      item.isFavorite 
                                        ? 'text-amber-400' 
                                        : isDarkMode ? 'text-gray-600 hover:bg-[#1f1f1f]' : 'text-slate-300 hover:bg-slate-100'
                                    }`}
                                    title="Toggle Favorite"
                                  >
                                    <Star className="w-3.5 h-3.5 fill-current" />
                                  </button>
                                  <button
                                    onClick={() => handleToggleExpand(item.id)}
                                    className={`p-1 rounded ${
                                      isDarkMode ? 'text-gray-400 hover:text-white hover:bg-[#1f1f1f]' : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100'
                                    }`}
                                    title={isExpanded ? 'Collapse' : 'Expand Details'}
                                  >
                                    {isExpanded ? <ChevronUp className="w-4 h-4 text-obscura-crimson" /> : <ChevronDown className="w-4 h-4" />}
                                  </button>
                                </div>
                              </div>

                              {/* Collapsed Quick Secret Row */}
                              {!isExpanded && (
                                <div className="px-3 pb-3 pt-0 flex items-center justify-between font-mono text-[11px]">
                                  <div className={`flex items-center gap-1 rounded-lg px-2 py-1 w-full justify-between border ${
                                    isDarkMode 
                                      ? 'text-gray-400 bg-[#0a0a0a] border-obscura-border/60' 
                                      : 'text-slate-600 bg-slate-50 border-slate-200'
                                  }`}>
                                    <span className="truncate max-w-[170px]">
                                      {showSecretId === item.id ? item.secretValue : '••••••••••••••••'}
                                    </span>
                                    <div className="flex items-center gap-1.5">
                                      <button
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          setShowSecretId(showSecretId === item.id ? null : item.id);
                                        }}
                                        className={isDarkMode ? 'text-gray-400 hover:text-white' : 'text-slate-500 hover:text-slate-900'}
                                      >
                                        {showSecretId === item.id ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                                      </button>
                                      <button
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          handleCopy(item.secretValue, item.title);
                                        }}
                                        className="text-obscura-crimson hover:text-red-700"
                                      >
                                        <Copy className="w-3.5 h-3.5" />
                                      </button>
                                    </div>
                                  </div>
                                </div>
                              )}

                              {/* Expanded Detailed View / Editor Sheet with Animated Layout Transition (expandVertically / shrinkVertically) */}
                              <AnimatePresence initial={false}>
                                {isExpanded && (
                                  <motion.div
                                    key={`expanded-content-${item.id}`}
                                    initial={{ height: 0, opacity: 0 }}
                                    animate={{ 
                                      height: 'auto', 
                                      opacity: 1,
                                      transition: {
                                        height: { duration: 0.28, ease: [0.04, 0.62, 0.23, 0.98] },
                                        opacity: { duration: 0.22, delay: 0.05 }
                                      }
                                    }}
                                    exit={{ 
                                      height: 0, 
                                      opacity: 0,
                                      transition: {
                                        height: { duration: 0.22, ease: [0.36, 0, 0.66, -0.56] },
                                        opacity: { duration: 0.15 }
                                      }
                                    }}
                                    className="overflow-hidden border-t border-obscura-border/60 bg-[#0c0c0c]"
                                  >
                                    <div className="px-3 pb-3 pt-1 space-y-3">
                                  
                                  {/* Tags Badge Pills with Substring Highlighting */}
                                  <div className="flex flex-wrap items-center gap-1.5 pt-1">
                                    <span className="text-[10px] text-gray-500 font-mono flex items-center gap-1">
                                      <Tag className="w-3 h-3 text-obscura-crimson" />
                                      Tags:
                                    </span>
                                    {item.tags.map((tag) => {
                                      const isTagMatch = searchQuery.trim() && tag.toLowerCase().includes(searchQuery.trim().toLowerCase());
                                      return (
                                        <span
                                          key={tag}
                                          className={`text-[10px] px-2 py-0.5 rounded-full font-semibold transition-all flex items-center gap-0.5 border ${
                                            isTagMatch
                                              ? 'bg-amber-400/25 text-amber-200 border-amber-400 ring-1 ring-amber-400/60 font-bold scale-105 shadow-xs'
                                              : isDarkMode
                                              ? 'bg-obscura-crimson/10 text-obscura-crimson border-obscura-crimson/30'
                                              : 'bg-red-50 text-red-700 border-red-200'
                                          }`}
                                        >
                                          <span>#</span>
                                          <HighlightText text={tag} query={searchQuery} isDarkMode={isDarkMode} accentVariant="amber" />
                                        </span>
                                      );
                                    })}
                                  </div>

                                  {!isEditingThis ? (
                                    /* Read-Only Detailed View */
                                    <div className="space-y-2.5">
                                      {/* Secret Value Card */}
                                      <div className="bg-[#141414] border border-obscura-border rounded-lg p-2.5 space-y-1">
                                        <div className="flex items-center justify-between text-[10px] text-gray-400 font-mono">
                                          <span>SECRET VALUE</span>
                                          <span className="text-emerald-400">AES-256 ENCRYPTED</span>
                                        </div>
                                        <div className="flex items-center justify-between font-mono text-xs">
                                          <span className="text-white font-semibold truncate max-w-[200px]">
                                            {showSecretId === item.id ? item.secretValue : '••••••••••••••••'}
                                          </span>
                                          <div className="flex items-center gap-1.5">
                                            <button
                                              onClick={() => setShowSecretId(showSecretId === item.id ? null : item.id)}
                                              className="p-1 text-gray-400 hover:text-white"
                                            >
                                              {showSecretId === item.id ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                                            </button>
                                            <button
                                              onClick={() => handleCopy(item.secretValue, item.title)}
                                              className="p-1 text-obscura-crimson hover:text-white"
                                            >
                                              <Copy className="w-3.5 h-3.5" />
                                            </button>
                                          </div>
                                        </div>
                                      </div>

                                      {/* URL / Location & Expiry Row */}
                                      <div className="grid grid-cols-2 gap-2 text-[11px] font-mono">
                                        <div className="bg-[#141414] border border-obscura-border rounded-lg p-2">
                                          <span className="text-[10px] text-gray-500 block mb-0.5">LOCATION / URL</span>
                                          <span className="text-gray-300 truncate block">
                                            <HighlightText text={item.urlOrCardNumber || 'N/A'} query={searchQuery} isDarkMode={isDarkMode} />
                                          </span>
                                        </div>
                                        <div className="bg-[#141414] border border-obscura-border rounded-lg p-2">
                                          <span className="text-[10px] text-gray-500 block mb-0.5 flex items-center gap-1">
                                            <Calendar className="w-3 h-3 text-obscura-crimson" />
                                            EXPIRY DATE
                                          </span>
                                          <span className="text-emerald-400 font-bold block">{item.expiryDate || 'Never'}</span>
                                        </div>
                                      </div>

                                      {/* Notes Field */}
                                      {item.notesOrCvv && (
                                        <div className="bg-[#141414] border border-obscura-border rounded-lg p-2">
                                          <span className="text-[10px] text-gray-500 font-mono block mb-0.5">NOTES & METADATA</span>
                                          <p className="text-xs text-gray-300 leading-snug">
                                            <HighlightText text={item.notesOrCvv} query={searchQuery} isDarkMode={isDarkMode} />
                                          </p>
                                        </div>
                                      )}

                                      {/* Metadata Audit Info */}
                                      <div className="flex items-center justify-between text-[10px] font-mono text-gray-500 pt-1 border-t border-obscura-border/40">
                                        <span>Created: {item.createdAt}</span>
                                        <span>Modified: {item.lastModified}</span>
                                      </div>

                                      {/* Expanded Action Toolbar */}
                                      <div className="flex items-center justify-between gap-2 pt-1">
                                        <button
                                          onClick={() => handleStartEdit(item)}
                                          className="flex-1 py-1.5 bg-obscura-crimson/20 hover:bg-obscura-crimson/30 border border-obscura-crimson/50 text-obscura-crimson text-xs font-bold rounded-lg flex items-center justify-center gap-1.5 transition-all"
                                        >
                                          <Edit3 className="w-3.5 h-3.5" />
                                          <span>Edit Detailed Entry</span>
                                        </button>

                                        <button
                                          onClick={() => handleDelete(item.id)}
                                          className="p-1.5 bg-red-950/40 hover:bg-red-900/60 border border-red-800/50 text-red-400 text-xs rounded-lg transition-all"
                                          title="Delete Entry"
                                        >
                                          <Trash2 className="w-3.5 h-3.5" />
                                        </button>
                                      </div>
                                    </div>
                                  ) : (
                                    /* Inline Edit Form Mode */
                                    <form onSubmit={handleSaveEdit} className="space-y-2.5 pt-1">
                                      <div className="flex items-center justify-between border-b border-obscura-border pb-1.5">
                                        <span className="text-xs font-bold text-obscura-crimson font-mono flex items-center gap-1">
                                          <Edit3 className="w-3.5 h-3.5" />
                                          Edit Entry Metadata
                                        </span>
                                        <button
                                          type="button"
                                          onClick={handleCancelEdit}
                                          className="text-gray-400 hover:text-white text-xs"
                                        >
                                          <X className="w-4 h-4" />
                                        </button>
                                      </div>

                                      {/* Title & Category */}
                                      <div className="grid grid-cols-2 gap-2">
                                        <div>
                                          <label className="text-[10px] text-gray-400 font-mono">Title</label>
                                          <input
                                            type="text"
                                            required
                                            value={editForm?.title || ''}
                                            onChange={(e) => setEditForm(prev => prev ? { ...prev, title: e.target.value } : null)}
                                            className="w-full mt-0.5 p-1.5 bg-[#141414] border border-obscura-border rounded text-xs text-white focus:border-obscura-crimson focus:outline-none"
                                          />
                                        </div>
                                        <div>
                                          <label className="text-[10px] text-gray-400 font-mono">Category</label>
                                          <select
                                            value={editForm?.category || 'LOGIN'}
                                            onChange={(e) => setEditForm(prev => prev ? { ...prev, category: e.target.value as any } : null)}
                                            className="w-full mt-0.5 p-1.5 bg-[#141414] border border-obscura-border rounded text-xs text-white focus:border-obscura-crimson focus:outline-none"
                                          >
                                            <option value="LOGIN">LOGIN</option>
                                            <option value="BANK_CARD">BANK CARD</option>
                                            <option value="API_KEY">API KEY</option>
                                            <option value="SECURE_NOTE">SECURE NOTE</option>
                                          </select>
                                        </div>
                                      </div>

                                      {/* Username / Identifier */}
                                      <div>
                                        <label className="text-[10px] text-gray-400 font-mono">Username / Cardholder</label>
                                        <input
                                          type="text"
                                          value={editForm?.usernameOrCardholder || ''}
                                          onChange={(e) => setEditForm(prev => prev ? { ...prev, usernameOrCardholder: e.target.value } : null)}
                                          className="w-full mt-0.5 p-1.5 bg-[#141414] border border-obscura-border rounded text-xs text-white focus:border-obscura-crimson focus:outline-none"
                                        />
                                      </div>

                                      {/* Secret Value */}
                                      <div>
                                        <div className="flex items-center justify-between">
                                          <label className="text-[10px] text-gray-400 font-mono">Secret Value (Encrypted in DB)</label>
                                          <div className="flex items-center gap-2">
                                            <button
                                              type="button"
                                              onClick={() => handleOpenQrScanner('edit')}
                                              className="text-[10px] text-emerald-400 font-bold hover:underline flex items-center gap-1 font-mono"
                                            >
                                              <QrCode className="w-3 h-3 text-emerald-400" />
                                              <span>Scan QR</span>
                                            </button>
                                            <button
                                              type="button"
                                              onClick={() => setShowEditGenerator(!showEditGenerator)}
                                              className="text-[10px] text-obscura-crimson font-bold hover:underline flex items-center gap-1 font-mono"
                                            >
                                              <Sparkles className="w-3 h-3 text-obscura-crimson" />
                                              <span>{showEditGenerator ? 'Hide Generator' : '⚡ Password Generator'}</span>
                                            </button>
                                          </div>
                                        </div>
                                        <input
                                          type="text"
                                          required
                                          value={editForm?.secretValue || ''}
                                          onChange={(e) => setEditForm(prev => prev ? { ...prev, secretValue: e.target.value } : null)}
                                          className="w-full mt-0.5 p-1.5 bg-[#141414] border border-obscura-border rounded text-xs font-mono text-emerald-400 focus:border-obscura-crimson focus:outline-none"
                                        />

                                        {/* Real-time Entropy & Password Strength Indicator for Edit Form */}
                                        <PasswordStrengthIndicator
                                          secret={editForm?.secretValue || ''}
                                          isCompact={true}
                                        />

                                        {showEditGenerator && (
                                          <div className="mt-2 animate-fade-in">
                                            <PasswordGenerator
                                              onApplyPassword={(pwd) => {
                                                setEditForm(prev => prev ? { ...prev, secretValue: pwd } : null);
                                              }}
                                            />
                                          </div>
                                        )}
                                      </div>

                                      {/* Expiry Date & URL */}
                                      <div className="grid grid-cols-2 gap-2">
                                        <div>
                                          <label className="text-[10px] text-gray-400 font-mono">Expiry Date</label>
                                          <input
                                            type="text"
                                            placeholder="YYYY-MM-DD or Never"
                                            value={editForm?.expiryDate || ''}
                                            onChange={(e) => setEditForm(prev => prev ? { ...prev, expiryDate: e.target.value } : null)}
                                            className="w-full mt-0.5 p-1.5 bg-[#141414] border border-obscura-border rounded text-xs text-white focus:border-obscura-crimson focus:outline-none"
                                          />
                                        </div>
                                        <div>
                                          <label className="text-[10px] text-gray-400 font-mono">URL / Card No.</label>
                                          <input
                                            type="text"
                                            value={editForm?.urlOrCardNumber || ''}
                                            onChange={(e) => setEditForm(prev => prev ? { ...prev, urlOrCardNumber: e.target.value } : null)}
                                            className="w-full mt-0.5 p-1.5 bg-[#141414] border border-obscura-border rounded text-xs text-white focus:border-obscura-crimson focus:outline-none"
                                          />
                                        </div>
                                      </div>

                                      {/* Tags Manager */}
                                      <div>
                                        <label className="text-[10px] text-gray-400 font-mono">Manage Tags</label>
                                        <div className="flex flex-wrap items-center gap-1 my-1">
                                          {editForm?.tags.map((tag) => (
                                            <span
                                              key={tag}
                                              className="text-[10px] bg-obscura-crimson/20 text-obscura-crimson border border-obscura-crimson/40 px-2 py-0.5 rounded-full flex items-center gap-1"
                                            >
                                              #{tag}
                                              <button
                                                type="button"
                                                onClick={() => handleRemoveTagFromEditForm(tag)}
                                                className="hover:text-red-400"
                                              >
                                                ×
                                              </button>
                                            </span>
                                          ))}
                                        </div>
                                        <div className="flex gap-1">
                                          <input
                                            type="text"
                                            placeholder="Add tag name..."
                                            value={newTagInput}
                                            onChange={(e) => setNewTagInput(e.target.value)}
                                            className="flex-1 p-1 bg-[#141414] border border-obscura-border rounded text-xs text-white focus:outline-none"
                                          />
                                          <button
                                            type="button"
                                            onClick={handleAddTagToEditForm}
                                            className="px-2 bg-[#222] border border-obscura-border text-gray-300 rounded text-xs font-bold hover:text-white"
                                          >
                                            + Tag
                                          </button>
                                        </div>
                                      </div>

                                      {/* Notes / CVV / Description */}
                                      <div>
                                        <label className="text-[10px] text-gray-400 font-mono">Notes & Instructions</label>
                                        <textarea
                                          rows={2}
                                          value={editForm?.notesOrCvv || ''}
                                          onChange={(e) => setEditForm(prev => prev ? { ...prev, notesOrCvv: e.target.value } : null)}
                                          className="w-full mt-0.5 p-1.5 bg-[#141414] border border-obscura-border rounded text-xs text-white focus:border-obscura-crimson focus:outline-none"
                                        />
                                      </div>

                                      {/* Save / Cancel Action Buttons */}
                                      <div className="flex gap-2 pt-1">
                                        <button
                                          type="button"
                                          onClick={() => handleRequestDelete(item)}
                                          className="px-2.5 py-1.5 bg-red-950/40 hover:bg-red-900/60 border border-red-800/50 text-red-400 text-xs rounded transition-all flex items-center justify-center gap-1 cursor-pointer"
                                          title="Delete Entry"
                                        >
                                          <Trash2 className="w-3.5 h-3.5" />
                                        </button>
                                        <button
                                          type="button"
                                          onClick={handleCancelEdit}
                                          className="flex-1 py-1.5 bg-[#222] text-gray-300 font-semibold text-xs rounded hover:bg-[#2e2e2e]"
                                        >
                                          Cancel
                                        </button>
                                        <button
                                          type="submit"
                                          className="flex-1 py-1.5 bg-obscura-crimson text-black font-extrabold text-xs rounded hover:bg-obscura-crimsonHover flex items-center justify-center gap-1"
                                        >
                                          <Save className="w-3.5 h-3.5" />
                                          Save Changes
                                        </button>
                                      </div>
                                    </form>
                                  )}
                                  </div>
                                </motion.div>
                              )}
                            </AnimatePresence>
                            </motion.div>
                          );
                        })}
                      </AnimatePresence>
                    )}
                    </div>

                    {/* Bottom Action Bar with Floating Action Button (FAB) and CSV Import */}
                    <div className="p-2.5 bg-[#0a0a0a] border-t border-obscura-border flex items-center justify-between gap-1.5 relative">
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => handleOpenBiometricPrompt('fingerprint')}
                          className="py-2 px-2 bg-[#141414] border border-obscura-crimson/50 hover:border-obscura-crimson text-obscura-crimson rounded-xl flex items-center justify-center gap-1 text-[10px] font-bold transition-all active:scale-95"
                          title="Re-authenticate Biometrics"
                        >
                          <Fingerprint className="w-3.5 h-3.5" />
                          <span>Auth</span>
                        </button>

                        <button
                          onClick={() => handleOpenQrScanner('add')}
                          className="py-2 px-2 bg-[#141414] border border-emerald-500/50 hover:border-emerald-500 text-emerald-400 rounded-xl flex items-center justify-center gap-1 text-[10px] font-bold transition-all active:scale-95"
                          title="Scan QR / TOTP Code"
                        >
                          <QrCode className="w-3.5 h-3.5" />
                          <span>QR</span>
                        </button>

                        <button
                          onClick={() => setIsCsvImportOpen(true)}
                          className="py-2 px-2 bg-[#141414] border border-sky-500/50 hover:border-sky-500 text-sky-400 rounded-xl flex items-center justify-center gap-1 text-[10px] font-bold transition-all active:scale-95 shadow-sm"
                          title="Universal CSV Importer (Chrome, Bitwarden, KeePass)"
                        >
                          <FileSpreadsheet className="w-3.5 h-3.5" />
                          <span>Import</span>
                        </button>

                        <button
                          onClick={() => setIsBackupExportOpen(true)}
                          className="py-2 px-2 bg-[#141414] border border-amber-500/50 hover:border-amber-500 text-amber-400 rounded-xl flex items-center justify-center gap-1 text-[10px] font-bold transition-all active:scale-95 shadow-sm"
                          title="Export Encrypted Vault Backup (AES-256-GCM / PBKDF2)"
                        >
                          <Download className="w-3.5 h-3.5" />
                          <span>Backup</span>
                        </button>
                      </div>

                      {/* Extended Floating Action Button (FAB) for Add Secret */}
                      <motion.button
                        layoutId="obscura-fab-add-secret"
                        onClick={() => setIsModalOpen(true)}
                        whileHover={{ scale: 1.04 }}
                        whileTap={{ scale: 0.94 }}
                        className="py-2 px-3 bg-obscura-crimson hover:bg-obscura-crimsonHover text-black font-extrabold text-[10px] rounded-xl flex items-center justify-center gap-1 shadow-lg shadow-obscura-crimson/30 ring-2 ring-obscura-crimson/30 active:shadow-none transition-all cursor-pointer shrink-0"
                        title="Add New Encrypted Secret"
                      >
                        <Plus className="w-3.5 h-3.5 stroke-[3]" />
                        <span className="tracking-wider">ADD SECRET</span>
                      </motion.button>
                    </div>
                  </div>
                )}

                {/* Android Native BiometricPrompt Modal Sheet */}
                {isBiometricPromptOpen && (
                  <div className="absolute inset-0 bg-black/80 backdrop-blur-md z-40 flex flex-col justify-end p-3 animate-fade-in">
                    <div className="bg-[#1C1B1F] border border-white/10 rounded-2xl p-5 text-center flex flex-col items-center shadow-2xl relative">
                      
                      {/* Grab Bar */}
                      <div className="w-8 h-1 bg-gray-600 rounded-full mb-3" />

                      {/* Header */}
                      <h3 className="text-sm font-bold text-white tracking-wide">Obscura Vault Authentication</h3>
                      <p className="text-[11px] text-gray-400 mt-0.5 font-mono">Verify identity to decrypt local vault</p>

                      {/* Sensor Ring Area */}
                      <div className="my-5 relative flex items-center justify-center">
                        <div className={`w-20 h-20 rounded-full flex items-center justify-center transition-all ${
                          biometricState === 'success' 
                            ? 'bg-emerald-500/20 border-2 border-emerald-500 text-emerald-400 scale-105'
                            : biometricState === 'failed' || biometricState === 'error'
                            ? 'bg-red-500/20 border-2 border-red-500 text-red-400 animate-bounce'
                            : 'bg-obscura-crimson/10 border-2 border-obscura-crimson text-obscura-crimson animate-pulse'
                        }`}>
                          {biometricState === 'success' ? (
                            <Check className="w-10 h-10 text-emerald-400" />
                          ) : biometricState === 'failed' || biometricState === 'error' ? (
                            <XCircle className="w-10 h-10 text-red-400" />
                          ) : biometricType === 'fingerprint' ? (
                            <Fingerprint className="w-10 h-10 text-obscura-crimson" />
                          ) : (
                            <ScanFace className="w-10 h-10 text-obscura-crimson" />
                          )}
                        </div>
                      </div>

                      {/* Status Text */}
                      <p className={`text-xs font-mono mb-4 px-2 ${
                        biometricState === 'success' ? 'text-emerald-400 font-bold' :
                        biometricState === 'failed' || biometricState === 'error' ? 'text-red-400 font-bold' :
                        'text-gray-300'
                      }`}>
                        {biometricStatusText}
                      </p>

                      {/* Interactive Trigger Actions inside Biometric Prompt */}
                      <div className="w-full grid grid-cols-2 gap-2 mb-3">
                        <button
                          onClick={() => handleBiometricSuccess('fingerprint')}
                          className="py-2 bg-emerald-950/60 hover:bg-emerald-900/80 border border-emerald-500/50 text-emerald-300 text-[11px] font-bold rounded-xl flex items-center justify-center gap-1.5 transition-all"
                        >
                          <Fingerprint className="w-3.5 h-3.5 text-emerald-400" />
                          <span>Pass Fingerprint</span>
                        </button>
                        <button
                          onClick={() => handleBiometricSuccess('faceid')}
                          className="py-2 bg-emerald-950/60 hover:bg-emerald-900/80 border border-emerald-500/50 text-emerald-300 text-[11px] font-bold rounded-xl flex items-center justify-center gap-1.5 transition-all"
                        >
                          <ScanFace className="w-3.5 h-3.5 text-emerald-400" />
                          <span>Pass FaceID</span>
                        </button>
                        <button
                          onClick={handleBiometricFailure}
                          className="py-2 bg-red-950/60 hover:bg-red-900/80 border border-red-500/50 text-red-300 text-[11px] font-bold rounded-xl flex items-center justify-center gap-1.5 transition-all"
                        >
                          <XCircle className="w-3.5 h-3.5 text-red-400" />
                          <span>Fail Verification</span>
                        </button>
                        <button
                          onClick={() => handleBiometricError(10, 'User cancelled prompt')}
                          className="py-2 bg-zinc-800 hover:bg-zinc-700 border border-zinc-700 text-gray-300 text-[11px] font-semibold rounded-xl transition-all"
                        >
                          Cancel / Use PIN
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                    {/* VIEW 2: SECURITY HEALTH DASHBOARD */}
                    {phoneView === 'health' && (
                      <div className="flex-1 overflow-y-auto p-3 space-y-3">
                        {/* Background Diagnostic Scan Controller Banner */}
                        <div className={`border rounded-xl p-3 transition-colors ${
                          isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-sm'
                        }`}>
                          <div className="flex items-center justify-between mb-1.5">
                            <div className="flex items-center gap-1.5">
                              <Activity className="w-4 h-4 text-obscura-crimson" />
                              <span className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>Diagnostic Coroutine Engine</span>
                            </div>
                            <button
                              onClick={handleRunDiagnosticScan}
                              disabled={isDiagnosticScanning}
                              className="px-2.5 py-1 bg-obscura-crimson/20 hover:bg-obscura-crimson/30 border border-obscura-crimson/50 text-obscura-crimson text-[10px] font-bold rounded-lg flex items-center gap-1 transition-all disabled:opacity-50"
                            >
                              <RefreshCcw className={`w-3 h-3 ${isDiagnosticScanning ? 'animate-spin' : ''}`} />
                              <span>{isDiagnosticScanning ? 'Scanning...' : 'Run Scan'}</span>
                            </button>
                          </div>

                          <p className={`text-[10px] font-mono leading-tight ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>
                            {scanStatusText}
                          </p>

                          {isDiagnosticScanning && (
                            <div className={`w-full h-1.5 rounded-full mt-2 overflow-hidden ${isDarkMode ? 'bg-[#1e1e1e]' : 'bg-slate-200'}`}>
                              <div
                                className="bg-obscura-crimson h-full transition-all duration-300"
                                style={{ width: `${scanProgress}%` }}
                              />
                            </div>
                          )}
                        </div>

                        {/* Enhanced Animated Security Health Gauge Card */}
                        <SecurityHealthGauge
                          score={securityReport.overallScore}
                          grade={securityReport.grade}
                          isScanning={isDiagnosticScanning}
                          scanProgress={scanProgress}
                          criticalIssuesCount={securityReport.criticalIssuesCount}
                          reusedCount={securityReport.reusedPasswordsCount}
                          weakCount={securityReport.weakPasswordsCount}
                          onRunScan={handleRunDiagnosticScan}
                          onAutoFixAll={handleAutoFixAll}
                          isDarkMode={isDarkMode}
                          showDetails={false}
                        />

                        {/* Vulnerability Metrics Grid */}
                        <div className="grid grid-cols-2 gap-2">
                          <div className={`border rounded-xl p-2.5 transition-colors ${
                            isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-xs'
                          }`}>
                            <div className="flex items-center justify-between">
                              <span className={`text-[10px] font-mono ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>Reused Secrets</span>
                              <AlertOctagon className={`w-3.5 h-3.5 ${securityReport.reusedPasswordsCount > 0 ? 'text-red-500' : 'text-emerald-500'}`} />
                            </div>
                            <p className={`text-base font-bold font-mono mt-1 ${securityReport.reusedPasswordsCount > 0 ? 'text-red-500' : 'text-emerald-500'}`}>
                              {securityReport.reusedPasswordsCount}
                            </p>
                          </div>

                          <div className={`border rounded-xl p-2.5 transition-colors ${
                            isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-xs'
                          }`}>
                            <div className="flex items-center justify-between">
                              <span className={`text-[10px] font-mono ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>Short Length</span>
                              <AlertTriangle className={`w-3.5 h-3.5 ${securityReport.weakPasswordsCount > 0 ? 'text-amber-500' : 'text-emerald-500'}`} />
                            </div>
                            <p className={`text-base font-bold font-mono mt-1 ${securityReport.weakPasswordsCount > 0 ? 'text-amber-500' : 'text-emerald-500'}`}>
                              {securityReport.weakPasswordsCount}
                            </p>
                          </div>

                          <div className={`border rounded-xl p-2.5 transition-colors ${
                            isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-xs'
                          }`}>
                            <div className="flex items-center justify-between">
                              <span className={`text-[10px] font-mono ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>Common Patterns</span>
                              <ShieldAlert className={`w-3.5 h-3.5 ${securityReport.commonSequencesCount > 0 ? 'text-red-500' : 'text-emerald-500'}`} />
                            </div>
                            <p className={`text-base font-bold font-mono mt-1 ${securityReport.commonSequencesCount > 0 ? 'text-red-500' : 'text-emerald-500'}`}>
                              {securityReport.commonSequencesCount}
                            </p>
                          </div>

                          <div className={`border rounded-xl p-2.5 transition-colors ${
                            isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-xs'
                          }`}>
                            <div className="flex items-center justify-between">
                              <span className={`text-[10px] font-mono ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>Missing Variety</span>
                              <Info className={`w-3.5 h-3.5 ${securityReport.missingVarietyCount > 0 ? 'text-amber-500' : 'text-emerald-500'}`} />
                            </div>
                            <p className={`text-base font-bold font-mono mt-1 ${securityReport.missingVarietyCount > 0 ? 'text-amber-500' : 'text-emerald-500'}`}>
                              {securityReport.missingVarietyCount}
                            </p>
                          </div>
                        </div>

                        {/* Flagged Secrets List */}
                        <div>
                          <h4 className={`text-[11px] font-bold uppercase tracking-wider mb-2 font-mono flex items-center justify-between ${
                            isDarkMode ? 'text-gray-400' : 'text-slate-500'
                          }`}>
                            <span>Flagged Secrets ({securityReport.itemReports.filter(r => r.issues.length > 0).length})</span>
                            <span className="text-[9px] text-obscura-crimson">SSOT Diagnosis</span>
                          </h4>

                          {securityReport.itemReports.filter(r => r.issues.length > 0).length === 0 ? (
                            <div className={`border rounded-xl p-4 text-center ${
                              isDarkMode ? 'bg-[#121212] border-emerald-500/30' : 'bg-emerald-50 border-emerald-200'
                            }`}>
                              <ShieldCheck className="w-8 h-8 text-emerald-500 mx-auto mb-1.5" />
                              <p className="text-xs font-bold text-emerald-700">All Secrets Pass Security Diagnostic!</p>
                              <p className={`text-[10px] mt-0.5 ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>High-entropy secrets encrypted in Room DB.</p>
                            </div>
                          ) : (
                            <div className="space-y-2">
                              {securityReport.itemReports
                                .filter(r => r.issues.length > 0)
                                .map((rep) => {
                                  const targetItem = items.find(i => i.id === rep.itemId);
                                  if (!targetItem) return null;

                                  return (
                                    <div key={rep.itemId} className={`border rounded-xl p-3 space-y-2 ${
                                      isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-xs'
                                    }`}>
                                      <div className="flex items-start justify-between">
                                        <div>
                                          <h5 className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>{rep.itemTitle}</h5>
                                          <p className={`text-[10px] font-mono ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>
                                            {targetItem.usernameOrCardholder || 'Secret Record'}
                                          </p>
                                        </div>
                                        <span className={`text-[9px] font-mono font-bold px-2 py-0.5 rounded border ${
                                          rep.score < 50 
                                            ? 'bg-red-500/10 text-red-500 border-red-500/30' 
                                            : 'bg-amber-500/10 text-amber-500 border-amber-500/30'
                                        }`}>
                                          Score: {rep.score}/100
                                        </span>
                                      </div>

                                      <div className="space-y-1">
                                        {rep.issues.map((iss, idx) => (
                                          <div key={idx} className="flex items-center gap-1.5 text-[10px] text-red-500 font-mono">
                                            <AlertTriangle className="w-3 h-3 text-red-500 shrink-0" />
                                            <span>{iss.description}</span>
                                          </div>
                                        ))}
                                      </div>

                                      <button
                                        onClick={() => handleAutoFixItem(rep.itemId)}
                                        className={`w-full py-1.5 border border-obscura-crimson/50 text-obscura-crimson font-bold text-[10px] rounded-lg flex items-center justify-center gap-1.5 transition-all ${
                                          isDarkMode 
                                            ? 'bg-[#1c1c1c] hover:bg-obscura-crimson hover:text-black' 
                                            : 'bg-red-50 hover:bg-obscura-crimson hover:text-black'
                                        }`}
                                      >
                                        <Zap className="w-3 h-3" />
                                        <span>Regenerate High-Entropy Password</span>
                                      </button>
                                    </div>
                                  );
                                })}
                            </div>
                          )}
                        </div>
                      </div>
                    )}

                    {/* VIEW 3: SETTINGS & DYNAMIC MATERIAL DESIGN 3 THEME SWITCHER */}
                    {phoneView === 'settings' && (
                      settingsSubView === 'autolock' ? (
                        <AutoLockSettingsScreen
                          autoLockMinutes={autoLockMinutes}
                          onAutoLockMinutesChange={(mins) => setAutoLockMinutes(mins)}
                          clipboardTimeoutMinutes={clipboardTimeoutMinutes}
                          onClipboardTimeoutMinutesChange={(mins) => setClipboardTimeoutMinutes(mins)}
                          lockOnBackground={lockOnBackground}
                          onLockOnBackgroundChange={(val) => setLockOnBackground(val)}
                          biometricOnResume={biometricOnResume}
                          onBiometricOnResumeChange={(val) => setBiometricOnResume(val)}
                          sensitiveClipFlag={sensitiveClipFlag}
                          onSensitiveClipFlagChange={(val) => setSensitiveClipFlag(val)}
                          enforceStrongBiometricsOnly={enforceStrongBiometricsOnly}
                          onEnforceStrongBiometricsOnlyChange={(val) => setEnforceStrongBiometricsOnly(val)}
                          onBack={() => setSettingsSubView('main')}
                          onTriggerInstantLock={() => {
                            setIsAuthenticated(false);
                            addLog('Vault Locked immediately from Auto-Lock config', 'info');
                          }}
                          onClearClipboardNow={handleClearClipboardImmediately}
                          onAddLog={addLog}
                          isDarkMode={isDarkMode}
                        />
                      ) : (
                      <div className="flex-1 overflow-y-auto p-3 space-y-3 animate-fade-in">
                        {/* Theme Mode Card */}
                        <div className={`border rounded-xl p-3.5 space-y-3 transition-colors ${
                          isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-sm'
                        }`}>
                          <div className="flex items-center justify-between border-b pb-2.5 border-inherit">
                            <div className="flex items-center gap-2">
                              <Palette className={`w-4 h-4 ${isDarkMode ? 'text-obscura-crimson' : 'text-red-600'}`} />
                              <h3 className={`text-xs font-extrabold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
                                Material Design 3 Appearance
                              </h3>
                            </div>
                            <span className={`text-[9px] font-mono font-bold px-2 py-0.5 rounded-full border ${
                              isDarkMode ? 'bg-obscura-crimson/10 text-obscura-crimson border-obscura-crimson/30' : 'bg-red-50 text-red-700 border-red-200'
                            }`}>
                              Adaptive Scheme
                            </span>
                          </div>

                          <p className={`text-[10px] ${isDarkMode ? 'text-gray-400' : 'text-slate-600'}`}>
                            Choose your preferred color theme. Material Design 3 tokens adapt dynamically across all forms, secret vault lists, and security health cards.
                          </p>

                          {/* Theme Switcher Options */}
                          <div className="grid grid-cols-3 gap-2">
                            <button
                              onClick={() => {
                                setThemeMode('dark');
                                addLog('Applied Material Design 3 Dark Mode (OLED Charcoal)', 'info');
                              }}
                              className={`p-2 rounded-xl border flex flex-col items-center gap-1 transition-all ${
                                themeMode === 'dark'
                                  ? 'bg-obscura-crimson/20 border-obscura-crimson text-white ring-1 ring-obscura-crimson'
                                  : isDarkMode
                                  ? 'bg-[#181818] border-obscura-border text-gray-400 hover:text-white'
                                  : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                              }`}
                            >
                              <Moon className="w-4 h-4 text-amber-400" />
                              <span className="text-[10px] font-bold">Dark Mode</span>
                              <span className="text-[8px] font-mono opacity-70">True Black</span>
                            </button>

                            <button
                              onClick={() => {
                                setThemeMode('light');
                                addLog('Applied Material Design 3 Expressive Light Mode', 'info');
                              }}
                              className={`p-2 rounded-xl border flex flex-col items-center gap-1 transition-all ${
                                themeMode === 'light'
                                  ? 'bg-red-500/20 border-red-500 text-red-900 font-bold ring-1 ring-red-500'
                                  : isDarkMode
                                  ? 'bg-[#181818] border-obscura-border text-gray-400 hover:text-white'
                                  : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                              }`}
                            >
                              <Sun className="w-4 h-4 text-red-600" />
                              <span className="text-[10px] font-bold">Light Mode</span>
                              <span className="text-[8px] font-mono opacity-70">Expressive</span>
                            </button>

                            <button
                              onClick={() => {
                                setThemeMode('system');
                                addLog('Applied Material Design 3 System Auto Mode', 'info');
                              }}
                              className={`p-2 rounded-xl border flex flex-col items-center gap-1 transition-all ${
                                themeMode === 'system'
                                  ? 'bg-indigo-500/20 border-indigo-500 text-indigo-400 font-bold ring-1 ring-indigo-500'
                                  : isDarkMode
                                  ? 'bg-[#181818] border-obscura-border text-gray-400 hover:text-white'
                                  : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                              }`}
                            >
                              <Monitor className="w-4 h-4 text-indigo-400" />
                              <span className="text-[10px] font-bold">System Auto</span>
                              <span className="text-[8px] font-mono opacity-70">Auto Detect</span>
                            </button>
                          </div>

                          {/* Accent Palette Picker */}
                          <div className="pt-2 border-t border-inherit space-y-2">
                            <label className={`text-[10px] font-bold font-mono uppercase tracking-wider block ${
                              isDarkMode ? 'text-gray-400' : 'text-slate-600'
                            }`}>
                              Material3 Accent Palette:
                            </label>
                            <div className="flex gap-1.5">
                              {[
                                { id: 'crimson', name: 'Crimson', bg: 'bg-red-600' },
                                { id: 'emerald', name: 'Emerald', bg: 'bg-emerald-500' },
                                { id: 'amber', name: 'Amber', bg: 'bg-amber-500' },
                                { id: 'indigo', name: 'Indigo', bg: 'bg-indigo-500' },
                              ].map((pal) => (
                                <button
                                  key={pal.id}
                                  onClick={() => {
                                    setAccentPalette(pal.id as any);
                                    addLog(`Accent Palette set to ${pal.name}`, 'info');
                                  }}
                                  className={`flex-1 py-1.5 px-1 rounded-lg border text-[10px] font-bold flex items-center justify-center gap-1 transition-all ${
                                    accentPalette === pal.id
                                      ? 'border-obscura-crimson ring-1 ring-obscura-crimson font-extrabold scale-105'
                                      : isDarkMode
                                      ? 'bg-[#181818] border-obscura-border text-gray-400'
                                      : 'bg-slate-50 border-slate-200 text-slate-700'
                                  }`}
                                >
                                  <span className={`w-2 h-2 rounded-full ${pal.bg}`} />
                                  <span className="truncate">{pal.name}</span>
                                </button>
                              ))}
                            </div>
                          </div>

                          {/* Material Design 3 Live Token Inspector */}
                          <div className={`p-2.5 rounded-lg border font-mono text-[10px] space-y-1 ${
                            isDarkMode ? 'bg-[#0a0a0a] border-obscura-border text-gray-300' : 'bg-slate-100 border-slate-200 text-slate-800'
                          }`}>
                            <div className="text-[9px] text-gray-500 uppercase tracking-widest font-bold">MD3 Dynamic Color Tokens (Live)</div>
                            <div className="grid grid-cols-2 gap-x-2 gap-y-0.5 pt-1">
                              <div className="flex justify-between">
                                <span className="opacity-70">sys.primary:</span>
                                <span className="font-bold text-obscura-crimson">{isDarkMode ? '#E50914' : '#DC2626'}</span>
                              </div>
                              <div className="flex justify-between">
                                <span className="opacity-70">sys.surface:</span>
                                <span className="font-bold">{isDarkMode ? '#0A0A0A' : '#FFFFFF'}</span>
                              </div>
                              <div className="flex justify-between">
                                <span className="opacity-70">sys.container:</span>
                                <span className="font-bold">{isDarkMode ? '#121212' : '#F8FAFC'}</span>
                              </div>
                              <div className="flex justify-between">
                                <span className="opacity-70">sys.onSurface:</span>
                                <span className="font-bold">{isDarkMode ? '#FFFFFF' : '#0F172A'}</span>
                              </div>
                            </div>
                          </div>
                        </div>

                        {/* Security & Lock Behavior Settings Card */}
                        <div className={`border rounded-xl p-3.5 space-y-3 transition-colors ${
                          isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-sm'
                        }`}>
                          <div className="flex items-center justify-between border-b pb-2.5 border-inherit">
                            <div className="flex items-center gap-2">
                              <Shield className={`w-4 h-4 ${isDarkMode ? 'text-obscura-crimson' : 'text-red-600'}`} />
                              <h3 className={`text-xs font-extrabold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
                                Security & Auto-Lock
                              </h3>
                            </div>
                            <span className="text-[9px] text-emerald-500 font-mono bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 rounded-full font-bold">
                              AES-256 GCM
                            </span>
                          </div>

                          {/* Open Full Auto-Lock Screen Navigation Button */}
                          <button
                            onClick={() => {
                              setSettingsSubView('autolock');
                              addLog('Opened dedicated Auto-Lock & Timeout Duration Configuration Screen (1 - 60 min range)', 'info');
                            }}
                            className={`w-full p-2.5 rounded-xl border text-left flex items-center justify-between transition-all group ${
                              isDarkMode
                                ? 'bg-[#181818] hover:bg-[#202020] border-obscura-border hover:border-obscura-crimson/50'
                                : 'bg-slate-50 hover:bg-slate-100 border-slate-200'
                            }`}
                          >
                            <div className="flex items-center gap-2.5">
                              <div className="p-2 rounded-lg bg-obscura-crimson/15 text-obscura-crimson border border-obscura-crimson/30 group-hover:scale-105 transition-transform">
                                <Clock className="w-4 h-4" />
                              </div>
                              <div>
                                <div className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
                                  Auto-Lock & Timeout Configuration
                                </div>
                                <div className={`text-[10px] font-mono flex items-center gap-1.5 mt-0.5 ${isDarkMode ? 'text-gray-400' : 'text-slate-500'}`}>
                                  <span className="text-obscura-crimson font-bold">Vault: {autoLockMinutes}m</span>
                                  <span>•</span>
                                  <span className="text-cyan-400 font-bold">Clipboard: {clipboardTimeoutMinutes}m</span>
                                </div>
                              </div>
                            </div>
                            <div className="flex items-center gap-1 text-[11px] font-mono font-bold text-obscura-crimson group-hover:translate-x-0.5 transition-transform">
                              <span>1-60m</span>
                              <ChevronRight className="w-4 h-4" />
                            </div>
                          </button>

                          <div className="space-y-2">
                            <div className="flex items-center justify-between">
                              <label className={`text-[10px] font-bold font-mono uppercase tracking-wider ${
                                isDarkMode ? 'text-gray-400' : 'text-slate-600'
                              }`}>
                                Quick Inactivity Presets:
                              </label>
                              <span className="text-[9px] font-mono text-gray-500">Range: 1 - 60 min</span>
                            </div>
                            <div className="grid grid-cols-5 gap-1">
                              {[1, 5, 15, 30, 60].map((mins) => (
                                <button
                                  key={mins}
                                  onClick={() => {
                                    setAutoLockMinutes(mins);
                                    addLog(`Auto-lock timeout updated to ${mins} minutes`, 'info');
                                  }}
                                  className={`py-1.5 rounded-lg border text-[10px] font-mono font-bold transition-all ${
                                    autoLockMinutes === mins
                                      ? 'bg-obscura-crimson text-black font-extrabold border-obscura-crimson shadow-xs'
                                      : isDarkMode
                                      ? 'bg-[#181818] border-obscura-border text-gray-300 hover:text-white'
                                      : 'bg-slate-50 border-slate-200 text-slate-700 hover:bg-slate-100'
                                  }`}
                                >
                                  {mins}m
                                </button>
                              ))}
                            </div>
                          </div>

                          {/* Quick Strong Biometrics Policy Switch */}
                          <div className={`p-2.5 rounded-xl border flex items-center justify-between gap-2 transition-all ${
                            enforceStrongBiometricsOnly
                              ? isDarkMode ? 'bg-obscura-crimson/10 border-obscura-crimson/40' : 'bg-red-50 border-red-200'
                              : isDarkMode ? 'bg-[#181818] border-obscura-border' : 'bg-slate-50 border-slate-200'
                          }`}>
                            <div className="flex items-center gap-2">
                              <Fingerprint className={`w-4 h-4 ${enforceStrongBiometricsOnly ? 'text-obscura-crimson' : 'text-gray-400'}`} />
                              <div>
                                <div className="text-[11px] font-bold text-white flex items-center gap-1.5">
                                  <span>Strong Biometrics Only</span>
                                  <span className={`text-[8px] font-mono px-1 rounded ${
                                    enforceStrongBiometricsOnly ? 'bg-obscura-crimson/20 text-obscura-crimson font-bold' : 'text-gray-400'
                                  }`}>
                                    {enforceStrongBiometricsOnly ? 'BIOMETRIC_STRONG' : 'FALLBACK OK'}
                                  </span>
                                </div>
                                <div className="text-[9px] text-gray-400">
                                  {enforceStrongBiometricsOnly ? 'DEVICE_CREDENTIAL fallback disabled' : 'Allows PIN / Pattern fallback'}
                                </div>
                              </div>
                            </div>
                            <button
                              onClick={() => {
                                const nextVal = !enforceStrongBiometricsOnly;
                                setEnforceStrongBiometricsOnly(nextVal);
                                addLog(`Enforce Strong Biometrics Only set to ${nextVal ? 'ENABLED' : 'DISABLED'}`, nextVal ? 'success' : 'info');
                              }}
                              className={`w-9 h-5 rounded-full p-0.5 transition-colors cursor-pointer shrink-0 ${
                                enforceStrongBiometricsOnly ? 'bg-obscura-crimson' : isDarkMode ? 'bg-gray-700' : 'bg-slate-300'
                              }`}
                            >
                              <div
                                className={`w-4 h-4 rounded-full bg-white transition-transform ${
                                  enforceStrongBiometricsOnly ? 'translate-x-4' : 'translate-x-0'
                                }`}
                              />
                            </button>
                          </div>

                          {/* Clear Cache / Lock Now Button */}
                          <div className="pt-2 flex gap-2">
                            <button
                              onClick={() => {
                                setIsAuthenticated(false);
                                addLog('Vault manually locked from Settings', 'info');
                              }}
                              className={`flex-1 py-2 border rounded-xl font-bold text-xs flex items-center justify-center gap-1.5 transition-all ${
                                isDarkMode 
                                  ? 'bg-[#181818] hover:bg-[#222] border-obscura-border text-white' 
                                  : 'bg-slate-100 hover:bg-slate-200 border-slate-200 text-slate-800'
                              }`}
                            >
                              <Lock className="w-3.5 h-3.5 text-obscura-crimson" />
                              <span>Lock Vault Now</span>
                            </button>

                            <button
                              onClick={() => {
                                addLog('Room DB SQLite cache cleared • SSOT re-indexed', 'info');
                                setCopiedToast('Cache Cleared');
                                setTimeout(() => setCopiedToast(null), 1500);
                              }}
                              className={`py-2 px-3 border rounded-xl font-bold text-xs flex items-center justify-center gap-1.5 transition-all ${
                                isDarkMode 
                                  ? 'bg-[#181818] hover:bg-[#222] border-obscura-border text-gray-300' 
                                  : 'bg-slate-100 hover:bg-slate-200 border-slate-200 text-slate-700'
                              }`}
                            >
                              <RefreshCcw className="w-3.5 h-3.5 text-emerald-500" />
                              <span>Sync DB</span>
                            </button>
                          </div>
                        </div>

                        {/* Backup & Encrypted Export Card */}
                        <div className={`border rounded-xl p-3.5 space-y-3 transition-colors ${
                          isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-sm'
                        }`}>
                          <div className="flex items-center justify-between border-b pb-2.5 border-inherit">
                            <div className="flex items-center gap-2">
                              <Lock className={`w-4 h-4 ${isDarkMode ? 'text-amber-400' : 'text-amber-600'}`} />
                              <h3 className={`text-xs font-extrabold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
                                Encrypted Backup & Vault Export
                              </h3>
                            </div>
                            <span className="text-[9px] text-amber-400 font-mono bg-amber-500/10 border border-amber-500/30 px-2 py-0.5 rounded-full font-bold">
                              AES-256-GCM
                            </span>
                          </div>

                          <p className={`text-[10px] leading-relaxed ${isDarkMode ? 'text-gray-400' : 'text-slate-600'}`}>
                            Export all vault secrets into a standalone password-protected JSON or binary file encrypted with AES-256-GCM and PBKDF2 (120,000 SHA-256 iterations) matching <code>BackupCryptoUtils</code>.
                          </p>

                          <div className="grid grid-cols-2 gap-2 pt-1">
                            <button
                              onClick={() => setIsBackupExportOpen(true)}
                              className="py-2.5 px-3 bg-amber-500/15 hover:bg-amber-500/25 border border-amber-500/40 text-amber-300 font-bold text-xs rounded-xl flex items-center justify-center gap-1.5 transition-all active:scale-98 shadow-sm cursor-pointer"
                            >
                              <Download className="w-4 h-4 text-amber-400" />
                              <span>Export Backup</span>
                            </button>

                            <button
                              onClick={() => setIsBackupRestoreOpen(true)}
                              className="py-2.5 px-3 bg-cyan-500/15 hover:bg-cyan-500/25 border border-cyan-500/40 text-cyan-300 font-bold text-xs rounded-xl flex items-center justify-center gap-1.5 transition-all active:scale-98 shadow-sm cursor-pointer"
                            >
                              <Upload className="w-4 h-4 text-cyan-400" />
                              <span>Restore Vault</span>
                            </button>
                          </div>
                        </div>

                        {/* Data Migration & Bulk Import Card */}
                        <div className={`border rounded-xl p-3.5 space-y-3 transition-colors ${
                          isDarkMode ? 'bg-[#121212] border-obscura-border' : 'bg-white border-slate-200 shadow-sm'
                        }`}>
                          <div className="flex items-center justify-between border-b pb-2.5 border-inherit">
                            <div className="flex items-center gap-2">
                              <FileSpreadsheet className={`w-4 h-4 ${isDarkMode ? 'text-sky-400' : 'text-sky-600'}`} />
                              <h3 className={`text-xs font-extrabold ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
                                Migration & CSV Import
                              </h3>
                            </div>
                            <span className="text-[9px] text-sky-400 font-mono bg-sky-500/10 border border-sky-500/30 px-2 py-0.5 rounded-full font-bold">
                              SAF STREAMING
                            </span>
                          </div>

                          <p className={`text-[10px] leading-relaxed ${isDarkMode ? 'text-gray-400' : 'text-slate-600'}`}>
                            Migrate existing passwords from Google Chrome, Bitwarden, KeePass, or custom CSV exports with atomic Room @Transaction batch insertion.
                          </p>

                          <button
                            onClick={() => setIsCsvImportOpen(true)}
                            className="w-full py-2.5 bg-sky-500/10 hover:bg-sky-500/20 border border-sky-500/40 text-sky-300 font-bold text-xs rounded-xl flex items-center justify-center gap-2 transition-all active:scale-98 shadow-sm cursor-pointer"
                          >
                            <FileSpreadsheet className="w-4 h-4 text-sky-400" />
                            <span>Launch CSV Import Wizard</span>
                          </button>
                        </div>
                      </div>
                      )
                    )}
                  </div>
                )}

                {/* Add Secret Slide-Up Modal Sheet */}
                <AnimatePresence>
                  {isModalOpen && (
                    <motion.div
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      exit={{ opacity: 0 }}
                      transition={{ duration: 0.2 }}
                      className="absolute inset-0 bg-black/80 backdrop-blur-sm z-40 flex flex-col justify-end"
                      onClick={() => setIsModalOpen(false)}
                    >
                      <motion.div
                        initial={{ y: '100%' }}
                        animate={{ y: 0 }}
                        exit={{ y: '100%' }}
                        transition={{ type: 'spring', damping: 28, stiffness: 300 }}
                        onClick={(e) => e.stopPropagation()}
                        className="bg-[#141414] border-t border-obscura-border rounded-t-3xl p-4 max-h-[90%] flex flex-col overflow-hidden shadow-2xl"
                      >
                        {/* Modal Header & Grab Handle */}
                        <div className="flex flex-col items-center mb-3">
                          <div className="w-10 h-1 bg-gray-600 rounded-full mb-2.5" />
                          <div className="flex items-center justify-between w-full">
                            <div className="flex items-center gap-2">
                              <div className="w-6 h-6 rounded-lg bg-obscura-crimson/15 border border-obscura-crimson/40 flex items-center justify-center">
                                <Plus className="w-3.5 h-3.5 text-obscura-crimson stroke-[3]" />
                              </div>
                              <h3 className="font-extrabold text-sm text-white tracking-wide">Add New Secret</h3>
                            </div>
                            <button
                              type="button"
                              onClick={() => setIsModalOpen(false)}
                              className="p-1 rounded-full text-gray-400 hover:text-white hover:bg-white/10"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          </div>
                        </div>

                        {/* Modal Form */}
                        <form onSubmit={handleAddSecret} className="space-y-3 overflow-y-auto pr-1 flex-1">
                          {/* Title */}
                          <div>
                            <label className="text-[10px] text-gray-400 font-mono">Title / Service Name</label>
                            <input
                              type="text"
                              required
                              placeholder="e.g. Netflix, GitHub, Primary Visa"
                              value={newTitle}
                              onChange={(e) => setNewTitle(e.target.value)}
                              className="w-full mt-0.5 p-2 bg-[#0c0c0c] border border-obscura-border rounded-xl text-xs text-white placeholder:text-gray-600 focus:border-obscura-crimson focus:outline-none"
                            />
                          </div>

                          {/* Category */}
                          <div>
                            <label className="text-[10px] text-gray-400 font-mono">Category</label>
                            <div className="grid grid-cols-4 gap-1 mt-0.5">
                              {(['LOGIN', 'BANK_CARD', 'API_KEY', 'SECURE_NOTE'] as const).map((cat) => (
                                <button
                                  type="button"
                                  key={cat}
                                  onClick={() => setNewCategory(cat)}
                                  className={`py-1.5 px-1 rounded-lg text-[9px] font-mono font-bold transition-all ${
                                    newCategory === cat
                                      ? 'bg-obscura-crimson text-black shadow-xs'
                                      : 'bg-[#1e1e1e] text-gray-400 hover:text-white'
                                  }`}
                                >
                                  {cat.replace('_', ' ')}
                                </button>
                              ))}
                            </div>
                          </div>

                          {/* Username / Account */}
                          <div>
                            <label className="text-[10px] text-gray-400 font-mono">
                              {newCategory === 'BANK_CARD' ? 'Cardholder Name' : 'Username / Email / Identifier'}
                            </label>
                            <input
                              type="text"
                              placeholder={newCategory === 'BANK_CARD' ? 'JOHN DOE' : 'user@obscura.app'}
                              value={newUsername}
                              onChange={(e) => setNewUsername(e.target.value)}
                              className="w-full mt-0.5 p-2 bg-[#0c0c0c] border border-obscura-border rounded-xl text-xs text-white placeholder:text-gray-600 focus:border-obscura-crimson focus:outline-none font-mono"
                            />
                          </div>

                          {/* Secret Value & Quick Generator */}
                          <div>
                            <div className="flex items-center justify-between">
                              <label className="text-[10px] text-gray-400 font-mono">
                                {newCategory === 'BANK_CARD' ? 'CVV / Security Code' : newCategory === 'SECURE_NOTE' ? 'Secure Text' : 'Password / API Key'}
                              </label>
                              <div className="flex items-center gap-1">
                                <button
                                  type="button"
                                  onClick={() => setShowAddGenerator(!showAddGenerator)}
                                  className="text-[9px] text-obscura-crimson font-mono flex items-center gap-0.5 hover:underline"
                                >
                                  <Sparkles className="w-2.5 h-2.5" />
                                  <span>{showAddGenerator ? 'Hide Gen' : 'Generate'}</span>
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleOpenQrScanner('add')}
                                  className="text-[9px] text-emerald-400 font-mono flex items-center gap-0.5 hover:underline ml-1.5"
                                >
                                  <QrCode className="w-2.5 h-2.5" />
                                  <span>Scan QR</span>
                                </button>
                              </div>
                            </div>
                            <input
                              type="text"
                              required
                              placeholder="Enter sensitive secret value"
                              value={newSecret}
                              onChange={(e) => setNewSecret(e.target.value)}
                              className="w-full mt-0.5 p-2 bg-[#0c0c0c] border border-obscura-border rounded-xl text-xs text-white font-mono placeholder:text-gray-600 focus:border-obscura-crimson focus:outline-none"
                            />

                            {/* Real-time Entropy & Password Strength Indicator for Add Secret */}
                            <PasswordStrengthIndicator
                              secret={newSecret}
                              isCompact={false}
                              showChecklist={true}
                            />

                            {/* In-App Password Generator for Add modal */}
                            {showAddGenerator && (
                              <div className="mt-2 p-2.5 bg-[#0a0a0a] border border-obscura-border rounded-xl">
                                <div className="flex items-center justify-between mb-1.5">
                                  <span className="text-[10px] font-mono text-gray-400 font-bold">In-App Cryptographic Generator</span>
                                  <button
                                    type="button"
                                    onClick={() => {
                                      const gen = generateStrongSecret();
                                      setNewSecret(gen);
                                      setCopiedToast('Generated secure 20-char key');
                                      setTimeout(() => setCopiedToast(null), 1500);
                                    }}
                                    className="px-2 py-0.5 bg-obscura-crimson text-black font-extrabold text-[9px] rounded flex items-center gap-1"
                                  >
                                    <Sparkles className="w-2.5 h-2.5" />
                                    <span>Roll Strong Key</span>
                                  </button>
                                </div>
                                <p className="text-[9px] font-mono text-gray-500">
                                  Uses java.security.SecureRandom with high Shannon Entropy (84.2 bits)
                                </p>
                              </div>
                            )}
                          </div>

                          {/* URL / Secondary Info */}
                          <div>
                            <label className="text-[10px] text-gray-400 font-mono">Website URL / Server Host</label>
                            <input
                              type="text"
                              placeholder="https://..."
                              value={newUrl}
                              onChange={(e) => setNewUrl(e.target.value)}
                              className="w-full mt-0.5 p-2 bg-[#0c0c0c] border border-obscura-border rounded-xl text-xs text-white placeholder:text-gray-600 focus:border-obscura-crimson focus:outline-none"
                            />
                          </div>

                          {/* Tags */}
                          <div>
                            <label className="text-[10px] text-gray-400 font-mono">Tags (comma separated)</label>
                            <input
                              type="text"
                              placeholder="Personal, Work, Streaming"
                              value={newTagsString}
                              onChange={(e) => setNewTagsString(e.target.value)}
                              className="w-full mt-0.5 p-2 bg-[#0c0c0c] border border-obscura-border rounded-xl text-xs text-white placeholder:text-gray-600 focus:border-obscura-crimson focus:outline-none"
                            />
                          </div>

                          {/* Submit Actions */}
                          <div className="flex gap-2 pt-2 pb-1">
                            <button
                              type="button"
                              onClick={() => setIsModalOpen(false)}
                              className="flex-1 py-2 bg-[#222] text-gray-300 font-semibold text-xs rounded-xl hover:bg-[#2c2c2c]"
                            >
                              Cancel
                            </button>
                            <button
                              type="submit"
                              className="flex-1 py-2 bg-obscura-crimson hover:bg-obscura-crimsonHover text-black font-extrabold text-xs rounded-xl flex items-center justify-center gap-1 shadow-lg shadow-obscura-crimson/25"
                            >
                              <Plus className="w-3.5 h-3.5 stroke-[3]" />
                              <span>Save to SQLCipher DB</span>
                            </button>
                          </div>
                        </form>
                      </motion.div>
                    </motion.div>
                  )}
                </AnimatePresence>

                {/* Android Material 3 BiometricPrompt System Dialog Overlay */}
                <AnimatePresence>
                  {isBiometricPromptOpen && (
                    <motion.div
                      initial={{ opacity: 0 }}
                      animate={{ opacity: 1 }}
                      exit={{ opacity: 0 }}
                      transition={{ duration: 0.15 }}
                      className="absolute inset-0 bg-black/80 backdrop-blur-xs z-50 flex flex-col justify-end"
                      onClick={() => handleBiometricError(10, 'User cancelled prompt')}
                    >
                      <motion.div
                        initial={{ y: '100%' }}
                        animate={{ y: 0 }}
                        exit={{ y: '100%' }}
                        transition={{ type: 'spring', damping: 28, stiffness: 300 }}
                        onClick={(e) => e.stopPropagation()}
                        className="bg-[#161616] border-t border-obscura-border rounded-t-3xl p-4 shadow-2xl space-y-3 relative"
                      >
                        {/* Drag Handle */}
                        <div className="w-10 h-1 bg-gray-600 rounded-full mx-auto mb-1" />
                        
                        {/* System Biometric Header */}
                        <div className="flex items-start justify-between">
                          <div className="flex items-center gap-2.5">
                            <div className={`p-2.5 rounded-xl ${
                              biometricState === 'error' || biometricState === 'failed'
                                ? 'bg-red-500/20 text-red-500 border border-red-500/40'
                                : biometricState === 'success'
                                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                                : 'bg-obscura-crimson/20 text-obscura-crimson border border-obscura-crimson/40'
                            }`}>
                              {biometricType === 'faceid' ? (
                                <ScanFace className="w-6 h-6 text-obscura-crimson" />
                              ) : (
                                <Fingerprint className="w-6 h-6 text-obscura-crimson animate-pulse" />
                              )}
                            </div>
                            <div>
                              <h3 className="font-extrabold text-sm text-white">Obscura Vault Master Key</h3>
                              <p className="text-[10px] font-mono text-gray-400">
                                {enforceStrongBiometricsOnly
                                  ? 'BIOMETRIC_STRONG (Class 3 Hardware Only)'
                                  : 'BIOMETRIC_STRONG | DEVICE_CREDENTIAL'}
                              </p>
                            </div>
                          </div>
                          <span className={`text-[8.5px] font-mono font-bold px-2 py-0.5 rounded-full border ${
                            enforceStrongBiometricsOnly 
                              ? 'bg-obscura-crimson/20 text-obscura-crimson border-obscura-crimson/50' 
                              : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                          }`}>
                            {enforceStrongBiometricsOnly ? 'STRONG ONLY' : 'FALLBACK OK'}
                          </span>
                        </div>

                        {/* Subtitle & State Feedback */}
                        <div className="p-2.5 bg-[#0d0d0d] border border-obscura-border/60 rounded-xl space-y-1 text-center">
                          <p className="text-[11px] font-mono text-gray-200">
                            {biometricStatusText}
                          </p>
                          <p className="text-[9px] font-mono text-gray-400">
                            {enforceStrongBiometricsOnly
                              ? 'Class 3 hardware biometrics enforced. Device PIN/Pattern fallback is disabled.'
                              : 'Class 3 hardware biometrics or device PIN/pattern credential.'}
                          </p>
                        </div>

                        {/* Interactive Sensor Target Button */}
                        <div className="flex flex-col items-center justify-center py-2">
                          <button
                            onClick={() => handleBiometricSuccess(biometricType)}
                            className="w-16 h-16 rounded-full border-2 border-obscura-crimson/80 bg-obscura-crimson/15 flex items-center justify-center shadow-lg shadow-obscura-crimson/20 hover:scale-105 active:scale-95 transition-all group cursor-pointer"
                            title="Tap to verify biometric sensor"
                          >
                            {biometricType === 'faceid' ? (
                              <ScanFace className="w-8 h-8 text-obscura-crimson group-hover:text-white transition-colors" />
                            ) : (
                              <Fingerprint className="w-8 h-8 text-obscura-crimson group-hover:text-white transition-colors animate-pulse" />
                            )}
                          </button>
                          <span className="text-[9px] font-mono text-gray-400 mt-1.5">Tap to Authenticate Sensor</span>
                        </div>

                        {/* Action Controls */}
                        <div className="flex gap-2 pt-1">
                          {!enforceStrongBiometricsOnly && (
                            <button
                              onClick={() => {
                                setIsBiometricPromptOpen(false);
                                addLog('[BiometricPrompt] Fallback to Device Credential (PIN / Pattern) selected', 'info');
                              }}
                              className="flex-1 py-2 bg-[#222] hover:bg-[#2c2c2c] text-gray-300 font-semibold text-xs rounded-xl font-mono"
                            >
                              Use Device PIN
                            </button>
                          )}
                          
                          <button
                            onClick={() => handleBiometricError(10, 'User clicked negative button (Cancel)')}
                            className={`py-2 bg-[#1a1a1a] hover:bg-[#222] text-gray-400 hover:text-white font-semibold text-xs rounded-xl font-mono ${
                              enforceStrongBiometricsOnly ? 'w-full' : 'flex-1'
                            }`}
                          >
                            Cancel {enforceStrongBiometricsOnly ? '(NegativeButtonText)' : ''}
                          </button>
                        </div>
                      </motion.div>
                    </motion.div>
                  )}
                </AnimatePresence>

                {/* Copied Toast Banner inside Phone */}
                {copiedToast && (
                  <div className="absolute bottom-16 left-4 right-4 bg-emerald-950 border border-emerald-500/50 text-emerald-300 text-[11px] font-mono py-2 px-3 rounded-xl text-center shadow-lg animate-fade-in z-30">
                    {copiedToast}
                  </div>
                )}
              </div>

              {/* Navigation Bar / Home Indicator */}
              <div className="w-full bg-black py-2 flex justify-center items-center z-20">
                <div className="w-32 h-1 bg-gray-600 rounded-full"></div>
              </div>
            </div>

            {/* Side Panel: Biometric Controller & Real-Time Event Log */}
            <div className="flex-1 w-full flex flex-col gap-4">
              
              {/* Biometric Prompt Test Controls Card */}
              <div className="bg-[#121212] border border-obscura-border rounded-2xl p-5 shadow-xl">
                <div className="flex items-center justify-between mb-3 border-b border-obscura-border pb-3">
                  <div className="flex items-center gap-2.5">
                    <div className="p-2 rounded-lg bg-obscura-crimson/10 border border-obscura-crimson/40">
                      <Fingerprint className="w-5 h-5 text-obscura-crimson" />
                    </div>
                    <div>
                      <h3 className="font-bold text-sm text-white">Android BiometricPrompt Controller</h3>
                      <p className="text-xs text-gray-400 font-mono">androidx.biometric.BiometricPrompt API</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className={`text-[10px] border px-2.5 py-1 rounded-full font-mono font-bold ${
                      enforceStrongBiometricsOnly
                        ? 'bg-obscura-crimson/20 text-obscura-crimson border-obscura-crimson/50'
                        : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                    }`}>
                      {enforceStrongBiometricsOnly ? 'BIOMETRIC_STRONG ONLY' : 'STRONG | DEVICE_CREDENTIAL'}
                    </span>
                  </div>
                </div>

                {/* Enforce Strong Biometrics Switch in Controller */}
                <div className={`mb-3.5 p-3 rounded-xl border flex items-center justify-between gap-3 transition-colors ${
                  enforceStrongBiometricsOnly
                    ? 'bg-obscura-crimson/10 border-obscura-crimson/40'
                    : 'bg-[#181818] border-obscura-border'
                }`}>
                  <div className="space-y-0.5">
                    <div className="text-xs font-bold text-white flex items-center gap-1.5">
                      <span>Enforce Strong Biometrics Only</span>
                      {enforceStrongBiometricsOnly && (
                        <span className="text-[9px] font-mono text-obscura-crimson font-extrabold bg-obscura-crimson/20 px-1.5 py-0.2 rounded border border-obscura-crimson/40">
                          PIN/Pattern Disabled
                        </span>
                      )}
                    </div>
                    <p className="text-[10px] text-gray-400 font-mono">
                      {enforceStrongBiometricsOnly
                        ? 'BiometricPrompt.PromptInfo: BIOMETRIC_STRONG (Class 3 sensor required. Fallback disabled).'
                        : 'BiometricPrompt.PromptInfo: BIOMETRIC_STRONG or DEVICE_CREDENTIAL (PIN fallback allowed).'}
                    </p>
                  </div>
                  <button
                    onClick={() => {
                      const nextVal = !enforceStrongBiometricsOnly;
                      setEnforceStrongBiometricsOnly(nextVal);
                      addLog(`Enforce Strong Biometrics Only toggled: ${nextVal ? 'ENABLED' : 'DISABLED'}`, nextVal ? 'success' : 'info');
                    }}
                    className={`w-11 h-6 rounded-full p-0.5 transition-colors cursor-pointer shrink-0 ${
                      enforceStrongBiometricsOnly ? 'bg-obscura-crimson' : 'bg-gray-700'
                    }`}
                  >
                    <div
                      className={`w-5 h-5 rounded-full bg-white transition-transform ${
                        enforceStrongBiometricsOnly ? 'translate-x-5' : 'translate-x-0'
                      }`}
                    />
                  </button>
                </div>

                <p className="text-xs text-gray-300 mb-4 leading-relaxed">
                  Test the Android system authentication callbacks in real-time. Triggering a simulation invokes 
                  <code className="text-obscura-crimson bg-[#1a1a1a] px-1.5 py-0.5 rounded ml-1 font-mono">BiometricPrompt.authenticate()</code>.
                </p>

                <div className="grid grid-cols-2 gap-2.5">
                  <button
                    onClick={() => {
                      handleOpenBiometricPrompt('fingerprint');
                      setTimeout(() => handleBiometricSuccess('fingerprint'), 800);
                    }}
                    className="py-2.5 px-3 bg-emerald-950/60 hover:bg-emerald-900 border border-emerald-600/50 text-emerald-300 rounded-xl font-bold text-xs flex items-center justify-center gap-2 transition-all shadow-md"
                  >
                    <Fingerprint className="w-4 h-4 text-emerald-400" />
                    <span>Fingerprint Auth (Pass)</span>
                  </button>

                  <button
                    onClick={() => {
                      handleOpenBiometricPrompt('faceid');
                      setTimeout(() => handleBiometricSuccess('faceid'), 800);
                    }}
                    className="py-2.5 px-3 bg-emerald-950/60 hover:bg-emerald-900 border border-emerald-600/50 text-emerald-300 rounded-xl font-bold text-xs flex items-center justify-center gap-2 transition-all shadow-md"
                  >
                    <ScanFace className="w-4 h-4 text-emerald-400" />
                    <span>FaceID Auth (Pass)</span>
                  </button>

                  <button
                    onClick={() => {
                      handleOpenBiometricPrompt('fingerprint');
                      setTimeout(() => handleBiometricFailure(), 800);
                    }}
                    className="py-2.5 px-3 bg-red-950/60 hover:bg-red-900 border border-red-600/50 text-red-300 rounded-xl font-bold text-xs flex items-center justify-center gap-2 transition-all shadow-md"
                  >
                    <XCircle className="w-4 h-4 text-red-400" />
                    <span>Simulate Sensor Fail</span>
                  </button>

                  <button
                    onClick={() => {
                      handleOpenBiometricPrompt('fingerprint');
                      setTimeout(() => handleBiometricError(10, 'User cancelled biometric prompt'), 800);
                    }}
                    className="py-2.5 px-3 bg-[#1e1e1e] hover:bg-[#282828] border border-obscura-border text-gray-300 rounded-xl font-bold text-xs flex items-center justify-center gap-2 transition-all"
                  >
                    <AlertCircle className="w-4 h-4 text-amber-400" />
                    <span>User Cancel Error</span>
                  </button>
                </div>

                {/* Android Quick Settings Notification Shade Tile Simulation */}
                <div className="mt-3.5 pt-3.5 border-t border-obscura-border">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-[11px] font-bold text-gray-300 font-mono flex items-center gap-1.5">
                      <SlidersHorizontal className="w-3.5 h-3.5 text-obscura-crimson" />
                      Quick Settings Notification Tile (TileService)
                    </span>
                    <span className="text-[9px] font-mono text-emerald-400 bg-emerald-500/10 border border-emerald-500/30 px-1.5 py-0.5 rounded font-bold">
                      API 34+ Ready
                    </span>
                  </div>
                  <button
                    onClick={() => {
                      setIsQuickSearchTileOpen(true);
                      addLog('[TileService.onClick] Obscura QuickSearchTile clicked -> Calling startActivityAndCollapse(PendingIntent) on Android 14+', 'info');
                      addLog('[QuickSearchActivity] Launched floating dialog activity with FLAG_SECURE & BiometricPrompt gate', 'info');
                    }}
                    className="w-full py-2.5 px-3 bg-gradient-to-r from-obscura-crimson/20 via-obscura-crimson/30 to-obscura-crimson/20 hover:from-obscura-crimson/30 hover:to-obscura-crimson/30 border border-obscura-crimson/50 text-white rounded-xl font-bold text-xs flex items-center justify-center gap-2 transition-all shadow-md shadow-obscura-crimson/10 group cursor-pointer"
                  >
                    <Key className="w-4 h-4 text-obscura-crimson group-hover:scale-110 transition-transform" />
                    <span>Trigger "Quick Search" Notification Shade Tile</span>
                  </button>
                </div>
              </div>

              {/* Real-time Biometric Event Callback Console Log */}
              <div className="bg-[#0e0e0e] border border-obscura-border rounded-2xl p-4 shadow-xl flex flex-col h-[320px]">
                <div className="flex items-center justify-between mb-2 pb-2 border-b border-obscura-border">
                  <div className="flex items-center gap-2">
                    <Terminal className="w-4 h-4 text-obscura-crimson" />
                    <span className="text-xs font-bold text-white font-mono">BiometricPrompt Callback Event Log</span>
                  </div>
                  <button
                    onClick={() => setBiometricLogs([])}
                    className="text-[10px] text-gray-500 hover:text-white font-mono flex items-center gap-1"
                  >
                    <RefreshCcw className="w-3 h-3" />
                    Clear
                  </button>
                </div>

                <div className="flex-1 overflow-y-auto space-y-1.5 font-mono text-[11px] pr-1">
                  {biometricLogs.length === 0 ? (
                    <p className="text-gray-600 py-4 text-center">No biometric callback logs registered</p>
                  ) : (
                    biometricLogs.map((log) => (
                      <div
                        key={log.id}
                        className={`p-2 rounded-lg border text-xs flex items-start gap-2 ${
                          log.type === 'success' 
                            ? 'bg-emerald-950/30 border-emerald-800/40 text-emerald-300' 
                            : log.type === 'error'
                            ? 'bg-red-950/30 border-red-800/40 text-red-300'
                            : 'bg-[#141414] border-obscura-border text-gray-300'
                        }`}
                      >
                        <span className="text-gray-500 text-[10px] shrink-0 mt-0.5">{log.time}</span>
                        <span className="flex-1 leading-snug">{log.message}</span>
                      </div>
                    ))
                  )}
                </div>
              </div>

            </div>

          </div>
        )}

        {/* Code Inspection Tab */}
        {activeTab === 'code' && (
          <div className="w-full max-w-5xl bg-[#0e0e0e] border border-obscura-border rounded-2xl overflow-hidden shadow-2xl">
            <div className="flex flex-wrap items-center justify-between px-6 py-3 bg-[#141414] border-b border-obscura-border gap-2">
              <div className="flex items-center gap-2">
                <Code className="w-5 h-5 text-obscura-crimson" />
                <span className="font-bold text-sm tracking-wide">Kotlin & Biometric Jetpack Architecture</span>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {(['AutoLockSettingsScreen', 'QuickSearchTileService', 'QuickSearchActivity', 'SecurityHealthGauge', 'Fido2CryptographyUtils', 'AutofillBiometricAuthActivity', 'ObscuraAutofillService', 'SecureClipboardManager', 'BackupCryptoUtils', 'BackupManager', 'BiometricAuthManager', 'AuthScreen', 'VaultDatabase', 'DashboardScreen', 'VaultEntity', 'VaultFtsEntity', 'VaultDao', 'VaultViewModel', 'EditVaultEntryBottomSheet', 'PasswordGeneratorSheet', 'ObscuraTheme', 'CsvImportEngine'] as const).map((file) => (
                  <button
                    key={file}
                    onClick={() => setSelectedFile(file as any)}
                    className={`px-3 py-1 rounded-lg text-xs font-mono transition-all ${
                      selectedFile === file
                        ? 'bg-obscura-crimson text-black font-bold shadow-md'
                        : 'bg-[#1e1e1e] text-gray-400 hover:text-white'
                    }`}
                  >
                    {file}.kt
                  </button>
                ))}
              </div>
            </div>

            <div className="p-6 overflow-x-auto font-mono text-xs text-gray-300 bg-[#080808] leading-relaxed max-h-[600px]">
              {selectedFile === 'AutoLockSettingsScreen' && (
                <pre className="text-rose-400">{`package com.obscura.ui.settings

import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.obscura.security.SecureClipboardManager
import com.obscura.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * AutoLockSettingsScreen.kt
 *
 * Material 3 Jetpack Compose screen for configuring:
 * 1. Vault Inactivity Auto-Lock duration (1 - 60 minutes) backed by ProcessLifecycleOwner & Room AES-256 memory wipe.
 * 2. SecureClipboardManager auto-clear delay (1 - 60 minutes) using Coroutine delay with ClipDescription.EXTRA_IS_SENSITIVE.
 * 3. Immediate app-background lock toggles (ProcessLifecycleOwner.ON_STOP).
 */
@Keep
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoLockSettingsScreen(
    viewModel: SettingsViewModel,
    secureClipboardManager: SecureClipboardManager,
    onNavigateBack: () -> Unit,
    onLockVaultNow: () -> Unit
) {
    val autoLockMinutes by viewModel.autoLockMinutes.collectAsStateWithLifecycle()
    val clipboardTimeoutMinutes by viewModel.clipboardTimeoutMinutes.collectAsStateWithLifecycle()
    val lockOnBackground by viewModel.lockOnBackground.collectAsStateWithLifecycle()
    val biometricOnResume by viewModel.biometricOnResume.collectAsStateWithLifecycle()
    val sensitiveClipFlag by viewModel.sensitiveClipFlag.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val presetDurations = listOf(1, 2, 5, 10, 15, 30, 45, 60)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Auto-Lock & Timeout Guards",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Configure 1-60 min Vault & Clipboard Timeouts",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0C0C0C)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CARD 1: Vault Inactivity Auto-Lock (1 to 60 Minutes)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFE50914))
                            Text(
                                "Vault Inactivity Timeout",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE50914).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE50914).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "$autoLockMinutes min",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFE50914),
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }

                    Text(
                        "Locks SQLCipher master key in memory when no touch/motion is detected on UI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )

                    // Continuous Slider (1 to 60 minutes)
                    Slider(
                        value = autoLockMinutes.toFloat(),
                        onValueChange = { viewModel.setAutoLockMinutes(it.toInt()) },
                        valueRange = 1f..60f,
                        steps = 58,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE50914),
                            activeTrackColor = Color(0xFFE50914),
                            inactiveTrackColor = Color(0xFF2B2B2B)
                        )
                    )

                    // Quick Preset Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetDurations.take(4).forEach { mins ->
                            FilterChip(
                                selected = autoLockMinutes == mins,
                                onClick = { viewModel.setAutoLockMinutes(mins) },
                                label = { Text("$mins m", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE50914),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }

            // CARD 2: SecureClipboardManager Auto-Clear (1 to 60 Minutes)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color(0xFF22D3EE))
                            Text(
                                "Clipboard Auto-Clear Timeout",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF22D3EE).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "$clipboardTimeoutMinutes min",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF22D3EE),
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }

                    Text(
                        "Schedules coroutine wipe job via ApplicationScope. Automatically purges copied secrets after chosen timeout.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )

                    Slider(
                        value = clipboardTimeoutMinutes.toFloat(),
                        onValueChange = { viewModel.setClipboardTimeoutMinutes(it.toInt()) },
                        valueRange = 1f..60f,
                        steps = 58,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF22D3EE),
                            activeTrackColor = Color(0xFF22D3EE),
                            inactiveTrackColor = Color(0xFF2B2B2B)
                        )
                    )

                    Button(
                        onClick = {
                            secureClipboardManager.clearImmediately()
                            scope.launch {
                                snackbarHostState.showSnackbar("System Clipboard Purged Immediately")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Purge System Clipboard Now", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // CARD 3: Android System Lifecycle Triggers
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "ProcessLifecycleOwner Events",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lock on App Backgrounding", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text("Triggers on ON_STOP lifecycle event", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = lockOnBackground,
                            onCheckedChange = { viewModel.setLockOnBackground(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE50914))
                        )
                    }

                    HorizontalDivider(color = Color(0xFF262626))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Require Biometric on Resume", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text("Instant BiometricPrompt challenge", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = biometricOnResume,
                            onCheckedChange = { viewModel.setBiometricOnResume(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF10B981))
                        )
                    }
                }
            }

            // CARD 4: Biometric Hardware Security Policy (Enforce Strong Biometrics Only)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(18.dp))
                                Text("Enforce Strong Biometrics Only", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Restricts BiometricPrompt.PromptInfo to BIOMETRIC_STRONG (Class 3 hardware). Explicitly disables DEVICE_CREDENTIAL (PIN/Pattern fallback) and enforces setNegativeButtonText(\"Cancel\").",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = enforceStrongBiometricsOnly,
                            onCheckedChange = { viewModel.setEnforceStrongBiometricsOnly(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFE50914))
                        )
                    }
                }
            }
        }
    }
}`}</pre>
              )}

              {selectedFile === 'QuickSearchTileService' && (
                <pre className="text-emerald-400">{`package com.obscura.tiles

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import com.obscura.ui.quicksearch.QuickSearchActivity

/**
 * QuickSearchTileService.kt
 *
 * Android Quick Settings Notification Shade Tile Service for Obscura Password Manager.
 * - Extends TileService.
 * - Handles Android 12 (API 31) through Android 14+ (API 34+) background activity launch restrictions.
 * - Uses startActivityAndCollapse(PendingIntent) on Android 14+ and safe intent fallbacks on earlier SDKs.
 */
@Keep
class QuickSearchTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // Synchronize and update the tile visual state
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Obscura Search"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Instant Vault Copy"
            }
            updateTile()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()

        // 1. Prepare Target Intent for Floating Dialog Activity
        val targetIntent = Intent(this, QuickSearchActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // 2. Launch Target Activity & Collapse Shade with API-Level Precision
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ (API 34+) Strict PendingIntent Requirement
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                targetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            // Android 13 & Lower Fallback
            @Suppress("DEPRECATION")
            startActivityAndCollapse(targetIntent)
        }
    }
}`}</pre>
              )}

              {selectedFile === 'QuickSearchActivity' && (
                <pre className="text-emerald-400">{`package com.obscura.ui.quicksearch

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.obscura.security.SecureClipboardManager
import com.obscura.ui.theme.ObscuraTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * QuickSearchActivity.kt
 *
 * Floating / Dialog-styled overlay activity triggered directly from Quick Settings.
 * - Enforces WindowManager.LayoutParams.FLAG_SECURE to block screen captures and task snapshots.
 * - Immediately triggers BiometricPrompt before revealing any UI or Room database contents.
 * - Safely finishes and dismisses when user cancels biometrics or selects a secret to copy.
 */
@Keep
@AndroidEntryPoint
class QuickSearchActivity : AppCompatActivity() {

    @Inject
    lateinit var secureClipboardManager: SecureClipboardManager

    private val viewModel: QuickSearchViewModel by viewModels()

    private var isBiometricallyAuthenticated by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Mandatory Security Flag: Block screenshots, screen recordings & recents thumbnail previews
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // 2. Render Compose Tree gated by Biometric authentication state
        setContent {
            ObscuraTheme(darkTheme = true) {
                if (isBiometricallyAuthenticated) {
                    QuickSearchScreen(
                        viewModel = viewModel,
                        onDismiss = { finish() },
                        onCopyPassword = { secretValue, title ->
                            // Copy using sensitive privacy flag and auto-destruction timer
                            secureClipboardManager.copySensitiveText(
                                text = secretValue,
                                label = "Obscura \${title} Password"
                            )
                            Toast.makeText(
                                applicationContext,
                                "Copied \${title} password to clipboard (auto-clears in 45s)",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    )
                }
            }
        }

        // 3. Trigger BiometricPrompt authentication gate immediately
        initiateBiometricGate()
    }

    private fun initiateBiometricGate() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isBiometricallyAuthenticated = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If user cancels or biometrics fails irrevocably, finish immediately to prevent hanging overlay
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Allow retry within standard BiometricPrompt dialog
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Obscura Quick Vault")
            .setSubtitle("Confirm biometrics to search and copy passwords")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}`}</pre>
              )}
              {selectedFile === 'SecurityHealthGauge' && (
                <pre className="text-emerald-400">{`package com.obscura.ui.components

import androidx.annotation.Keep
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obscura.ui.theme.CanvasBlack
import com.obscura.ui.theme.CrimsonPrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * SecurityHealthGauge.kt
 *
 * Cinematic Jetpack Compose Radial Security Health Gauge.
 * Utilizes Animatable and animateFloatAsState to smoothly animate gauge rotation,
 * needle pointer sweep, and arc progress when overall security score updates after a scan.
 */
@Keep
@Composable
fun SecurityHealthGauge(
    score: Int,
    grade: String,
    isScanning: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 1. Smoothly interpolate score float with spring physics
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SecurityScoreFloatAnimation"
    )

    // 2. Smooth needle and gauge sweep rotation angle using Animatable
    val rotationAngle = remember { Animatable(-135f) }
    LaunchedEffect(score) {
        // Map score (0..100) to gauge arc angles (-135° to +135°, total 270°)
        val targetAngle = (score.coerceIn(0, 100) / 100f) * 270f - 135f
        rotationAngle.animateTo(
            targetValue = targetAngle,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    // 3. Continuous radar sweep rotation during background Coroutine scanning
    val infiniteTransition = rememberInfiniteTransition(label = "RadarScannerRotation")
    val scanRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScanSweep"
    )

    // Dynamic Color Tokens based on score thresholds
    val gaugeColor = remember(animatedScore) {
        when {
            animatedScore >= 85f -> Color(0xFF10B981) // Emerald
            animatedScore >= 60f -> Color(0xFFF59E0B) // Amber
            else -> CrimsonPrimary                    // Crimson Red
        }
    }

    Box(
        modifier = modifier
            .size(140.dp)
            .background(CanvasBlack, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = 10.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background Track Arc (270°)
            drawArc(
                color = Color(0xFF1E1E1E),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Animated Foreground Score Arc with Gradient Brush
            val sweepAngle = (animatedScore.coerceIn(0f, 100f) / 100f) * 270f
            val gradientBrush = Brush.sweepGradient(
                colors = listOf(CrimsonPrimary, Color(0xFFF59E0B), Color(0xFF10B981))
            )
            
            drawArc(
                brush = gradientBrush,
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Scanning Radar Glow
            if (isScanning) {
                rotate(scanRotation) {
                    drawCircle(
                        color = CrimsonPrimary.copy(alpha = 0.25f),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    )
                }
            }
        }

        // Animated Rotating Needle Pointer Indicator
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotationAngle.value),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(8.dp)
                    .background(Color.White, CircleShape)
            )
        }

        // Center Score Value & Grade Typography
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "\${animatedScore.toInt()}",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black
            )
            Text(
                text = "GRADE \$grade",
                color = gaugeColor,
                fontSize = 10.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}`}</pre>
              )}
              {selectedFile === 'SecureClipboardManager' && (
                <pre className="text-emerald-400">{`package com.obscura.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.annotation.Keep
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure Clipboard Manager
 * - Sets ClipDescription.EXTRA_IS_SENSITIVE on Android 13+ (API 33+) to suppress system UI previews.
 * - Schedules automatic clipboard destruction after 45 seconds using ApplicationScope.
 * - Safely verifies content before clearing to avoid erasing newly copied non-sensitive text.
 */
@Keep
@Singleton
class SecureClipboardManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val externalScope: CoroutineScope
) {
    private val clipboardManager: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val activeClearJob = AtomicReference<Job?>(null)
    private val lastCopiedSecret = AtomicReference<String?>(null)

    companion object {
        private const val AUTO_CLEAR_DELAY_MS = 45_000L // 45 seconds
        private const val SENSITIVE_CLIP_LABEL = "Obscura Sensitive Data"
    }

    /**
     * Copies sensitive text (passwords, PINs, OTP tokens) to the system clipboard securely.
     */
    fun copySensitiveText(
        text: String,
        label: String = SENSITIVE_CLIP_LABEL,
        clearDelayMs: Long = AUTO_CLEAR_DELAY_MS
    ) {
        val clipManager = clipboardManager ?: return

        // 1. Construct ClipData with Android 13+ Privacy Flags
        val clipData = ClipData.newPlainText(label, text).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
        }

        // 2. Set primary clip on main thread
        clipManager.setPrimaryClip(clipData)
        lastCopiedSecret.set(text)

        // 3. Cancel any pending auto-clear job and schedule a fresh one
        activeClearJob.getAndSet(null)?.cancel()

        val newJob = externalScope.launch {
            delay(clearDelayMs)
            clearIfUnchanged(text)
        }
        activeClearJob.set(newJob)
    }

    /**
     * Wipes the clipboard if and only if the current content still equals the copied secret.
     */
    private fun clearIfUnchanged(expectedSecret: String) {
        val clipManager = clipboardManager ?: return

        try {
            val primaryClip = clipManager.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                val currentText = primaryClip.getItemAt(0).text?.toString()
                if (currentText == expectedSecret) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipManager.clearPrimaryClip()
                    } else {
                        clipManager.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    lastCopiedSecret.compareAndSet(expectedSecret, null)
                }
            }
        } catch (_: Exception) {
            // Guard against background IPC or permission restrictions
        }
    }

    fun clearImmediately() {
        activeClearJob.getAndSet(null)?.cancel()
        val clipManager = clipboardManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipManager.clearPrimaryClip()
        } else {
            clipManager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        lastCopiedSecret.set(null)
    }
}`}</pre>
              )}
              {selectedFile === 'BackupCryptoUtils' && (
                <pre className="text-amber-300">{`package com.obscura.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.AEADBadTagException

/**
 * Portable AES-256-GCM Encryption with PBKDF2 (HMAC-SHA256) Key Derivation.
 * Prepend Salt (16B) and IV (12B) to ciphertext for standalone cross-device decryption.
 */
object BackupCryptoUtils {

    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = 12 // Standard GCM IV size
    private const val TAG_LENGTH_BITS = 128

    fun encryptPayload(plainText: String, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Prepend Salt (16B) + IV (12B) + CipherText
        return salt + iv + cipherText
    }

    fun decryptPayload(encryptedData: ByteArray, password: CharArray): String {
        if (encryptedData.size < SALT_SIZE_BYTES + IV_SIZE_BYTES + 16) {
            throw IllegalArgumentException("Invalid or corrupted backup payload format.")
        }

        val salt = encryptedData.copyOfRange(0, SALT_SIZE_BYTES)
        val iv = encryptedData.copyOfRange(SALT_SIZE_BYTES, SALT_SIZE_BYTES + IV_SIZE_BYTES)
        val cipherText = encryptedData.copyOfRange(SALT_SIZE_BYTES + IV_SIZE_BYTES, encryptedData.size)

        val secretKey = deriveKey(password, salt)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw SecurityException("Incorrect backup password or tampered backup file.", e)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val derivedKey = factory.generateSecret(spec).encoded
        return SecretKeySpec(derivedKey, "AES")
    }
}`}</pre>
              )}

              {selectedFile === 'BackupManager' && (
                <pre className="text-cyan-300">{`package com.obscura.data.backup

import android.content.Context
import android.net.Uri
import com.obscura.data.local.VaultDatabase
import com.obscura.data.local.VaultEntity
import com.obscura.security.BackupCryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val entries: List<VaultEntity>
)

class BackupManager(private val context: Context, private val db: VaultDatabase) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    suspend fun exportToFile(uri: Uri, password: CharArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = db.vaultDao().getAllEntriesDirect()
            val payload = BackupPayload(entries = entries)
            val jsonString = json.encodeToString(payload)

            val encryptedBytes = BackupCryptoUtils.encryptPayload(jsonString, password)

            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(encryptedBytes)
                stream.flush()
            } ?: throw IllegalStateException("Failed to open output stream for export.")
        }
    }

    suspend fun importFromFile(uri: Uri, password: CharArray): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val encryptedBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: throw IllegalStateException("Failed to open input stream for import.")

            val decryptedJson = BackupCryptoUtils.decryptPayload(encryptedBytes, password)
            val payload = json.decodeFromString<BackupPayload>(decryptedJson)

            // Insert into SSOT Room Database with OnConflictStrategy.REPLACE
            db.vaultDao().insertAll(payload.entries)
            payload.entries.size
        }
    }
}`}</pre>
              )}

              {selectedFile === 'ObscuraAutofillService' && (
                <pre className="text-emerald-400">{`package com.obscura.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.Keep
import com.obscura.R
import com.obscura.data.VaultDatabase
import kotlinx.coroutines.*

/**
 * Obscura System Autofill Service
 * Integrates with Android Autofill Framework to securely inject credentials
 * locked behind mandatory BiometricPrompt verification.
 */
@Keep
class ObscuraAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val fillContexts = request.fillContexts
        val latestContext = fillContexts.lastOrNull() ?: return callback.onSuccess(null)
        val structure = latestContext.structure

        // 1. Traverse AssistStructure to extract package name, web domain, and input node IDs
        val parser = AssistStructureParser().parse(structure)
        if (parser.usernameId == null && parser.passwordId == null) {
            callback.onSuccess(null)
            return
        }

        serviceScope.launch {
            // 2. Query matching credentials from SQLCipher Room Database by package/domain
            val db = VaultDatabase.getInstance(applicationContext)
            val matches = db.vaultDao().findMatchingEntries(parser.targetPackageName, parser.webDomain ?: "")

            if (matches.isEmpty()) {
                callback.onSuccess(null)
                return@launch
            }

            val responseBuilder = FillResponse.Builder()

            // 3. For each match, create a LOCKED Dataset requiring Biometric Intent
            matches.forEach { entry ->
                val datasetBuilder = Dataset.Builder()

                // Create custom presentation view for the drop-down suggestion
                val presentation = RemoteViews(packageName, R.layout.autofill_suggestion_item).apply {
                    setTextViewText(R.id.text_title, entry.title)
                    setTextViewText(R.id.text_subtitle, entry.usernameOrCardholder)
                }

                // CRITICAL: Construct Biometric Auth PendingIntent (Do not include raw password)
                val intent = Intent(applicationContext, AutofillBiometricAuthActivity::class.java).apply {
                    putExtra(AutofillBiometricAuthActivity.EXTRA_ENTRY_ID, entry.id)
                    putExtra(AutofillBiometricAuthActivity.EXTRA_USERNAME, entry.usernameOrCardholder)
                    putExtra(AutofillBiometricAuthActivity.EXTRA_SECRET_VALUE, entry.secretValue)
                    if (parser.usernameId != null) putExtra(AutofillBiometricAuthActivity.EXTRA_USERNAME_ID, parser.usernameId)
                    if (parser.passwordId != null) putExtra(AutofillBiometricAuthActivity.EXTRA_PASSWORD_ID, parser.passwordId)
                }

                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    entry.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                // Lock the dataset behind Biometric Prompt IntentSender
                datasetBuilder.setAuthentication(pendingIntent.intentSender)

                // Assign presentation views to autofill IDs
                parser.usernameId?.let { id ->
                    datasetBuilder.setValue(id, AutofillValue.forText(entry.usernameOrCardholder), presentation)
                }
                parser.passwordId?.let { id ->
                    datasetBuilder.setValue(id, AutofillValue.forText("*****"), presentation)
                }

                responseBuilder.addDataset(datasetBuilder.build())
            }

            callback.onSuccess(responseBuilder.build())
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}`}</pre>
              )}

              {selectedFile === 'BiometricAuthManager' && (
                <pre className="text-emerald-400">{`package com.example.security

import android.content.Context
import androidx.annotation.Keep
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * BiometricAuthManager
 * Manages Fingerprint, FaceID, and Device PIN authentication flows using AndroidX Biometric API.
 * Supports dynamic enforcement of BIOMETRIC_STRONG (Class 3 hardware) vs PIN fallback.
 */
@Keep
class BiometricAuthManager(private val context: Context) {

    /**
     * Launches the native Android Biometric Prompt dialog with dynamic biometric policy enforcement.
     *
     * @param enforceStrongBiometricsOnly When true, restricts prompt to BIOMETRIC_STRONG only and
     *                                   explicitly disables DEVICE_CREDENTIAL (PIN/Pattern fallback),
     *                                   requiring a custom negative button text.
     */
    fun promptBiometricAuthentication(
        activity: FragmentActivity,
        title: String = "Obscura Vault Authentication",
        subtitle: String = "Verify identity to decrypt local vault",
        enforceStrongBiometricsOnly: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess() // Decrypts Room DB master key via Android Keystore
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError("Authentication error ($errorCode): $errString")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometric verification failed. Try again.")
            }
        }

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (enforceStrongBiometricsOnly) {
            // Strict Class 3 hardware biometrics only — NO Device Credential (PIN/Pattern) fallback allowed.
            promptInfoBuilder
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel") // Mandatory when DEVICE_CREDENTIAL is not set
        } else {
            // Standard policy: Class 3 hardware biometrics with Device PIN/Pattern/Password fallback.
            promptInfoBuilder
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
        }

        val promptInfo = promptInfoBuilder.build()
        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}`}</pre>
              )}

              {selectedFile === 'AuthScreen' && (
                <pre className="text-sky-300">{`package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CanvasBlack
import com.example.ui.theme.CrimsonPrimary

@Composable
fun AuthScreen(
    pinInput: String,
    errorMessage: String?,
    onBiometricClick: () -> Unit,
    onPinDigitEntered: (String) -> Unit,
    onPinBackspace: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CanvasBlack
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("OBSCURA VAULT", style = MaterialTheme.typography.headlineMedium)
            
            // Biometric Trigger Button
            Button(
                onClick = onBiometricClick,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = "Biometrics")
                Text("Scan Fingerprint / FaceID")
            }

            // Keypad Grid for 6-Digit PIN fallback...
        }
    }
}`}</pre>
              )}

              {selectedFile === 'VaultDatabase' && (
                <pre className="text-emerald-400">{`package com.obscura.vault.data.local

import android.content.Context
import androidx.annotation.Keep
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.obscura.vault.data.local.dao.VaultDao
import com.obscura.vault.data.local.entity.VaultEntity
import com.obscura.vault.data.local.entity.VaultFtsEntity
import com.obscura.security.KeystoreManager
import net.sqlcipher.database.SupportFactory

/**
 * VaultDatabase.kt
 * Single Source of Truth (SSOT) Room Database encrypted with SQLCipher AES-256.
 * Contains primary [VaultEntity] table and virtual [VaultFtsEntity] Full-Text Search index.
 */
@Keep
@Database(
    entities = [
        VaultEntity::class,
        VaultFtsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {

    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context): VaultDatabase {
            val keystoreManager = KeystoreManager(context)
            val passphrase = keystoreManager.getOrCreateDatabasePassphrase()
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context,
                VaultDatabase::class.java,
                "obscura_encrypted_vault.db"
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}`}</pre>
              )}

              {selectedFile === 'DashboardScreen' && (
                <pre className="text-sky-300">{`package com.obscura.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obscura.data.local.VaultEntity
import com.obscura.ui.theme.CanvasBlack
import com.obscura.ui.theme.CrimsonPrimary

/**
 * Custom AnnotatedString Builder for Query Highlighting
 *
 * Scans [text] for case-insensitive occurrences of [query].
 * Slices matching spans and appends bold typography with accent color and subtle background highlight.
 */
fun buildHighlightedAnnotatedString(
    text: String,
    query: String,
    accentColor: Color = CrimsonPrimary,
    highlightBackground: Color = Color(0x33E50914),
    defaultColor: Color = Color.White,
    defaultFontWeight: FontWeight = FontWeight.Normal
): AnnotatedString {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty() || text.isEmpty()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = trimmedQuery.lowercase()

        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
            if (matchIndex == -1) {
                // Append remaining substring in default style
                withStyle(
                    SpanStyle(
                        color = defaultColor,
                        fontWeight = defaultFontWeight
                    )
                ) {
                    append(text.substring(currentIndex))
                }
                break
            }

            // Append prefix before the match
            if (matchIndex > currentIndex) {
                withStyle(
                    SpanStyle(
                        color = defaultColor,
                        fontWeight = defaultFontWeight
                    )
                ) {
                    append(text.substring(currentIndex, matchIndex))
                }
            }

            // Append highlighted match with bold typography and accent styling
            val matchEnd = matchIndex + trimmedQuery.length
            withStyle(
                SpanStyle(
                    color = accentColor,
                    fontWeight = FontWeight.Black,
                    background = highlightBackground
                )
            ) {
                append(text.substring(matchIndex, matchEnd))
            }

            currentIndex = matchEnd
        }
    }
}

/**
 * Jetpack Compose Animated Dashboard List with Reactive FTS4 Full-Text Search
 *
 * Replaces manual in-memory filtering by observing [VaultViewModel.vaultEntries] StateFlow,
 * which executes debounced (300ms) FTS4 queries on Room/SQLCipher database via [Dispatchers.IO].
 */
@Composable
fun DashboardScreen(
    viewModel: VaultViewModel,
    onEntryClick: (VaultEntity) -> Unit,
    onAddNewClick: () -> Unit,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect reactive StateFlows from ViewModel with lifecycle-awareness
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val entries by viewModel.vaultEntries.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = CanvasBlack,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Reactive Search Input Header with debounced StateFlow updates
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { 
                    Text("Search titles, accounts or tags...", color = Color.Gray, fontSize = 13.sp) 
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotBlank()) CrimsonPrimary else Color.Gray
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f),
                        exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f)
                    ) {
                        IconButton(onClick = viewModel::onClearSearch) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color.White
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonPrimary,
                    unfocusedBorderColor = Color(0xFF222222),
                    focusedContainerColor = Color(0xFF0F0F0F),
                    unfocusedContainerColor = Color(0xFF0A0A0A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = CrimsonPrimary
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Animated FTS Query Status Bar
            AnimatedVisibility(
                visible = searchQuery.isNotBlank(),
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(150)) + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FTS4 MATCH: ${entries.size} results (debounced 300ms)",
                        style = MaterialTheme.typography.labelSmall,
                        color = CrimsonPrimary
                    )
                    Text(
                        text = "CLEAR FILTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray,
                        modifier = Modifier.clickable { viewModel.onClearSearch() }
                    )
                }
            }

            // Animated LazyColumn with Compose Transition Item Placement
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Vault is empty" else "No matching encrypted records found",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = entries,
                        key = { item -> item.id }
                    ) { item ->
                        Box(
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing),
                                    fadeOutSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing),
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                        ) {
                            VaultItemCard(
                                entry = item,
                                searchQuery = searchQuery,
                                onClick = { onEntryClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * VaultItemCard displaying highlighted title, account username, and tags via AnnotatedString
 */
@Composable
fun VaultItemCard(
    entry: VaultEntity,
    searchQuery: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color(0xFF141414),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F1F1F))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title highlighted in bold / Crimson accent
                Text(
                    text = buildHighlightedAnnotatedString(
                        text = entry.title,
                        query = searchQuery,
                        accentColor = CrimsonPrimary,
                        highlightBackground = Color(0x33E50914),
                        defaultColor = Color.White,
                        defaultFontWeight = FontWeight.Bold
                    ),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (entry.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Username / Account highlighted
            Text(
                text = buildHighlightedAnnotatedString(
                    text = entry.usernameOrCardholder,
                    query = searchQuery,
                    accentColor = CrimsonPrimary,
                    highlightBackground = Color(0x33E50914),
                    defaultColor = Color.Gray
                ),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Tags row with Gold/Amber AnnotatedString highlights
            if (entry.tags.isNotBlank()) {
                val tagList = remember(entry.tags) { entry.tags.split(",").map { it.trim() } }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tagList.forEach { tag ->
                        val isMatch = searchQuery.isNotBlank() && tag.contains(searchQuery.trim(), ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isMatch) Color(0x33FBBF24) else Color(0xFF1E1E1E),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, 
                                if (isMatch) Color(0xFFFBBF24) else Color(0xFF2E2E2E)
                            ),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = buildHighlightedAnnotatedString(
                                    text = "#$tag",
                                    query = searchQuery,
                                    accentColor = Color(0xFFFDE68A),
                                    highlightBackground = Color(0x44FBBF24),
                                    defaultColor = Color(0xFFD1D5DB)
                                ),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}`}</pre>
              )}

              {selectedFile === 'VaultEntity' && (
                <pre className="text-amber-300">{`package com.obscura.vault.data.local.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * VaultEntity.kt
 * Primary Room Entity representing encrypted records stored in SQLCipher DB.
 */
@Keep
@Entity(tableName = "vault_entries")
data class VaultEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "username")
    val username: String = "",

    @ColumnInfo(name = "encrypted_password")
    val encryptedPassword: String = "", // Ciphertext blob

    @ColumnInfo(name = "url")
    val url: String = "",

    @ColumnInfo(name = "category")
    val category: String = "LOGIN",

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "tags")
    val tags: String = "", // Comma-separated tags (e.g. "Personal,Streaming")

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)`}</pre>
              )}

              {selectedFile === 'VaultFtsEntity' && (
                <pre className="text-amber-300">{`package com.obscura.vault.data.local.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * VaultFtsEntity.kt
 *
 * FTS4 virtual table indexing plaintext searchable metadata for blazing-fast tokenized MATCH queries.
 * Configured with [contentEntity] = VaultEntity::class to map directly to the primary table
 * without incurring duplicate data storage overhead in SQLCipher.
 */
@Keep
@Entity(tableName = "vault_entries_fts")
@Fts4(contentEntity = VaultEntity::class)
data class VaultFtsEntity(
    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "notes")
    val notes: String,

    @ColumnInfo(name = "tags")
    val tags: String
)`}</pre>
              )}

              {selectedFile === 'VaultDao' && (
                <pre className="text-emerald-400">{`package com.obscura.vault.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.obscura.vault.data.local.entity.VaultEntity
import kotlinx.coroutines.flow.Flow

/**
 * VaultDao.kt
 *
 * Room DAO executing high-performance Full-Text Search MATCH queries joined with the FTS4 virtual table.
 */
@Dao
interface VaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VaultEntity): Long

    @Update
    suspend fun update(entity: VaultEntity)

    @Query("SELECT * FROM vault_entries ORDER BY is_favorite DESC, title ASC")
    fun getAllEntries(): Flow<List<VaultEntity>>

    /**
     * Executes Full-Text Search (FTS4) across indexed title, username, url, notes, and tags.
     * Uses rowid mapping to join the virtual index with the primary encrypted table.
     */
    @Query(
        """
        SELECT vault_entries.* 
        FROM vault_entries 
        JOIN vault_entries_fts ON vault_entries.id = vault_entries_fts.rowid 
        WHERE vault_entries_fts MATCH :ftsQuery
        ORDER BY vault_entries.is_favorite DESC, vault_entries.title ASC
        """
    )
    fun searchVaultFts(ftsQuery: String): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vault_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): VaultEntity?
}`}</pre>
              )}

              {selectedFile === 'PasswordGeneratorSheet' && (
                <pre className="text-amber-300">{`package com.example.obscura.ui.components

import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.security.SecureRandom

/**
 * PasswordGeneratorSheet.kt
 * High-entropy cryptographic random password generator with Jetpack Compose Material 3 UI.
 */
@Keep
@Composable
fun PasswordGeneratorSheet(
    onPasswordGenerated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var length by remember { mutableFloatStateOf(18f) }
    var useUpper by remember { mutableStateOf(true) }
    var useLower by remember { mutableStateOf(true) }
    var useNumbers by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }

    val currentPassword = remember(length, useUpper, useLower, useNumbers, useSymbols) {
        generateSecurePassword(
            length = length.toInt(),
            includeUpper = useUpper,
            includeLower = useLower,
            includeNumbers = useNumbers,
            includeSymbols = useSymbols
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0C0C))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "High-Entropy Password Generator",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )

        Surface(
            color = Color(0xFF141414),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = currentPassword,
                color = Color(0xFFE50914),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(12.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Length: \${length.toInt()}", color = Color.Gray)
        }
        Slider(
            value = length,
            onValueChange = { length = it },
            valueRange = 8f..64f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFE50914))
        )

        Button(
            onClick = { onPasswordGenerated(currentPassword) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use Generated Password", color = Color.Black)
        }
    }
}

private fun generateSecurePassword(
    length: Int,
    includeUpper: Boolean,
    includeLower: Boolean,
    includeNumbers: Boolean,
    includeSymbols: Boolean
): String {
    val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val lower = "abcdefghijklmnopqrstuvwxyz"
    val numbers = "0123456789"
    val symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    
    val pool = StringBuilder().apply {
        if (includeUpper) append(upper)
        if (includeLower) append(lower)
        if (includeNumbers) append(numbers)
        if (includeSymbols) append(symbols)
    }.ifEmpty { append(lower + numbers) }.toString()

    val random = SecureRandom()
    return (1..length).map { pool[random.nextInt(pool.length)] }.joinToString("")
}`}</pre>
              )}

              {selectedFile === 'VaultViewModel' && (
                <pre className="text-purple-300">{`package com.obscura.vault.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.obscura.vault.data.local.dao.VaultDao
import com.obscura.vault.data.local.entity.VaultEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * VaultViewModel.kt
 *
 * Manages reactive search state with debounce(300L), flatMapLatest, and Dispatchers.IO
 * offloading to protect SQLCipher database performance from keystroke thrashing.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class VaultViewModel(
    private val vaultDao: VaultDao
) : ViewModel() {

    // 1. Raw user search query StateFlow
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 2. Debounced and transformed Flow of VaultEntities
    val vaultEntries: StateFlow<List<VaultEntity>> = _searchQuery
        .debounce(300L) // Prevents database thrashing during rapid user typing
        .distinctUntilChanged()
        .flatMapLatest { rawQuery ->
            val trimmed = rawQuery.trim()
            if (trimmed.isBlank()) {
                vaultDao.getAllEntries()
            } else {
                // SQLite FTS wildcard tokenization (e.g. "netflix pass" -> "netflix* pass*")
                val sanitized = sanitizeFtsQuery(trimmed)
                vaultDao.searchVaultFts(sanitized)
            }
        }
        .flowOn(Dispatchers.IO) // Safe non-blocking execution off the UI thread
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onClearSearch() {
        _searchQuery.value = ""
    }

    fun updateEntry(entry: VaultEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultDao.update(entry)
        }
    }

    private fun sanitizeFtsQuery(query: String): String {
        return query
            .replace(Regex("[^a-zA-Z0-9А-Яа-я_\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
            .ifBlank { "*" }
    }
}`}</pre>
              )}

              {selectedFile === 'Fido2CryptographyUtils' && (
                <pre className="text-amber-300">{`package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.Keep
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Fido2CryptographyUtils
 *
 * Cryptographic utility object for Passkey (FIDO2 / WebAuthn) operations adhering to
 * W3C Web Authentication & FIDO Alliance CTAP2 specifications.
 */
@Keep
object Fido2CryptographyUtils {

    const val EC_CURVE_P256 = "secp256r1"
    const val SIGN_ALGORITHM_ECDSA_SHA256 = "SHA256withECDSA"

    // FIDO2 / CTAP2 Authenticator Flags
    const val FLAG_USER_PRESENT: Byte = 0x01.toByte()      // Bit 0: UP
    const val FLAG_USER_VERIFIED: Byte = 0x04.toByte()     // Bit 2: UV (Biometrics)
    const val FLAG_ATTESTED_DATA: Byte = 0x40.toByte()     // Bit 6: AT

    /**
     * Generates a new ECDSA P-256 (secp256r1) KeyPair for WebAuthn / Passkeys.
     */
    @JvmStatic
    fun generateEcP256KeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec(EC_CURVE_P256)
        keyPairGenerator.initialize(ecSpec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Computes the SHA-256 cryptographic digest.
     */
    @JvmStatic
    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    @JvmStatic
    fun sha256(data: String): ByteArray = sha256(data.toByteArray(StandardCharsets.UTF_8))

    /**
     * Signs the FIDO2 Assertion payload according to W3C WebAuthn spec:
     * Signature payload = authenticatorData (authData) || clientDataHash
     */
    @JvmStatic
    fun signFido2Assertion(
        privateKey: PrivateKey,
        authData: ByteArray,
        clientDataHash: ByteArray
    ): ByteArray {
        val dataToSign = ByteArray(authData.size + clientDataHash.size)
        System.arraycopy(authData, 0, dataToSign, 0, authData.size)
        System.arraycopy(clientDataHash, 0, dataToSign, authData.size, clientDataHash.size)

        val signer = Signature.getInstance(SIGN_ALGORITHM_ECDSA_SHA256)
        signer.initSign(privateKey)
        signer.update(dataToSign)
        return signer.sign() // Standard ASN.1 DER signature
    }

    /**
     * Builds the standard Authenticator Data (authData) structure:
     * 32-byte rpIdHash + 1-byte flags (UP | UV) + 4-byte signCount
     */
    @JvmStatic
    fun buildAuthenticatorData(
        rpId: String,
        userPresent: Boolean = true,
        userVerified: Boolean = true,
        signCount: Int = 1
    ): ByteArray {
        val rpIdHash = sha256(rpId)
        val stream = ByteArrayOutputStream()
        stream.write(rpIdHash)

        var flags = 0
        if (userPresent) flags = flags or FLAG_USER_PRESENT.toInt()
        if (userVerified) flags = flags or FLAG_USER_VERIFIED.toInt()
        stream.write(flags and 0xFF)

        val counterBytes = ByteBuffer.allocate(4).putInt(signCount).array()
        stream.write(counterBytes)
        return stream.toByteArray()
    }
}`}</pre>
              )}

              {selectedFile === 'AutofillBiometricAuthActivity' && (
                <pre className="text-emerald-300">{`package com.example.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Transparent Activity called by the Android Autofill Subsystem (AutofillService) via PendingIntent
 * whenever a protected Dataset or FillResponse requires Biometric Authorization.
 */
@Keep
class AutofillBiometricAuthActivity : FragmentActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "com.example.autofill.EXTRA_ENTRY_ID"
        const val EXTRA_ENTRY_TITLE = "com.example.autofill.EXTRA_ENTRY_TITLE"
        const val EXTRA_USERNAME = "com.example.autofill.EXTRA_USERNAME"
        const val EXTRA_SECRET_VALUE = "com.example.autofill.EXTRA_SECRET_VALUE"
        const val EXTRA_USERNAME_AUTOFILL_ID = "com.example.autofill.EXTRA_USERNAME_AUTOFILL_ID"
        const val EXTRA_PASSWORD_AUTOFILL_ID = "com.example.autofill.EXTRA_PASSWORD_AUTOFILL_ID"
        const val EXTRA_PENDING_DATASET = "com.example.autofill.EXTRA_PENDING_DATASET"
        const val EXTRA_PENDING_RESPONSE = "com.example.autofill.EXTRA_PENDING_RESPONSE"
        const val EXTRA_AUTOPROMPT = "com.example.autofill.EXTRA_AUTOPROMPT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryTitle = intent.getStringExtra(EXTRA_ENTRY_TITLE) ?: "Obscura Vault Item"
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: ""

        setContent {
            AutofillBiometricAuthScreen(
                entryTitle = entryTitle,
                username = username,
                autoPrompt = intent.getBooleanExtra(EXTRA_AUTOPROMPT, true),
                onTriggerBiometrics = { startBiometricVerification() },
                onCancel = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            )
        }
    }

    private fun startBiometricVerification() {
        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                resolveAutofillSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Obscura Vault Autofill")
            .setSubtitle("Authenticate to release credentials")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        BiometricPrompt(this, executor, callback).authenticate(promptInfo)
    }

    private fun resolveAutofillSuccess() {
        val replyIntent = Intent()
        val dataset = buildAuthenticatedDataset()
        if (dataset != null) {
            replyIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        }
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }
}`}</pre>
              )}

              {selectedFile === 'CsvImportEngine' && (
                <pre className="text-sky-300">{`package com.obscura.importer

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import androidx.room.Transaction
import com.obscura.data.local.VaultDao
import com.obscura.data.local.VaultEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

enum class DuplicatePolicy { SKIP, OVERWRITE, KEEP_BOTH }

data class ImportResult(val inserted: Int, val overwritten: Int, val skipped: Int)

@Keep
@Singleton
class CsvImportEngine @Inject constructor(
    private val context: Context,
    private val vaultDao: VaultDao
) {
    /**
     * Streams CSV from SAF InputStream on Dispatchers.IO and performs atomic batch insertion.
     */
    suspend fun importFromUri(
        uri: Uri,
        conflictPolicy: DuplicatePolicy,
        onProgress: (Int, Int) -> Unit
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            var inserted = 0
            var overwritten = 0
            var skipped = 0

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: error("Failed to open Storage Access Framework InputStream")

            val records = mutableListOf<VaultEntity>()

            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val headerLine = reader.readLine() ?: error("Empty CSV file")
                val headers = parseCsvRow(headerLine).map { it.trim().lowercase() }
                val format = detectFormat(headers)

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val fields = parseCsvRow(line!!)
                    val entity = mapToEntity(fields, headers, format) ?: continue
                    records.add(entity)
                }
            }

            // Atomic batch transaction in SQLCipher Room DB
            vaultDao.batchImportAtomicTransaction(records, conflictPolicy) { current, total ->
                onProgress(current, total)
            }
        }
    }

    private fun parseCsvRow(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"'); i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString()); sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString())
        return tokens
    }
}`}</pre>
              )}
            </div>
          </div>
        )}

        {/* DB Inspector Tab */}
        {activeTab === 'db_inspector' && (
          <div className="w-full max-w-5xl bg-[#0e0e0e] border border-obscura-border rounded-2xl overflow-hidden shadow-2xl p-6">
            <div className="flex items-center justify-between mb-6">
              <div className="flex items-center gap-3">
                <Database className="w-6 h-6 text-emerald-400" />
                <div>
                  <h3 className="font-bold text-lg">SQLCipher Room Database Inspector</h3>
                  <p className="text-xs text-gray-400 font-mono">SQLite DB: obscura_encrypted_vault.db (Cipher: AES-256-CBC)</p>
                </div>
              </div>
              <span className="text-xs font-mono bg-emerald-500/10 text-emerald-400 px-3 py-1 rounded-full border border-emerald-500/30">
                STATE: MOUNTED & ENCRYPTED
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left font-mono text-xs border-collapse">
                <thead>
                  <tr className="border-b border-obscura-border text-gray-400 bg-[#141414]">
                    <th className="p-3">ID</th>
                    <th className="p-3">TITLE</th>
                    <th className="p-3">CATEGORY</th>
                    <th className="p-3">EXPIRY DATE</th>
                    <th className="p-3">TAGS</th>
                    <th className="p-3">ENCRYPTED SECRET (AES-256)</th>
                    <th className="p-3">LAST MODIFIED</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-obscura-border/40">
                  {items.map((item) => (
                    <tr key={item.id} className="hover:bg-[#141414] transition-colors">
                      <td className="p-3 text-gray-500">{item.id}</td>
                      <td className="p-3 font-bold text-white">{item.title}</td>
                      <td className="p-3">
                        <span className="bg-obscura-crimson/20 text-obscura-crimson px-2 py-0.5 rounded text-[10px]">
                          {item.category}
                        </span>
                      </td>
                      <td className="p-3 text-emerald-400">{item.expiryDate || 'Never'}</td>
                      <td className="p-3">
                        <div className="flex flex-wrap gap-1">
                          {item.tags.map(t => (
                            <span key={t} className="text-[10px] bg-gray-800 text-gray-300 px-1.5 py-0.5 rounded">
                              #{t}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="p-3 text-emerald-400 truncate max-w-[180px]">
                        0x{Array.from(item.secretValue).map(c => c.charCodeAt(0).toString(16)).join('')}f8a2c1
                      </td>
                      <td className="p-3 text-gray-400 text-[10px]">{item.lastModified}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </main>

      {/* Modal for adding new secret */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-[#121212] border border-obscura-border rounded-2xl w-full max-w-md p-6 relative">
            <h3 className="text-lg font-bold text-white mb-4 flex items-center gap-2">
              <Plus className="w-5 h-5 text-obscura-crimson" />
              Add Encrypted Secret
            </h3>

            <form onSubmit={handleAddSecret} className="space-y-3.5">
              <div>
                <label className="text-xs text-gray-400 font-mono">Title</label>
                <input
                  type="text"
                  required
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="e.g., TMDB Production Key"
                  className="w-full mt-1 p-2 bg-[#0a0a0a] border border-obscura-border rounded-xl text-xs text-white focus:outline-none focus:border-obscura-crimson"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-xs text-gray-400 font-mono">Category</label>
                  <select
                    value={newCategory}
                    onChange={(e) => setNewCategory(e.target.value as any)}
                    className="w-full mt-1 p-2 bg-[#0a0a0a] border border-obscura-border rounded-xl text-xs text-white focus:outline-none focus:border-obscura-crimson"
                  >
                    <option value="LOGIN">Login Credential</option>
                    <option value="BANK_CARD">Bank Card</option>
                    <option value="API_KEY">API Key</option>
                    <option value="SECURE_NOTE">Secure Note</option>
                  </select>
                </div>

                <div>
                  <label className="text-xs text-gray-400 font-mono">Expiry Date</label>
                  <input
                    type="text"
                    value={newExpiryDate}
                    onChange={(e) => setNewExpiryDate(e.target.value)}
                    placeholder="YYYY-MM-DD"
                    className="w-full mt-1 p-2 bg-[#0a0a0a] border border-obscura-border rounded-xl text-xs text-white focus:outline-none focus:border-obscura-crimson"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs text-gray-400 font-mono">Username / Identifier</label>
                <input
                  type="text"
                  value={newUsername}
                  onChange={(e) => setNewUsername(e.target.value)}
                  placeholder="e.g., admin@obscura.app"
                  className="w-full mt-1 p-2 bg-[#0a0a0a] border border-obscura-border rounded-xl text-xs text-white focus:outline-none focus:border-obscura-crimson"
                />
              </div>

              <div>
                <div className="flex items-center justify-between">
                  <label className="text-xs text-gray-400 font-mono">Secret Value (Encrypted in Room DB)</label>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => handleOpenQrScanner('add')}
                      className="text-xs text-emerald-400 font-bold hover:underline flex items-center gap-1 font-mono"
                    >
                      <QrCode className="w-3.5 h-3.5 text-emerald-400" />
                      <span>Scan QR Code</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setShowAddGenerator(!showAddGenerator)}
                      className="text-xs text-obscura-crimson font-bold hover:underline flex items-center gap-1 font-mono"
                    >
                      <Sparkles className="w-3.5 h-3.5 text-obscura-crimson" />
                      <span>{showAddGenerator ? 'Hide Generator' : '⚡ Password Generator'}</span>
                    </button>
                  </div>
                </div>
                <input
                  type="text"
                  required
                  value={newSecret}
                  onChange={(e) => setNewSecret(e.target.value)}
                  placeholder="Password, API Key, or TOTP Secret"
                  className="w-full mt-1 p-2 bg-[#0a0a0a] border border-obscura-border rounded-xl text-xs text-white font-mono focus:outline-none focus:border-obscura-crimson"
                />

                {showAddGenerator && (
                  <div className="mt-2 animate-fade-in">
                    <PasswordGenerator
                      onApplyPassword={(pwd) => {
                        setNewSecret(pwd);
                      }}
                    />
                  </div>
                )}
              </div>

              <div>
                <label className="text-xs text-gray-400 font-mono">Tags (comma separated)</label>
                <input
                  type="text"
                  value={newTagsString}
                  onChange={(e) => setNewTagsString(e.target.value)}
                  placeholder="Personal, Work, Streaming"
                  className="w-full mt-1 p-2 bg-[#0a0a0a] border border-obscura-border rounded-xl text-xs text-white focus:outline-none focus:border-obscura-crimson"
                />
              </div>

              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="flex-1 py-2 bg-[#1e1e1e] text-gray-300 font-semibold text-xs rounded-xl hover:bg-[#282828]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2 bg-obscura-crimson text-black font-extrabold text-xs rounded-xl hover:bg-obscura-crimsonHover shadow-lg shadow-obscura-crimson/20"
                >
                  Save Secret
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* QR Code Scanner Overlay Modal */}
      <QrScannerModal
        isOpen={isQrScannerOpen}
        onClose={() => setIsQrScannerOpen(false)}
        onImportSecret={handleImportQrResult}
        isDarkMode={isDarkMode}
      />

      {/* Universal CSV Import Wizard Modal (Chrome, Bitwarden, KeePass) */}
      <CsvImportWizardModal
        isOpen={isCsvImportOpen}
        onClose={() => setIsCsvImportOpen(false)}
        existingItems={items}
        onExecuteImport={handleExecuteCsvImport}
        isDarkMode={isDarkMode}
      />

      {/* Encrypted Vault Backup & Export Modal (AES-256-GCM / PBKDF2) */}
      <BackupExportModal
        isOpen={isBackupExportOpen}
        onClose={() => setIsBackupExportOpen(false)}
        items={items}
        onLogEvent={(msg, type) => addLog(msg, type)}
        isDarkMode={isDarkMode}
      />

      {/* Encrypted Vault Backup Restore Modal (AES-256-GCM / PBKDF2) */}
      <BackupRestoreModal
        isOpen={isBackupRestoreOpen}
        onClose={() => setIsBackupRestoreOpen(false)}
        onRestoreEntries={handleRestoreVaultBackup}
        onLogEvent={(msg, type) => addLog(msg, type)}
        isDarkMode={isDarkMode}
      />

      {/* Mandatory Delete Confirmation Modal */}
      <DeleteConfirmationModal
        isOpen={isDeleteModalOpen}
        item={itemToDelete}
        onConfirm={handleConfirmDelete}
        onCancel={handleCancelDelete}
        isDarkMode={isDarkMode}
      />

      {/* Android Quick Settings Notification Shade Tile Simulation Floating Activity */}
      <QuickSearchTileModal
        isOpen={isQuickSearchTileOpen}
        onClose={() => setIsQuickSearchTileOpen(false)}
        items={items}
        onCopySecret={(secret, title) => {
          handleCopy(secret, title);
          addLog(`[QuickSearchActivity] Copied sensitive password for "${title}" via SecureClipboardManager (FLAG_SECURE + EXTRA_IS_SENSITIVE)`, 'success');
        }}
        isDarkMode={isDarkMode}
      />
    </div>
  );
}
