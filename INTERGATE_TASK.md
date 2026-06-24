# Meshtastic Integration into OsmAnd MeshTracker — Technical Specification

## 1. Current State Analysis

### 1.1 Project Layout

```
/home/grishberg/projects/android/
├── meshtastic/                     # Meshtastic Android library (KMP, 27+ modules)
├── meshtastic-demo/                # Demo app consuming meshtastic libraries
│   └── app/src/main/java/
│       └── com/github/grishberg/meshtastic/demo/
│           ├── DemoMainViewModel.kt   # Node list ViewModel
│           ├── NodesFragment.kt       # Nodes tab RecyclerView
│           ├── DemoNodeListAdapter.kt # Node → UI binding
│           └── DemoGpsSenderUseCase.kt # GPS broadcast to mesh
├── osm-build/
│   └── OsmAnd-project/             # OsmAnd-based navigation app
│       ├── settings.gradle          # includeBuild('meshtastic')
│       ├── OsmAnd/build-common.gradle
│       │   └── deps (436-444): 8 meshtastic core modules
│       └── OsmAnd/src/net/osmand/plus/plugins/radiomesh/
│           ├── MeshTrackerPlugin.java     # Plugin entry point
│           ├── MeshTrackerLayer.java      # Map rendering layer
│           ├── MeshtasticBridge.java      # Bridge interface
│           ├── MeshtasticBridgeImpl.kt    # BLE connection implementation
│           ├── MeshtasticNode.java        # Node data model
│           ├── NodeToMeshObjectConverter.kt # Node → MeshObject mapping
│           ├── MeshtasticConnectionDialog.kt # BLE/USB scan & connect UI
│           ├── MeshtasticNodeDialog.kt    # Node info display
│           ├── MeshObject.java            # Map object model (AIS legacy)
│           └── MeshTrackerSettingsFragment.java # Plugin settings
```

### 1.2 Dependency Integration (osm-build)

**settings.gradle** — composite build includes the meshtastic project directly:
```groovy
includeBuild('/home/grishberg/projects/android/meshtastic')
```

**build-common.gradle** — 8 meshtastic core modules are already added:
```groovy
implementation project(':meshtastic:core:proto')
implementation project(':meshtastic:core:model')
implementation project(':meshtastic:core:ble')
implementation project(':meshtastic:core:common')
implementation project(':meshtastic:core:di')
implementation project(':meshtastic:core:repository')
implementation project(':meshtastic:core:data')
implementation project(':meshtastic:core:domain')
```

meshtastic-demo adds **19 more modules** (core:database, core:datastore, core:service, core:network, core:nfc, core:prefs, core:resources, core:ui, core:barcode, core:takserver, plus 8 feature modules).

### 1.3 Current Data Flow (osm-build)

```
BLE Device
    │
    ▼ (direct BLE — bypasses meshtastic library's service layer)
MeshtasticBridgeImpl
    │  . BLE scan via KableBleScanner
    │  . BLE connect via KableMeshtasticRadioProfile
    │  . Manual protobuf handshake (WantConfig / FromRadio)
    │  . Manual addOrUpdateNode() parsing
    │  . Returns List<MeshtasticNode> on demand
    │
    ▼ (poll every 30s via Timer)
MeshTrackerPlugin.refreshMeshtasticNodes()
    │
    ▼
NodeToMeshObjectConverter.convertAll(nodes)
    │
    ▼
MeshDataManager.onMeshObjectReceived()
    │
    ▼
MeshTrackerLayer (rendering)
```

### 1.4 meshtastic-demo Data Flow (reactive)

```
BLE Device
    │
    ▼ (via meshtastic library)
MeshServiceClient (core:service)
    │  . Deserializes FromRadio protobuf
    │  . Maintains node DB
    │
    ▼
NodeRepository.getNodes(): Flow<List<Node>>
    │  . Reactive stream
    │  . Each Node has Position with lat/lon
    │
    ▼
DemoMainViewModel
    │  . filters: validPosition != null
    │  . Exposes StateFlow<List<Node>>
    │
    ▼
NodesFragment / DemoNodeListAdapter
```

### 1.5 Key Differences

