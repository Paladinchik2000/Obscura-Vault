import React, { useState, useEffect, useRef } from 'react';
import { QrCode, Camera, Upload, X, Check, Flashlight, RefreshCw, AlertCircle, Key, User, FileText, Sparkles, Zap, ShieldCheck } from 'lucide-react';
import jsQR from 'jsqr';

export interface QrScanResult {
  rawResult: string;
  secretValue: string;
  parsedTitle?: string;
  parsedUsername?: string;
  parsedIssuer?: string;
  parsedCategory?: 'LOGIN' | 'BANK_CARD' | 'API_KEY' | 'SECURE_NOTE';
  isTotp: boolean;
}

export function parseQrCodeData(data: string): QrScanResult {
  const trimmed = data.trim();

  // 1. Check for TOTP URI format (otpauth://totp/...)
  if (trimmed.startsWith('otpauth://')) {
    try {
      const url = new URL(trimmed);
      const pathname = decodeURIComponent(url.pathname.replace(/^\/\//, '').replace(/^\//, '')); // e.g. "totp/Google:user@gmail.com" or "Google:user@gmail.com"
      const params = url.searchParams;

      const secretParam = params.get('secret') || '';
      const issuerParam = params.get('issuer') || '';

      // Parse label (e.g., "Google:user@gmail.com" or "Obscura")
      let labelPart = pathname.replace(/^totp\//, '').replace(/^hotp\//, '');
      let title = issuerParam;
      let username = '';

      if (labelPart.includes(':')) {
        const parts = labelPart.split(':');
        if (!title) title = parts[0].trim();
        username = parts[1].trim();
      } else if (labelPart) {
        if (!title) title = labelPart.trim();
      }

      if (!title) title = 'TOTP Authenticator Key';

      return {
        rawResult: trimmed,
        secretValue: secretParam || trimmed,
        parsedTitle: title,
        parsedUsername: username,
        parsedIssuer: issuerParam || title,
        parsedCategory: 'LOGIN',
        isTotp: true
      };
    } catch {
      // Fallback regex parsing for non-standard TOTP URIs
      const secretMatch = trimmed.match(/secret=([A-Z0-9]+)/i);
      const issuerMatch = trimmed.match(/issuer=([^&]+)/i);

      return {
        rawResult: trimmed,
        secretValue: secretMatch ? secretMatch[1] : trimmed,
        parsedTitle: issuerMatch ? decodeURIComponent(issuerMatch[1]) : 'TOTP Key',
        parsedUsername: '',
        parsedIssuer: issuerMatch ? decodeURIComponent(issuerMatch[1]) : '',
        parsedCategory: 'LOGIN',
        isTotp: true
      };
    }
  }

  // 2. Check for JSON format credentials
  if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
    try {
      const obj = JSON.parse(trimmed);
      return {
        rawResult: trimmed,
        secretValue: obj.secret || obj.password || obj.key || trimmed,
        parsedTitle: obj.title || obj.name || obj.issuer || 'Scanned JSON Key',
        parsedUsername: obj.username || obj.user || obj.email || '',
        parsedCategory: obj.category || 'LOGIN',
        isTotp: false
      };
    } catch {
      // ignore json parse error
    }
  }

  // 3. Plain text / API key / Secret password
  return {
    rawResult: trimmed,
    secretValue: trimmed,
    parsedTitle: trimmed.length > 25 ? 'Imported Secret Key' : undefined,
    isTotp: false
  };
}

interface QrScannerModalProps {
  isOpen: boolean;
  onClose: () => void;
  onImportSecret: (result: QrScanResult) => void;
  isDarkMode?: boolean;
}

export const QrScannerModal: React.FC<QrScannerModalProps> = ({
  isOpen,
  onClose,
  onImportSecret,
  isDarkMode = true
}) => {
  const [activeTab, setActiveTab] = useState<'camera' | 'upload' | 'preset'>('camera');
  const [cameraFacing, setCameraFacing] = useState<'environment' | 'user'>('environment');
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [isCameraActive, setIsCameraActive] = useState<boolean>(false);
  const [scanResult, setScanResult] = useState<QrScanResult | null>(null);
  const [torchEnabled, setTorchEnabled] = useState<boolean>(false);
  const [isProcessingFile, setIsProcessingFile] = useState<boolean>(false);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const animationFrameId = useRef<number | null>(null);

  // Initialize camera stream
  useEffect(() => {
    if (!isOpen || activeTab !== 'camera') {
      stopCamera();
      return;
    }

    let stream: MediaStream | null = null;

    async function startCamera() {
      setCameraError(null);
      setIsCameraActive(false);

      try {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          throw new Error('Camera access API is not supported in this environment.');
        }

        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: cameraFacing, width: { ideal: 1280 }, height: { ideal: 720 } }
        });

        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.setAttribute('playsinline', 'true'); // required for iOS
          await videoRef.current.play();
          setIsCameraActive(true);
          requestScanFrame();
        }
      } catch (err: any) {
        console.warn('Camera stream error:', err);
        setCameraError(err.message || 'Camera permission denied or camera not found.');
        setIsCameraActive(false);
      }
    }

    startCamera();

    return () => {
      stopCamera();
      if (stream) {
        stream.getTracks().forEach(track => track.stop());
      }
    };
  }, [isOpen, activeTab, cameraFacing]);

  const stopCamera = () => {
    if (animationFrameId.current) {
      cancelAnimationFrame(animationFrameId.current);
      animationFrameId.current = null;
    }

    if (videoRef.current && videoRef.current.srcObject) {
      const stream = videoRef.current.srcObject as MediaStream;
      stream.getTracks().forEach(track => track.stop());
      videoRef.current.srcObject = null;
    }
    setIsCameraActive(false);
  };

  const requestScanFrame = () => {
    if (!videoRef.current || videoRef.current.readyState !== videoRef.current.HAVE_ENOUGH_DATA) {
      animationFrameId.current = requestAnimationFrame(requestScanFrame);
      return;
    }

    const video = videoRef.current;
    const canvas = canvasRef.current || document.createElement('canvas');
    const ctx = canvas.getContext('2d');

    if (ctx) {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
      const code = jsQR(imageData.data, imageData.width, imageData.height, {
        inversionAttempts: 'attemptBoth'
      });

      if (code && code.data) {
        const parsed = parseQrCodeData(code.data);
        setScanResult(parsed);
        stopCamera();
        return;
      }
    }

    animationFrameId.current = requestAnimationFrame(requestScanFrame);
  };

  // Upload file scanner
  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsProcessingFile(true);
    setCameraError(null);

    const reader = new FileReader();
    reader.onload = (event) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = img.width;
        canvas.height = img.height;
        const ctx = canvas.getContext('2d');
        if (ctx) {
          ctx.drawImage(img, 0, 0);
          const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
          const code = jsQR(imageData.data, imageData.width, imageData.height, {
            inversionAttempts: 'attemptBoth'
          });

          if (code && code.data) {
            const parsed = parseQrCodeData(code.data);
            setScanResult(parsed);
          } else {
            setCameraError('No valid QR code detected in uploaded image.');
          }
        }
        setIsProcessingFile(false);
      };
      img.onerror = () => {
        setCameraError('Failed to read image file.');
        setIsProcessingFile(false);
      };
      img.src = event.target?.result as string;
    };
    reader.readAsDataURL(file);
  };

  const handleApplyPreset = (qrCodeString: string) => {
    const parsed = parseQrCodeData(qrCodeString);
    setScanResult(parsed);
  };

  const handleConfirmImport = () => {
    if (scanResult) {
      onImportSecret(scanResult);
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/85 backdrop-blur-md flex items-center justify-center p-3 z-50 animate-fade-in font-sans">
      <div className={`w-full max-w-md rounded-2xl border shadow-2xl flex flex-col overflow-hidden relative ${
        isDarkMode ? 'bg-[#121212] border-obscura-border text-white' : 'bg-white border-slate-200 text-slate-900'
      }`}>
        {/* Modal Header */}
        <div className={`p-3.5 border-b flex items-center justify-between ${
          isDarkMode ? 'bg-[#0a0a0a] border-obscura-border' : 'bg-slate-50 border-slate-200'
        }`}>
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-obscura-crimson/20 border border-obscura-crimson/40 text-obscura-crimson">
              <QrCode className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-xs font-extrabold tracking-wide uppercase font-mono">Obscura QR Code Scanner</h3>
              <p className="text-[10px] text-gray-400 font-mono">Scan TOTP 2FA URIs or Plain Credentials</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className={`p-1 rounded-lg border transition-all ${
              isDarkMode ? 'bg-[#181818] border-obscura-border text-gray-400 hover:text-white' : 'bg-slate-100 border-slate-200 text-slate-500 hover:text-slate-900'
            }`}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Mode Switcher Tabs */}
        <div className={`flex items-center p-1 border-b text-[11px] font-mono font-bold ${
          isDarkMode ? 'bg-[#161616] border-obscura-border' : 'bg-slate-100 border-slate-200'
        }`}>
          <button
            onClick={() => {
              setActiveTab('camera');
              setScanResult(null);
            }}
            className={`flex-1 py-1.5 rounded-lg flex items-center justify-center gap-1.5 transition-all ${
              activeTab === 'camera'
                ? 'bg-obscura-crimson text-black font-extrabold shadow-sm'
                : isDarkMode ? 'text-gray-400 hover:text-white' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Camera className="w-3.5 h-3.5" />
            <span>Live Camera</span>
          </button>

          <button
            onClick={() => {
              setActiveTab('upload');
              setScanResult(null);
            }}
            className={`flex-1 py-1.5 rounded-lg flex items-center justify-center gap-1.5 transition-all ${
              activeTab === 'upload'
                ? 'bg-obscura-crimson text-black font-extrabold shadow-sm'
                : isDarkMode ? 'text-gray-400 hover:text-white' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Upload className="w-3.5 h-3.5" />
            <span>Upload Image</span>
          </button>

          <button
            onClick={() => {
              setActiveTab('preset');
              setScanResult(null);
            }}
            className={`flex-1 py-1.5 rounded-lg flex items-center justify-center gap-1.5 transition-all ${
              activeTab === 'preset'
                ? 'bg-obscura-crimson text-black font-extrabold shadow-sm'
                : isDarkMode ? 'text-gray-400 hover:text-white' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>Test Presets</span>
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-4 space-y-3">
          {/* TAB 1: LIVE CAMERA SCANNER */}
          {activeTab === 'camera' && !scanResult && (
            <div className="space-y-3">
              <div className="relative w-full h-56 bg-black rounded-xl overflow-hidden border border-obscura-border flex items-center justify-center shadow-inner group">
                <video
                  ref={videoRef}
                  className="w-full h-full object-cover"
                />
                <canvas ref={canvasRef} className="hidden" />

                {/* Simulated Flash Overlay */}
                {torchEnabled && (
                  <div className="absolute inset-0 bg-amber-100/10 pointer-events-none" />
                )}

                {/* Target Frame Reticle */}
                <div className="absolute inset-8 border-2 border-dashed border-obscura-crimson/70 rounded-2xl pointer-events-none flex items-center justify-center">
                  <div className="w-full h-0.5 bg-obscura-crimson shadow-[0_0_8px_#E50914] animate-pulse" />
                  <div className="absolute top-2 left-2 w-4 h-4 border-t-2 border-l-2 border-obscura-crimson" />
                  <div className="absolute top-2 right-2 w-4 h-4 border-t-2 border-r-2 border-obscura-crimson" />
                  <div className="absolute bottom-2 left-2 w-4 h-4 border-b-2 border-l-2 border-obscura-crimson" />
                  <div className="absolute bottom-2 right-2 w-4 h-4 border-b-2 border-r-2 border-obscura-crimson" />
                </div>

                {/* Camera Control Toolbar */}
                <div className="absolute bottom-2 left-2 right-2 flex items-center justify-between bg-black/60 backdrop-blur-md p-1.5 rounded-xl border border-white/10 text-[10px] font-mono">
                  <button
                    onClick={() => setCameraFacing(prev => prev === 'environment' ? 'user' : 'environment')}
                    className="px-2 py-1 bg-white/10 hover:bg-white/20 rounded-lg flex items-center gap-1 text-white font-bold"
                  >
                    <RefreshCw className="w-3 h-3 text-obscura-crimson" />
                    <span>Flip Lens ({cameraFacing === 'environment' ? 'Back' : 'Front'})</span>
                  </button>

                  <button
                    onClick={() => setTorchEnabled(!torchEnabled)}
                    className={`px-2 py-1 rounded-lg flex items-center gap-1 font-bold ${
                      torchEnabled ? 'bg-amber-400 text-black' : 'bg-white/10 text-white'
                    }`}
                  >
                    <Flashlight className="w-3 h-3" />
                    <span>{torchEnabled ? 'Flash ON' : 'Flash OFF'}</span>
                  </button>
                </div>
              </div>

              {cameraError && (
                <div className="p-2.5 rounded-xl bg-red-950/40 border border-red-500/50 text-red-300 text-xs font-mono space-y-1">
                  <div className="flex items-center gap-1.5 font-bold">
                    <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
                    <span>Camera Permission / Hardware Notice</span>
                  </div>
                  <p className="text-[10px] text-red-200/80 leading-relaxed">
                    {cameraError} You can also upload a QR screenshot file or use pre-configured test TOTP keys below.
                  </p>
                </div>
              )}
            </div>
          )}

          {/* TAB 2: UPLOAD IMAGE SCANNER */}
          {activeTab === 'upload' && !scanResult && (
            <div className="space-y-3">
              <label className={`border-2 border-dashed rounded-2xl p-6 flex flex-col items-center justify-center gap-2 cursor-pointer transition-all ${
                isDarkMode ? 'border-obscura-border bg-[#0a0a0a] hover:border-obscura-crimson' : 'bg-slate-50 border-slate-300 hover:border-red-500'
              }`}>
                <Upload className="w-8 h-8 text-obscura-crimson animate-bounce" />
                <div className="text-center">
                  <p className="text-xs font-bold">Select or Drop QR Code Image</p>
                  <p className="text-[10px] text-gray-400 font-mono mt-0.5">Supports PNG, JPG, WEBP screenshots of 2FA TOTP codes</p>
                </div>
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleFileUpload}
                  className="hidden"
                />
              </label>

              {isProcessingFile && (
                <div className="p-2.5 bg-obscura-crimson/10 border border-obscura-crimson/30 rounded-xl text-xs text-obscura-crimson font-mono flex items-center justify-center gap-2">
                  <RefreshCw className="w-4 h-4 animate-spin" />
                  <span>Decoding QR matrix pixels via jsQR...</span>
                </div>
              )}

              {cameraError && (
                <div className="p-2.5 rounded-xl bg-red-950/40 border border-red-500/50 text-red-300 text-xs font-mono flex items-center gap-1.5">
                  <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
                  <span>{cameraError}</span>
                </div>
              )}
            </div>
          )}

          {/* TAB 3: DEMO TEST PRESETS */}
          {activeTab === 'preset' && !scanResult && (
            <div className="space-y-2">
              <p className="text-[11px] text-gray-400 font-mono">
                Click any standard TOTP 2FA QR code preset to test auto-extraction:
              </p>

              <div className="space-y-1.5 max-h-56 overflow-y-auto">
                {[
                  {
                    title: 'GitHub 2FA Authenticator Key',
                    user: 'obscura_developer',
                    code: 'otpauth://totp/GitHub:obscura_developer?secret=HXDM4V2BN8P9QZ1K&issuer=GitHub',
                    icon: 'octocat'
                  },
                  {
                    title: 'Google Account TOTP Key',
                    user: 'peta45577@gmail.com',
                    code: 'otpauth://totp/Google:peta45577@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google',
                    icon: 'google'
                  },
                  {
                    title: 'AWS Production IAM Key',
                    user: 'admin@obscura.app',
                    code: 'otpauth://totp/AmazonWebServices:admin@obscura.app?secret=KVKFK3S3MZ2T2222&issuer=AmazonWebServices',
                    icon: 'aws'
                  },
                  {
                    title: 'Plain API Production Secret',
                    user: 'Live API Key',
                    code: 'sec_live_9f8a3b2c1d0e4f5a6b7c8d9e0f1a2b3c',
                    icon: 'key'
                  },
                  {
                    title: 'JSON Encrypted Credential',
                    user: 'Coinbase Pro',
                    code: '{"title":"Coinbase 2FA","username":"crypto_trader","secret":"B37F8912A5CDE410","category":"API_KEY"}',
                    icon: 'json'
                  }
                ].map((item, idx) => (
                  <button
                    key={idx}
                    onClick={() => handleApplyPreset(item.code)}
                    className={`w-full p-2.5 border rounded-xl text-left transition-all flex items-center justify-between ${
                      isDarkMode 
                        ? 'bg-[#181818] border-obscura-border hover:border-obscura-crimson hover:bg-[#202020]' 
                        : 'bg-slate-50 border-slate-200 hover:border-red-500 hover:bg-slate-100'
                    }`}
                  >
                    <div>
                      <div className="flex items-center gap-1.5">
                        <span className="text-xs font-bold text-white">{item.title}</span>
                        {item.code.startsWith('otpauth://') && (
                          <span className="text-[9px] bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 px-1.5 py-0.2 rounded font-mono font-bold">
                            TOTP 2FA
                          </span>
                        )}
                      </div>
                      <p className="text-[10px] text-gray-400 font-mono truncate max-w-[260px]">{item.user}</p>
                    </div>
                    <Zap className="w-4 h-4 text-obscura-crimson shrink-0" />
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* SUCCESSFUL PARSED SCAN RESULT DISPLAY */}
          {scanResult && (
            <div className="space-y-3 animate-fade-in">
              <div className="p-3 bg-emerald-950/40 border border-emerald-500/50 rounded-xl space-y-2 text-xs">
                <div className="flex items-center justify-between text-emerald-400 font-bold font-mono">
                  <span className="flex items-center gap-1">
                    <ShieldCheck className="w-4 h-4 text-emerald-400" />
                    <span>{scanResult.isTotp ? 'TOTP 2FA Key Extracted!' : 'QR Code Decoded Successfully'}</span>
                  </span>
                  <span className="text-[9px] bg-emerald-500/20 px-2 py-0.5 rounded-full border border-emerald-500/40">
                    AES-256 READY
                  </span>
                </div>

                {/* Parsed Fields Summary */}
                <div className="space-y-1.5 pt-1 font-mono text-[11px] text-gray-200">
                  {scanResult.parsedTitle && (
                    <div className="flex items-center gap-1.5">
                      <FileText className="w-3.5 h-3.5 text-obscura-crimson shrink-0" />
                      <span className="text-gray-400">Title:</span>
                      <strong className="text-white font-bold">{scanResult.parsedTitle}</strong>
                    </div>
                  )}

                  {scanResult.parsedUsername && (
                    <div className="flex items-center gap-1.5">
                      <User className="w-3.5 h-3.5 text-obscura-crimson shrink-0" />
                      <span className="text-gray-400">Username/Account:</span>
                      <strong className="text-white">{scanResult.parsedUsername}</strong>
                    </div>
                  )}

                  <div className="flex items-start gap-1.5 bg-[#0a0a0a] p-2 rounded-lg border border-obscura-border">
                    <Key className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-0.5" />
                    <div className="overflow-hidden">
                      <span className="text-gray-400 text-[10px] block">Extracted Secret Value:</span>
                      <strong className="text-emerald-300 font-mono text-xs break-all block">{scanResult.secretValue}</strong>
                    </div>
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex gap-2">
                <button
                  onClick={() => setScanResult(null)}
                  className="flex-1 py-2 bg-[#222] hover:bg-[#333] text-gray-300 text-xs font-bold rounded-xl font-mono flex items-center justify-center gap-1"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  <span>Rescan QR</span>
                </button>

                <button
                  onClick={handleConfirmImport}
                  className="flex-1 py-2 bg-obscura-crimson hover:bg-obscura-crimsonHover text-black text-xs font-extrabold rounded-xl font-mono flex items-center justify-center gap-1.5 shadow-lg shadow-obscura-crimson/20"
                >
                  <Check className="w-4 h-4" />
                  <span>IMPORT TO SECRET FIELD</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
