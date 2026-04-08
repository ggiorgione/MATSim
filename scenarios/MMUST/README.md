# MMUST — Luxembourg MATSim Scenario

Multimodal MATSim scenario for Luxembourg built from VISUM network exports and GTFS transit data.

---

## Scenario files

### Files needed to run the simulation

| File | Description |
|------|-------------|
| `full_config.xml` | MATSim configuration (the only file to pass to the Controler) |
| `network_with_pt.xml` | Multimodal network including PT links (output of PTMapper) |
| `transitSchedule.xml` | Mapped PT schedule with `linkRefId` on all stops (output of PTMapper) |
| `transitVehicles.xml` | PT vehicle fleet definitions (output of GTFS converter) |
| `plans.xml` | Synthetic population (~1 % sample) — modes: `car`, `pt`, `bike`, `walk` |
| `plans_170k.xml` | Full population (~170 k agents) |

### Network-building inputs

All VISUM CSV exports are under `VISUM/Input4MATSim/`:

| File | Used for |
|------|----------|
| `Nodes.csv` | Road node coordinates, IDs, types |
| `Links.csv` | Road links with TSYSSET mode codes, capacity, free-speed, geometry |
| `VISUM/GTFS/042026/` | GTFS transit data (stops, routes, trips, stop_times, …) |

TSYSSET → MATSim mode mapping:

| TSYSSET code | MATSim mode |
|---|---|
| `B` | `bus` |
| `Bike` | `bike` |
| `R` | `rail` |
| `M`, `M_*` | `walk` (walk links — teleported in simulation) |
| `Covoit`, `Covoit_*` | `ride` |
| `PL`, `PL_*` | `truck` |
| `V`, `V_*`, `VS_*` | `car` |

### Intermediate / pre-processing files

| File | Description |
|------|-------------|
| `pt2matsim-tools/network.xml` | Base multimodal network (no PT links) — input for PTMapper |
| `pt2matsim-tools/transitSchedule_unmapped.xml` | Raw GTFS schedule (no link references) — input for PTMapper |
| `pt2matsim-tools/ptmapping_config.xml` | Configuration for pt2matsim `PublicTransitMapper` (pre-processing only, not used by the simulation) |

---

## How to regenerate the scenario inputs

All commands are run from the **workspace root** (`c:\Users\giulio\Documents\VSCode\MATSim`).

### Step 1 — Build the base network from VISUM CSVs

```bat
mvn -pl matsim exec:java ^
  -Dexec.mainClass=org.matsim.visum.Visum2MATSimNetworkConverter ^
  -Dexec.args="scenarios/MMUST/VISUM/Input4MATSim scenarios/MMUST/pt2matsim-tools EPSG:3857"
```

Output: `scenarios/MMUST/pt2matsim-tools/network.xml` (200 933 nodes, 469 602 links, modes: `bike`, `bus`, `car`, `rail`, `ride`, `truck`)

### Step 2 — Convert GTFS to an unmapped transit schedule

```bat
mvn -pl pt2matsim exec:java ^
  -Dexec.mainClass=org.matsim.pt2matsim.run.Gtfs2TransitSchedule ^
  -Dexec.args="scenarios/MMUST/VISUM/GTFS/042026 20260424 EPSG:3857 scenarios/MMUST/pt2matsim-tools/transitSchedule_unmapped.xml scenarios/MMUST/transitVehicles.xml"
```

Output: `pt2matsim-tools/transitSchedule_unmapped.xml`, `transitVehicles.xml`

### Step 3 — Map the schedule to the network (PTMapper)

```bat
mvn -pl pt2matsim exec:java ^
  -Dexec.mainClass=org.matsim.pt2matsim.run.PublicTransitMapper ^
  -Dexec.args=scenarios/MMUST/pt2matsim-tools/ptmapping_config.xml
```

Output: `scenarios/MMUST/transitSchedule.xml`, `scenarios/MMUST/network_with_pt.xml`

> **Note**: `ptmapping_config.xml` is a `pt2matsim` configuration file, not a MATSim simulation config. The internal file paths (`inputNetworkFile`, `inputScheduleFile`, etc.) are relative to the workspace root.

---

## Running the simulation

### From the command line (Maven)

```bat
mvn -pl matsim exec:java ^
  -Dexec.mainClass=org.matsim.core.controler.Controler ^
  -Dexec.args=scenarios/MMUST/full_config.xml
```

### From the bundled launcher (Windows)

Double-click `run.bat` in `scenarios/MMUST/`. Requires `matsim-simulation.jar` and optionally `jre21/` in the same folder.

### From an IDE

Run `org.matsim.core.controler.Controler` and pass `scenarios/MMUST/full_config.xml` as the program argument.

---

## Mode configuration

| Mode | Network routing | Notes |
|------|----------------|-------|
| `car` | Network-routed (`networkModes = car`) | Car links in `network_with_pt.xml` |
| `pt` | Transit schedule (`SwissRailRaptor`) | Requires `transitSchedule.xml` + `transitVehicles.xml` |
| `bike` | Teleported (beeline × 1.3, 15 km/h) | Bike links exist in network but routing uses teleportation |
| `walk` | Teleported (beeline × 1.3, 3 km/h) | Walk is **not** a network mode — no walk links in `network_with_pt.xml`; this is standard MATSim behaviour |

---

## Output

Simulation results are written to `scenarios/MMUST/output/` (configured in `full_config.xml`).