| Aspect | osm-build (current) | meshtastic-demo |
|--------|-------------------|-----------------|
| **Data retrieval** | Pull-based (`getNodes()` every 30s) | Reactive (`Flow<List<Node>>`) |
| **Meshtastic core usage** | Direct BLE only, manual protobuf | Full library: service + repository layers |
| **Node model** | Custom `MeshtasticNode` (Java) | Library `Node` (Kotlin, with proper position) |
| **Connection mgmt** | Manual BLE via `KableBleConnectionFactory` | `MeshServiceClient` + `RadioController` |
| **DI** | Manual instantiation | Koin modules |
| **Node persistence** | In-memory `CopyOnWriteArrayList` | Room database (core:database) |
| **GPS broadcasting** | Not implemented | `DemoGpsSenderUseCase` |
| **Modules used** | 8 core modules | 27+ modules (all core + features) |

### 1.6 Gaps in Current osm-build Implementation

1. **No reactive node updates** — The 30-second polling timer is inefficient and misses real-time position changes.
2. **Manual protobuf handshake** — `MeshtasticBridgeImpl` reimplements the BLE handshake and protobuf parsing that the library already provides.
3. **Stale/lost node handling** — No mechanism to detect and remove stale nodes from the map.
4. **No position broadcasting** — The device's own GPS is never sent to the mesh network.
5. **No battery/voltage data** — `addOrUpdateNode()` hardcodes battery=0 and voltage=0f.
6. **AIS-ism in code** — `MeshObject` still uses ship/vessel terminology (MMSI, COG, SOG, etc.).
7. **Missing modules** — `core:service`, `core:database`, `core:network`, `core:prefs` are not used.
8. **Koin not initialized** — The meshtastic library relies on Koin for DI; osm-build doesn't initialize it.

---

## 2. Implementation Plan

### Phase 1: Add Missing Meshtastic Module Dependencies

**Goal:** Match the module set that meshtastic-demo uses.

**Files to modify:**
- `OsmAnd/build-common.gradle`

**Changes:**
```groovy
// Additional meshtastic modules needed:
implementation project(':meshtastic:core:service')
implementation project(':meshtastic:core:database')
implementation project(':meshtastic:core:datastore')
implementation project(':meshtastic:core:network')
implementation project(':meshtastic:core:prefs')
implementation project(':meshtastic:core:resources')
```

**Risk:** Dependency conflicts with OsmAnd's existing protobuf, coroutines, or Room versions. May need version alignment.

---

### Phase 2: Refactor MeshtasticBridgeImpl to Use Library Components

**Goal:** Replace the manual BLE/protobuf handling with the meshtastic library's `RadioController` and `NodeRepository`.

**Current approach (to be replaced):**
- `MeshtasticBridgeImpl` creates `KableBleScanner` and `KableBleConnectionFactory` directly
- Manual `FromRadio` protobuf parsing in `processFromRadioPacket()`
- Manual node storage in `CopyOnWriteArrayList<MeshtasticNode>`

**Target approach:**
- Use `RadioController` from `core:service` for BLE connection management
- Use `NodeRepository` from `core:repository` for reactive node data
- Use `ServiceRepository` for connection state
- Initialize Koin for meshtastic DI modules

**Files to create/modify:**
- `MeshtasticBridge.java` — Update interface to expose reactive streams
- `MeshtasticBridgeImpl.kt` — Rewrite to use library components
- `MeshTrackerPlugin.java` — Initialize Koin, wire reactive streams

**New MeshtasticBridge interface:**
```java
public interface MeshtasticBridge {
    void initialize(Context context);
    Flow<List<MeshtasticNode>> observeNodes();    // reactive, not poll-based
    Flow<ConnectionState> observeConnectionState();
    void connect(String address);
    void disconnect();
    boolean isConnected();
    // ... scan methods remain similar
}
```

**Reactive node flow:**
```
NodeRepository.getNodes(): Flow<List<Node>>
    │
    ▼ (map to MeshtasticNode)
Flow<List<MeshtasticNode>>
    │
    ▼ (subscribe in plugin)
MeshTrackerPlugin — pushes to MeshDataManager
    │
    ▼
MeshTrackerLayer
```

---

### Phase 3: Implement Reactive Node Observation

