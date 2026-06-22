# YaDisk Sync - Android Application Specification

## 1. Project Overview

**Project Name:** YaDisk Sync
**Type:** Native Android Application (Kotlin)
**Core Functionality:** Automatic photo synchronization from Yandex Disk to local storage with background periodic sync via WorkManager.

## 2. Technology Stack & Choices

- **Language:** Kotlin 1.9.x
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **UI Framework:** Jetpack Compose + Material Design 3
- **Architecture:** Clean Architecture with MVVM
- **Dependency Injection:** Hilt
- **Local Database:** Room
- **Networking:** Retrofit2 + OkHttp + Kotlin Serialization
- **Background Processing:** WorkManager (PeriodicWorkRequest)
- **Async Operations:** Kotlin Coroutines + Flow
- **Yandex Disk API:** REST WebDAV-like API

## 3. Feature List

### Authentication & Settings
- OAuth token input for Yandex Disk access
- Configure oldest date filter (download photos newer than specified date)
- Local storage path configuration
- Manual sync trigger button

### Sync Engine
- Query Yandex Disk public photos resource
- Download photos in batches (portions) to local folder
- Track download status per file in Room database
- Skip already-downloaded files on subsequent syncs
- Handle deleted local files gracefully (re-download if still exists on disk)

### Background Processing
- Periodic sync via WorkManager (configurable interval, default: 15 minutes)
- Constraint-aware execution (network available, not low battery)
- Progress notifications during active sync

### Database Tracking
- Store file metadata: remote ID, filename, download date, local path, size, checksum
- Track sync status: pending, downloading, completed, failed
- Clean up records for deleted local files

## 4. UI/UX Design Direction

**Visual Style:** Material Design 3 with dynamic color support
**Color Scheme:** Blue primary (Yandex brand), neutral backgrounds
**Layout Approach:**
- Single activity with bottom navigation (2 tabs: Home/Settings)
- Home screen: sync status, last sync time, manual sync button, recent downloads list
- Settings screen: token input, date filter picker, storage path display

## 5. Data Models

### SyncedFileEntity (Room)
```
id: Long (PK, auto-generate)
remoteId: String (unique)
fileName: String
remotePath: String
localPath: String?
fileSize: Long
mimeType: String
downloadedAt: Long? (timestamp)
syncStatus: Enum (PENDING, DOWNLOADING, COMPLETED, FAILED)
createdAt: Long
```

### SyncSettings (DataStore)
```
oauthToken: String
oldestDateMillis: Long
storagePath: String
syncIntervalMinutes: Int
lastSyncTime: Long
```

## 6. API Integration

Yandex Disk REST API endpoints:
- `GET /v1/disk/resources/files` - list files in Photos folder
- `GET /v1/disk/resources/download?path={path}` - get download URL
- Download via returned href URL with Bearer token