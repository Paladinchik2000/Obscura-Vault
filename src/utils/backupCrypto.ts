/**
 * Backup Crypto Utilities
 *
 * Implements AES-256-GCM encryption with PBKDF2 (HMAC-SHA256) key derivation (120,000 iterations),
 * matching com.example.security.BackupCryptoUtils on Android.
 */

export interface EncryptedBackupContainer {
  version: number;
  format: 'OBSCURA_ENCRYPTED_VAULT_BACKUP';
  app: 'Obscura Vault';
  encryption: {
    cipher: 'AES-256-GCM';
    kdf: 'PBKDF2WithHmacSHA256';
    iterations: number;
    keyLength: number;
    tagLength: number;
  };
  salt: string; // Base64 16 bytes
  iv: string; // Base64 12 bytes
  ciphertext: string; // Base64 AES-GCM ciphertext + 16-byte tag
  combinedPayloadBase64: string; // [Salt 16B] + [IV 12B] + [Ciphertext] (matches Android BackupCryptoUtils binary byte array)
  itemCount: number;
  timestamp: number;
  dateIso: string;
  checksumSha256: string;
}

export const PBKDF2_ITERATIONS = 120_000;
export const SALT_SIZE_BYTES = 16;
export const IV_SIZE_BYTES = 12; // 96-bit GCM IV
export const TAG_LENGTH_BITS = 128;

function arrayBufferToBase64(buffer: ArrayBuffer | Uint8Array): string {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function base64ToArrayBuffer(base64: string): Uint8Array {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

async function computeSha256Hex(data: Uint8Array): Promise<string> {
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Encrypts raw plaintext using AES-256-GCM and PBKDF2 key derivation.
 */
export async function encryptVaultPayload(
  plainText: string,
  password: string,
  itemCount: number
): Promise<EncryptedBackupContainer> {
  if (!password || password.length === 0) {
    throw new Error('Backup password cannot be empty.');
  }

  // 1. Generate cryptographically secure random Salt (16B) and IV (12B)
  const salt = new Uint8Array(SALT_SIZE_BYTES);
  crypto.getRandomValues(salt);

  const iv = new Uint8Array(IV_SIZE_BYTES);
  crypto.getRandomValues(iv);

  // 2. Import password as base key for PBKDF2
  const encoder = new TextEncoder();
  const passwordKey = await crypto.subtle.importKey(
    'raw',
    encoder.encode(password),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  // 3. Derive 256-bit AES-GCM Key with 120,000 iterations of HMAC-SHA256
  const aesKey = await crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt,
      iterations: PBKDF2_ITERATIONS,
      hash: 'SHA-256'
    },
    passwordKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt']
  );

  // 4. Encrypt plaintext JSON with AES-256-GCM
  const plainBytes = encoder.encode(plainText);
  const cipherBuffer = await crypto.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv,
      tagLength: TAG_LENGTH_BITS
    },
    aesKey,
    plainBytes
  );

  const cipherBytes = new Uint8Array(cipherBuffer);

  // 5. Combine [Salt 16B] + [IV 12B] + [Ciphertext] (matching Android BackupCryptoUtils)
  const combinedBytes = new Uint8Array(salt.length + iv.length + cipherBytes.length);
  combinedBytes.set(salt, 0);
  combinedBytes.set(iv, salt.length);
  combinedBytes.set(cipherBytes, salt.length + iv.length);

  const checksum = await computeSha256Hex(combinedBytes);
  const now = Date.now();

  return {
    version: 2,
    format: 'OBSCURA_ENCRYPTED_VAULT_BACKUP',
    app: 'Obscura Vault',
    encryption: {
      cipher: 'AES-256-GCM',
      kdf: 'PBKDF2WithHmacSHA256',
      iterations: PBKDF2_ITERATIONS,
      keyLength: 256,
      tagLength: TAG_LENGTH_BITS
    },
    salt: arrayBufferToBase64(salt),
    iv: arrayBufferToBase64(iv),
    ciphertext: arrayBufferToBase64(cipherBytes),
    combinedPayloadBase64: arrayBufferToBase64(combinedBytes),
    itemCount,
    timestamp: now,
    dateIso: new Date(now).toISOString(),
    checksumSha256: checksum
  };
}

/**
 * Decrypts an encrypted backup container or raw combined base64/binary string using the password.
 */
export async function decryptVaultPayload(
  payloadOrJson: string | EncryptedBackupContainer,
  password: string
): Promise<{ plainText: string; metadata: any }> {
  if (!password) {
    throw new Error('Password is required for decryption.');
  }

  let salt: Uint8Array;
  let iv: Uint8Array;
  let cipherBytes: Uint8Array;
  let metadata: any = {};

  if (typeof payloadOrJson === 'object' && payloadOrJson.ciphertext) {
    // Encrypted JSON container format
    salt = base64ToArrayBuffer(payloadOrJson.salt);
    iv = base64ToArrayBuffer(payloadOrJson.iv);
    cipherBytes = base64ToArrayBuffer(payloadOrJson.ciphertext);
    metadata = { ...payloadOrJson };
  } else {
    // String - could be JSON or raw Base64 combined payload
    let parsed: any;
    try {
      parsed = JSON.parse(payloadOrJson as string);
    } catch {
      parsed = null;
    }

    if (parsed && parsed.salt && parsed.ciphertext) {
      salt = base64ToArrayBuffer(parsed.salt);
      iv = base64ToArrayBuffer(parsed.iv);
      cipherBytes = base64ToArrayBuffer(parsed.ciphertext);
      metadata = parsed;
    } else {
      // Treat as raw base64 string containing [Salt 16B] + [IV 12B] + [Ciphertext]
      const combined = base64ToArrayBuffer((payloadOrJson as string).trim());
      if (combined.length < SALT_SIZE_BYTES + IV_SIZE_BYTES + 16) {
        throw new Error('Invalid or corrupted backup payload file format.');
      }
      salt = combined.slice(0, SALT_SIZE_BYTES);
      iv = combined.slice(SALT_SIZE_BYTES, SALT_SIZE_BYTES + IV_SIZE_BYTES);
      cipherBytes = combined.slice(SALT_SIZE_BYTES + IV_SIZE_BYTES);
      metadata = { rawPayloadSize: combined.length };
    }
  }

  // Derive Key
  const encoder = new TextEncoder();
  const passwordKey = await crypto.subtle.importKey(
    'raw',
    encoder.encode(password),
    { name: 'PBKDF2' },
    false,
    ['deriveKey']
  );

  const iterations = metadata.encryption?.iterations || PBKDF2_ITERATIONS;

  const aesKey = await crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt,
      iterations,
      hash: 'SHA-256'
    },
    passwordKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['decrypt']
  );

  try {
    const plainBuffer = await crypto.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv,
        tagLength: TAG_LENGTH_BITS
      },
      aesKey,
      cipherBytes
    );

    const decoder = new TextDecoder();
    const plainText = decoder.decode(plainBuffer);
    return { plainText, metadata };
  } catch (err: any) {
    throw new Error('Decryption failed: Incorrect password or tampered backup file.');
  }
}