**Goal:** Replace `Timer`-based polling with `Flow` subscription.

**Files to modify:**
- `MeshTrackerPlugin.java`
- `MeshtasticBridgeImpl.kt`

**Changes:**
```kotlin
// In MeshtasticBridgeImpl or a new service class:
scope.launch {
    nodeRepository.getNodes()
        .collect { nodes ->
            val filtered = nodes.filter { it.validPosition != null }
            val meshNodes = filtered.map { node ->
                MeshtasticNode(
                    nodeId = node.num,
                    userId = node.user.id,
                    longName = node.user.long_name,
                    latitude = node.latitude,
                    longitude = node.longitude,
                    altitude = node.position.altitude,
                    lastHeard = node.lastHeard,
                    batteryLevel = node.deviceMetrics.battery_level,
                    voltage = node.deviceMetrics.voltage,
                    hasPosition = node.validPosition != null
                )
            }
            onNodesChanged(meshNodes)
        }
}
```

**Remove:**
- `MeshDataManager.initTimer()` — no longer needed
- `refreshMeshtasticNodes()` timer callback in `MeshTrackerPlugin`

---

### Phase 4: Implement GPS Position Broadcasting

**Goal:** Send the device's GPS position to the mesh network periodically, following `DemoGpsSenderUseCase` pattern.

**Files to create:**
- `MeshGpsBroadcastUseCase.kt` — Periodic GPS sender

**Integration points:**
- OsmAnd's `LocationProvider.getLastKnownLocation()` for GPS data
- `RadioController.sendMessage()` via meshtastic library
- Configurable interval (default 60s) via plugin settings

**Flow:**
```
OsmAnd LocationProvider
    │
    ▼
MeshGpsBroadcastUseCase (every N seconds)
    │  . Get last known location
    │  . Convert to protobuf Position
    │  . Create DataPacket
    │
    ▼
RadioController.sendMessage()
    │
    ▼
BLE → Mesh network
```

---

### Phase 5: Adapt MeshObject for Meshtastic Nodes

**Goal:** Add meshtastic-specific fields to `MeshObject` or create a parallel model.

**Files to modify:**
- `MeshObject.java` — Add batteryLevel, voltage, userId fields
- `NodeToMeshObjectConverter.kt` — Map all new fields
- `MeshObjType.java` — Add `MESH_NODE` type
- `MeshObjectDrawable.java` — Render battery indicator, online status
- `MeshTrackerLayer.java` — Update context menu for meshtastic data

**Node → MeshObject field mapping:**

| Meshtastic `Node` field | `MeshObject` field | Notes |
|---|---|---|
| `num` (Int) | `ais_mmsi` | Unique identifier |
| `latitude` | `ais_position.lat` | `position.latitude_i * 1e-7` |
| `longitude` | `ais_position.lon` | `position.longitude_i * 1e-7` |
| `altitude` | `ais_altitude` | From position |
| `user.long_name` | `ais_shipName` | Display name |
| `user.id` | `ais_callSign` | Short ID (!XXXXXXXX) |
| `lastHeard` (epoch sec) | `lastUpdate` (epoch ms) | `lastHeard * 1000L` |
| `deviceMetrics.battery_level` | New field | 0-100 or -1 |
| `deviceMetrics.voltage` | New field | Float voltage |
| `validPosition != null` | `hasPosition` | Boolean flag |

**Context menu updates (`getObjectName`):**
```
"Meshtastic: {long_name}"
  ID: {user.id}
  Last heard: {formatted timestamp}
  Battery: {battery_level}% ({voltage}V)
  Position: {lat}, {lon}
  Online: {yes/no}
```

---

### Phase 6: Update Settings & Connection UI

**Goal:** Polish the connection flow and add GPS broadcast settings.

**Files to modify:**
- `MeshTrackerSettingsFragment.java` — Add GPS broadcast toggle and interval
- `mesh_settings.xml` — Add GPS broadcast preference entries

**New preferences:**
- `mesh_gps_broadcast_enabled` (boolean, default: false)
- `mesh_gps_broadcast_interval` (list: 15s, 30s, 60s, 120s, 300s, default: 60s)

