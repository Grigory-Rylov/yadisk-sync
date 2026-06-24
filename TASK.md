# Task: Meshtastic Node Integration into OsmAnd MeshTrackerLayer

## Overview

Integrate real Meshtastic node data into OsmAnd's `MeshTrackerLayer` by replacing hardcoded mock points with live node positions from the meshtastic core modules. The integration should follow the same architecture used in `meshtastic-demo` for connecting to meshtastic devices and receiving node data.

## Current State

### OsmAnd MeshTracker (radiomesh plugin)
- **Location**: `osm-build/OsmAnd-project/OsmAnd/src/net/osmand/plus/plugins/radiomesh/`
- **Data source**: `MeshSimulationProvider.initMockData()` — hardcoded 2 points near Saint Petersburg (59.94°N, 30.30°E)
- **Rendering**: `MeshTrackerLayer` renders `MeshObject` instances from `MeshDataManager`
- **Data flow**: `MeshSimulationProvider` → `plugin.onMeshObjectReceived()` → `MeshDataManager` → `MeshTrackerLayer`

### meshtastic-demo
- **Connection**: `RadioController` (core:ble) manages BLE connection to meshtastic device
- **Node data**: `NodeRepository.getNodes()` returns `StateFlow<List<Node>>` with positions
- **Model**: `Node` contains `latitude`, `longitude`, `user.long_name`, `lastHeard`, `batteryLevel`
- **ViewModel**: `DemoMainViewModel` exposes nodes and connection state as StateFlows

## Goal

Replace the hardcoded mock data in `MeshSimulationProvider.initMockData()` with real-time node positions streamed from the meshtastic core, rendered as `MeshObject` markers on the OsmAnd map.

## Architecture

```
[BLE/Serial Connection] → RadioController → NodeRepository → Flow<Node>
                                                        ↓
                                               MeshtasticBridge (NEW)
                                                        ↓
                                         Node → MeshObject converter
                                                        ↓
                                        plugin.onMeshObjectReceived(meshObject)
                                                        ↓
                                           MeshDataManager (existing)
                                                        ↓
                                        MeshTrackerLayer rendering (existing)
```

## Task Breakdown

### Phase 1: Meshtastic Core Dependency

- [ ] T1.1: Add meshtastic core modules as dependencies to the OsmAnd app
  - `:core:model` — Node, User, Position data classes
  - `:core:proto` — Protobuf definitions
  - `:core:ble` — BLE connection infrastructure
  - `:core:data` — NodeRepository, ServiceRepository
  - `:core:common` — Utility classes
  - `:core:di` — Koin dependency injection
  - `:core:database` — Room database for node persistence
  - `:core:repository` — Repository interfaces
  - `:core:service` — MeshService AIDL binding
  - `:core:network` — Network utilities
- [ ] T1.2: Resolve dependency conflicts between OsmAnd and meshtastic dependencies
  - Align Kotlin, AGP, coroutine versions
  - Handle transitive dependency overrides

### Phase 2: MeshtasticBridge Adapter

- [ ] T2.1: Create `MeshtasticBridge` class in `radiomesh/` package
  - Java-compatible entry point for meshtastic core (Kotlin) infrastructure
  - Initializes Koin DI module for required meshtastic components
  - Exposes `connect()`, `disconnect()`, `isConnected()` methods
- [ ] T2.2: Implement `Node` → `MeshObject` converter
  - Map `Node.num` → `MeshObject.ais_mmsi` (unique identifier)
  - Map `Node.latitude` / `Node.longitude` → `MeshObject.ais_position`
  - Map `Node.user.long_name` → `MeshObject.ais_shipName`
  - Map `Node.lastHeard` → `MeshObject.lastUpdate`
  - Map `Node.batteryLevel` → status indicator
  - Map `Node.deviceMetrics.voltage` → additional metadata
  - Use `MeshObjType.MESH_LANDSTATION` for mesh nodes (or create new type `MESH_NODE`)
- [ ] T2.3: Implement Flow observer
  - Collect `NodeRepository.getNodes()` flow on a background thread
  - On each emission, diff against existing `MeshObject` map
  - Call `meshDataManager.onMeshObjectReceived()` for new/updated nodes
  - Call `meshDataManager.onMeshObjectRemoved()` for stale nodes

### Phase 3: Connection Entry Points

- [ ] T3.1: Add Meshtastic connection preferences to `MeshTrackerSettingsFragment`
  - Connection type selector: BLE / Serial / WiFi
  - Device selection list (scanned BLE devices)
  - Channel configuration (name, PSK key)
  - Connect / Disconnect button
- [ ] T3.2: Implement BLE device scanning
  - Use meshtastic `core:ble` scanning infrastructure
  - Expose discovered devices to settings UI
