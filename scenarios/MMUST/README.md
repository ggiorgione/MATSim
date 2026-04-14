# MMUST — Luxembourg MATSim Scenario

Multimodal MATSim scenario for Luxembourg built from OSM data and GTFS transit data.

---

## Simulation files

| File | Description |
|------|-------------|
| `input/full_config.xml` | MATSim configuration (pass to Controler) |
| `input/network_with_pt.xml` | Multimodal network including PT links (PTMapper output) |
| `input/transitSchedule.xml` | Mapped PT schedule (PTMapper output) |
| `input/transitVehicles.xml` | PT vehicle fleet (GTFS converter output) |
| `input/plans.xml` | Synthetic population (~1 % sample) |
| `input/plans_170k.xml` | Full population (~170 k agents) |

## Network-building inputs

| Source | Location |
|--------|----------|
| OSM `.pbf` files (Lorraine, Luxembourg, Rheinland-Pfalz, Saarland) | `tools/OSM/` |
| GTFS transit data | `tools/VISUM/GTFS/042026/` |

## How to regenerate

All steps use the self-contained package in `tools/OSM/osm_converter_package/` which bundles JDK 25 and the pt2matsim shaded jar.

### Step 1 — Merge OSM files (osmium-tool)

```bat
osmium merge tools/OSM/lorraine-260412.osm.pbf tools/OSM/luxembourg-260412.osm.pbf ^
             tools/OSM/rheinland-pfalz-260412.osm.pbf tools/OSM/saarland-260412.osm.pbf ^
             -o tools/OSM/merged.osm.pbf
```

### Step 2 — OSM → network.xml

Double-click `tools/OSM/osm_converter_package/run_osm_converter_unc.bat`.
Config: `tools/OSM/osm_converter_config.xml`. Output: `osm_converter_package/output/network_osm.xml` (824 MB).

### Step 3 — GTFS → unmapped schedule

Double-click `tools/OSM/osm_converter_package/run_gtfs_to_schedule.bat`.
Output: `osm_converter_package/output/transitSchedule_unmapped.xml` (682 lines, 2 267 routes), `transitVehicles.xml`.

### Step 4 — Map schedule to network (PTMapper)

Update `tools/pt2matsim-tools/ptmapping_config.xml` `inputNetworkFile` to point to `network_osm.xml`, then run `PublicTransitMapper`.
Output: `input/transitSchedule_mapped.xml`, `input/network_with_pt.xml`.

> See `tools/pt2matsim-tools/README_transit_input_procedure.md` for detailed instructions.

---

## Running the simulation

```bat
mvn -pl matsim exec:java ^
  -Dexec.mainClass=org.matsim.core.controler.Controler ^
  -Dexec.args=scenarios/MMUST/input/full_config.xml
```

Or double-click `run.bat` in `scenarios/MMUST/`. Simulation output: `scenarios/MMUST/output/`.