**Connection dialog improvements:**
- Show real connection state (not just "connected after 3s timeout")
- Handle reconnection on plugin enable
- Show RSSI/signal strength

---

### Phase 7: Clean Up Legacy AIS Code

**Goal:** Remove or deprecate ship-tracking features that are irrelevant for meshtastic.

**Files to consider:**
- `MeshMessageListener.java` — UDP/TCP NMEA listener (keep as fallback)
- `MeshMessageSimulationListener.java` — Simulation (keep as dev tool)
- `MeshTrackerHelper.java` — CPA calculations for vessel collision avoidance (low priority for meshtastic)
- `MeshLoadTask.java` — File import (keep or deprecate)

**MeshObjType cleanup:**
- Keep existing ship types for backward compatibility
- Add `MESH_NODE` type to represent meshtastic nodes
- Update icon mapping in `MeshObjectDrawable` for `MESH_NODE`

---

## 3. Architecture Diagrams

### 3.1 Target Architecture

```
┌─────────────────────────────────────────────────┐
│                  OsmAnd App                      │
│                                                  │
│  ┌─────────────────────────────────────────┐     │
│  │         MeshTrackerPlugin               │     │
│  │  ┌─────────────────┐  ┌──────────────┐  │     │
│  │  │ KoinInitializer │  │     DI       │  │     │
│  │  │ (meshtastic)    │  │ (own scopes) │  │     │
│  │  └────────┬────────┘  └──────────────┘  │     │
│  │           │                              │     │
│  │  ┌────────▼────────────────────────┐     │     │
│  │  │     MeshtasticBridgeImpl        │     │     │
│  │  │  ┌──────────────────────────┐   │     │     │
│  │  │  │ RadioController (BLE)    │   │     │     │
│  │  │  │ ServiceRepository        │   │     │     │
│  │  │  │ NodeRepository (Flow)    │   │     │     │
│  │  │  │ MeshGpsBroadcastUseCase  │   │     │     │
│  │  │  └──────────────────────────┘   │     │     │
│  │  └─────────────────────────────────┘     │     │
│  │                                           │     │
│  │  ┌──────────────────────────────────┐     │     │
│  │  │    NodeToMeshObjectConverter    │     │     │
│  │  └──────────────┬───────────────────┘     │     │
│  │                 │                          │     │
│  │  ┌──────────────▼──────────────┐          │     │
│  │  │     MeshDataManager        │          │     │
│  │  │  (ConcurrentHashMap)       │          │     │
│  │  └──────────────┬──────────────┘          │     │
│  └─────────────────│──────────────────────────┘     │
│                    │                                │
│  ┌─────────────────▼──────────────────────────┐     │
│  │           MeshTrackerLayer                  │     │
│  │  (onPrepareBufferImage → draw markers)      │     │
│  └─────────────────────────────────────────────┘     │
│                                                    │
│  ┌─────────────────────────────────────────────┐   │
│  │    MeshTrackerSettingsFragment              │   │
│  │  (BLE scan, connect, GPS broadcast config)  │   │
│  └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                        │
                        ▼ (composite build)
┌─────────────────────────────────────────────────────┐
│              meshtastic/ library                      │
│  ┌──────┐ ┌──────┐ ┌────────┐ ┌──────────┐         │
│  │ ble  │ │proto │ │ model  │ │ service  │         │
│  ├──────┤ ├──────┤ ├────────┤ ├──────────┤         │
│  │common│ │data  │ │domain  │ │repository│         │
│  ├──────┤ ├──────┤ ├────────┤ ├──────────┤         │
│  │database│datastore│ prefs │ │ network  │         │
│  └──────┘ └──────┘ └────────┘ └──────────┘         │
└─────────────────────────────────────────────────────┘
```

### 3.2 Reactive Data Flow

```
BLE Device → protobuf FromRadio messages
    │
    ▼
RadioController (core:service)
    │  . Decodes protobuf
    │  . Updates node DB (core:database/Room)
    │
    ▼
NodeRepository.getNodes(): Flow<List<Node>>
    │  . Reactively emits on DB changes
    │  . Each Node has protobuf Position
    │
    ▼ (.map { filter validPosition, convert to MeshtasticNode })
    │
    ▼
MeshTrackerPlugin (coroutine collect)
    │  . Removes stale nodes
    │  . Calls onMeshObjectReceived for new/updated
    │
    ▼
MeshDataManager.onMeshObjectReceived(meshObject)
    │  . Updates ConcurrentHashMap
    │
    ▼
MeshTrackerLayer.onMeshObjectReceived()
    │  . Creates/updates MeshObjectDrawable
    │  . queueEvent.mapActivityInvalidate()
    │
    ▼
OsmAnd map renders marker
```