- [ ] T3.3: Implement connection lifecycle management
  - Start MeshService / bind RadioController on plugin enable + connect
  - Handle connection state changes (connected, disconnected, error)
  - Show connection status indicator on map layer
- [ ] T3.4: Add GPS position broadcasting
  - Use pattern from `DemoGpsSenderUseCase` for periodic position broadcast
  - Send current device GPS position to mesh channel at configurable interval (60s default)

### Phase 4: Replace Mock Data

- [ ] T4.1: Modify `MeshTrackerPlugin.setEnabled(true)` to initialize MeshtasticBridge instead of `initMockData`
- [ ] T4.2: Remove or deprecate `MeshSimulationProvider.initMockData()`
- [ ] T4.3: Keep simulation as fallback when no meshtastic device is connected
- [ ] T4.4: Update `MeshDataManager.initTimer()` cleanup timer to work with real node data

### Phase 5: UI Integration

- [ ] T5.1: Update `MeshObjectDrawable` to display meshtastic-specific data
  - Node name from `user.long_name`
  - Battery level indicator
  - Online/offline status based on `lastHeard`
- [ ] T5.2: Update `MeshTrackerLayer.getObjectName()` context menu
  - Show meshtastic node info: name, ID, last heard, battery, distance
- [ ] T5.3: Add connection status overlay on map (connected, scanning, error)

### Phase 6: GPS Position Broadcasting

- [ ] T6.1: Implement periodic GPS broadcast service
  - Get device GPS location from OsmAnd's `LocationProvider`
  - Encode as `ProtoPosition` and send via `RadioController.sendMessage()`
  - Broadcast interval: configurable (30-60s)
- [ ] T6.2: Add broadcast toggle to settings
  - Enable/disable position sharing
  - Configure broadcast interval

### Phase 7: Build & Verification

- [ ] T7.1: Verify `./gradlew :OsmAnd:assembleDebug` succeeds
- [ ] T7.2: Verify `./gradlew :OsmAnd:assembleAndroidFullOpenglFatDebug` succeeds
- [ ] T7.3: Validate no runtime crashes during plugin initialization
- [ ] T7.4: Test mock data fallback when no meshtastic device available

## Data Mapping Reference

### Node → MeshObject

| Meshtastic Node field | MeshObject field | Notes |
|---|---|---|
| `num` (Int) | `ais_mmsi` (Int) | Unique identifier |
| `latitude` | `ais_position.lat` | Computed from `position.latitude_i * 1e-7` |
| `longitude` | `ais_position.lon` | Computed from `position.longitude_i * 1e-7` |
| `user.long_name` | `ais_shipName` | Display name |
| `user.id` | `ais_callSign` | Short node ID (e.g., !A1B2C3D4) |
| `lastHeard` (epoch sec) | `lastUpdate` (epoch ms) | Multiply by 1000 |
| `batteryLevel` | custom field | For UI display |
| `deviceMetrics.voltage` | custom field | For UI display |

## Key Files to Create

- `radiomesh/MeshtasticBridge.java` — Java-compatible bridge to meshtastic core
- `radiomesh/MeshtasticBridgeImpl.kt` — Kotlin implementation with coroutines
- `radiomesh/NodeToMeshObjectConverter.kt` — Node → MeshObject mapping

## Key Files to Modify

- `radiomesh/MeshTrackerPlugin.java` — Initialize bridge, connection lifecycle
- `radiomesh/MeshSimulationProvider.java` — Replace mock data with bridge data
- `radiomesh/MeshTrackerSettingsFragment.java` — Add connection UI
- `radiomesh/MeshTrackerLayer.java` — Update context menu for meshtastic nodes
- `radiomesh/MeshObject.java` — Potentially add meshtastic-specific fields
- `radiomesh/MeshObjType.java` — Add `MESH_NODE` type

## Constraints

- OsmAnd plugin is Java-based; meshtastic core is Kotlin — bridge must be Java-compatible
- OsmAnd uses its own plugin lifecycle (`OsmandPlugin.setEnabled()`), not Android lifecycle
- Meshtastic DI uses Koin; OsmAnd does not — bridge must initialize its own Koin context
- Must handle the case where meshtastic device is not connected gracefully (fallback to empty or no markers)

## Success Criteria

1. OsmAnd APK builds successfully with meshtastic core dependencies
2. Meshtastic device can be connected from OsmAnd settings
3. Nodes with valid GPS positions appear as markers on the OsmAnd map
4. Node markers update in real-time as position data arrives
5. Device GPS position is broadcast to mesh network periodically
6. Graceful fallback when no meshtastic device is connected
