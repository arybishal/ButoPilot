# AutoPilot Android - Secure Backup & Reliable Data Recovery

**Document Version:** 1.0.0  
**Phase:** 5 (Diagnostics, Backup & Data Recovery)  
**Status:** Accepted & Verified  

---

## 1. Overview

AutoPilot implements a versioned, tamper-evident, and cryptographically secure backup system (`.autopilot` format). It allows users to export and import automation configurations, schedules, proxy endpoints, proxy sources, and execution history across devices using Android's Storage Access Framework (SAF) without requiring broad storage permissions.

---

## 2. Archive Specification (`.autopilot`)

The `.autopilot` container is a standardized zip archive containing structured JSON data files and a top-level metadata manifest:

```
archive.autopilot (ZIP)
├── manifest.json            # Versioned header, record counts, checksums, crypto metadata
├── payload.enc              # (When encrypted) AES-256-GCM encrypted payload package
├── profiles.json            # (When unencrypted) Automation profile configurations
├── schedules.json           # (When unencrypted) Schedule and recurrence rules
├── proxies.json             # (When unencrypted) Proxy endpoints (CREDENTIALS STRIPPED)
├── sources.json             # (When unencrypted) Proxy subscription sources
└── history.json             # (When unencrypted) Historical execution run records
```

### 2.1 Manifest Schema (`manifest.json`)
```json
{
  "formatVersion": 1,
  "appVersion": "1.0.0",
  "schemaVersion": 4,
  "createdAt": 1774310400000,
  "isEncrypted": true,
  "encryptionSaltBase64": "vB7X...==",
  "encryptionIvBase64": "h8Kq...==",
  "kdfIterations": 100000,
  "exportedCategories": ["PROFILES", "SCHEDULES", "PROXIES", "PROXY_SOURCES", "EXECUTION_HISTORY"],
  "recordCounts": {
    "profiles": 12,
    "schedules": 4,
    "proxies": 2598,
    "proxySources": 2,
    "executionHistory": 48
  },
  "checksums": {
    "profiles.json": "9f83...67a1",
    "schedules.json": "a3b2...c4d5",
    "proxies.json": "1e2f...3a4b"
  }
}
```

---

## 3. Cryptographic Security Standards

When password protection is enabled:
- **Key Derivation Function:** PBKDF2 with HMAC-SHA256 (`PBKDF2WithHmacSHA256`), upgraded to **250,000 iterations** (`BackupCrypto.PBKDF2_ITERATIONS`).
- **Backward Compatibility:** Archives created with legacy 100,000 iterations (`BackupCrypto.LEGACY_PBKDF2_ITERATIONS`) are detected via `manifest.kdfIterations` and decrypted seamlessly.
- **Salt:** 16 bytes generated per backup using `SecureRandom`.
- **Cipher:** AES-256 in Galois/Counter Mode (`AES/GCM/NoPadding`).
- **Initialization Vector / Nonce:** 12 bytes generated per encryption operation using `SecureRandom`. **Never reused.**
- **Authentication Tag:** 128-bit authentication tag ensuring cryptographic integrity and authenticity. Tampering or incorrect passwords fail cleanly with `AEADBadTagException`.

---

## 4. Mandatory Security & Safety Rules

### 4.1 Strict Credential Exclusion
When serializing proxies for backup, the following fields are **strictly nullified**:
- `encryptedUsername = null`
- `encryptedPassword = null`
- `usernameIv = null`
- `passwordIv = null`
- Notes are annotated: `[Credentials excluded in backup for security]`.

Android KeyStore master keys never leave the hardware sandbox. Upon restore, users must re-enter credentials for authenticated proxies.

### 4.2 Fail-Safe Schedule State
Any schedule restored from a backup is **always restored with `enabled = false`** and `status = 'IDLE'`. This prevents unmonitored or unexpected background executions from firing immediately upon restore until explicitly confirmed by the user.

### 4.3 Pre-Transaction Entity Validation
Before creating safety backups or executing database transactions, `RestoreCoordinator` enforces strict structural validation on all deserialized records:
- Profiles: Blank ID or blank name triggers immediate rollback/abortion.
- Schedules: Blank ID or blank profileId triggers immediate abortion.
- Proxies: Blank ID, blank host, or port out of range `(1..65535)` triggers immediate abortion with zero database changes.

### 4.4 Path Traversal (Zip Slip) & Zip Bomb Defense
During archive inspection and extraction:
- Any zip entry name containing `..`, leading forward-slashes (`/`), or backslashes (`\`) throws an immediate `SecurityException`.
- Archives containing more than 50 entries or exceeding 50MB uncompressed are rejected to protect against zip bomb resource exhaustion.

---

## 5. Restoration Modes & Safety Rollback

Users can select between two restore modes:
1. **Merge Mode (Non-destructive):** Restores backup records alongside existing local profiles, schedules, and proxies.
2. **Replace Mode (Destructive with Auto-Safety Backup & Atomic Rollback):**
   - Automatically takes a safety backup of existing local data (`autopilot_safety_backup_<timestamp>.autopilot`) prior to modifying the database.
   - Clears existing records and inserts restored records atomically inside `database.withTransaction { ... }`.
   - Any database constraint failure or exception automatically rolls back the entire transaction, leaving existing data intact.

---

## 6. Automatic Periodic Backup Subsystem

AutoPilot integrates background periodic backups via Android WorkManager:
- **Configuration:** Managed via `BackupPreferences` and configurable in the Backup UI:
  - `isEnabled: Boolean` (Toggle switch)
  - `frequency: AutoBackupFrequency` (`DAILY` = 24h, `WEEKLY` = 168h)
  - `retentionCount: Int` (Configurable: 3, 5, or 10 backups)
  - `includeHistory: Boolean` (Toggle execution history inclusion)
- **WorkManager Scheduling:** Handled by `AutoBackupWorker` using `ExistingPeriodicWorkPolicy.UPDATE` with battery and storage constraints.
- **Retention Pruning:** Older automated backup files in `backups/auto` exceeding `retentionCount` are pruned automatically after each successful run.

## 6. Automatic Periodic Backups

`AutoBackupWorker` runs periodically via WorkManager (24-hour cycle) under battery-not-low and storage-not-low constraints.
- Backups are written atomically to internal storage (`backups/auto/`).
- Enforces retention count: preserves the 5 most recent auto-backups and purges older archives.