---

## 4. File Inventory

### Files to CREATE

| File | Purpose |
|------|---------|
| `radiomesh/KoinInitializer.kt` | Initialize Koin with meshtastic modules |
| `radiomesh/MeshGpsBroadcastUseCase.kt` | Periodic GPS position broadcasting |
| `radiomesh/MeshNodeInfo.kt` | Meshtastic-specific node data extension |

### Files to MODIFY

| File | Changes |
|------|---------|
| `OsmAnd/build-common.gradle` | Add 6+ meshtastic module dependencies |
| `radiomesh/MeshtasticBridge.java` | Add `observeNodes()`, `observeConnectionState()` |
| `radiomesh/MeshtasticBridgeImpl.kt` | Use RadioController + NodeRepository, reactive flows |
| `radiomesh/MeshTrackerPlugin.java` | Remove polling Timer, use Flow subscription, init Koin |
| `radiomesh/MeshObject.java` | Add batteryLevel, voltage, userId fields |
| `radiomesh/NodeToMeshObjectConverter.kt` | Map new fields, handle MeshtasticNode → MeshObject |
| `radiomesh/MeshObjType.java` | Add `MESH_NODE` enum value |
| `radiomesh/MeshObjectDrawable.java` | Render meshtastic-specific visuals (battery, online) |
| `radiomesh/MeshTrackerLayer.java` | Update context menu for meshtastic data |
| `radiomesh/MeshTrackerSettingsFragment.java` | Add GPS broadcast settings |
| `mesh_settings.xml` | Add GPS broadcast preferences |
| `strings.xml` | Add meshtastic-related strings |

### Files to REMOVE or DEPRECATE

| File | Reason |
|------|--------|
| `radiomesh/MeshMessageListener.java` | Legacy AIS NMEA UDP/TCP (deprecate) |
| `radiomesh/MeshMessageSimulationListener.java` | Legacy AIS simulation (deprecate) |

---

## 5. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Protobuf version conflict (OsmAnd uses older proto3, meshtastic uses newer) | Build failure | Align protobuf versions in OsmAnd's gradle config; use same protobuf-java version |
| Koin DI conflict with OsmAnd's manual DI | Runtime crash | Initialize meshtastic Koin modules in a separate `Modules` instance; avoid global Koin |
| Room database conflict (OsmAnd may use its own DB) | Build/runtime | Use meshtastic's Room database in a separate context; check for schema conflicts |
| Kotlin/Coroutines version mismatch | Build failure | Align Kotlin to 2.1.x and coroutines to 1.9.x in `versions.gradle` |
| `MeshtasticBridgeImpl` heavily coupled to manual BLE | High refactoring effort | Keep old implementation as fallback; add new implementation in parallel |
| No working Meshtastic device for testing | Can't verify BLE | Keep mock/simulation data as development fallback |

---

## 6. Build Verification

After each phase, verify:

```bash
cd /home/grishberg/projects/android/osm-build/OsmAnd-project

# Basic build
./gradlew :OsmAnd:assembleAndroidFullOpenglFatDebug

# Check for dependency conflicts
./gradlew :OsmAnd:dependencies

# Check for lint issues
./gradlew :OsmAnd:lint
```

---

## 7. Success Criteria

1. APK builds successfully with all meshtastic library modules
2. Meshtastic device connection via BLE works from plugin settings
3. Nodes with valid GPS positions appear as map markers on the OsmAnd map
4. Node markers update reactively (no 30s polling delay)
5. Node markers show proper names, battery, and online status
6. Device GPS position is broadcast to mesh at configurable interval
7. Stale nodes are removed from the map after timeout
8. Fallback to empty/placeholder state when no BLE device connected
9. No crashes on plugin enable/disable toggle
