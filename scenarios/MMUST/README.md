# MMUST — Luxembourg MATSim Scenario

Multimodal MATSim scenario for Luxembourg built from OSM data and GTFS transit data.

---

## Simulation files

| File | Description |
|------|-------------|
| `input/full_config.xml` | MATSim configuration (pass to Controler) |
| `input/network_with_pt.xml` | Multimodal network including PT links (PTMapper output) |
| `input/transitSchedule_mapped.xml` | Mapped PT schedule (PTMapper output) |
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

### Steps 3–5 — PT schedule (automated)

Double-click `tools/pt2matsim-tools/run_full_pt_pipeline.bat`. This runs all five PT steps in sequence:

1. GTFS → unmapped schedule for each of the four countries (BE, FR, GE, LU)
2. Merge the four schedules into `transitSchedule_unmapped_merged.xml`
3. Map merged schedule onto the OSM network → `transitSchedule_mapped.xml` + `network_with_pt.xml`
4. Deploy outputs to `scenarios/MMUST/input/`
5. Remove loop-link routes (fixes `UmlaufInterpolator` crash at MATSim startup)

**Prerequisites**: `tools/pt2matsim-tools/input/GTFS/{BE,FR,GE,LU}/` populated with GTFS feeds; `ptmapping_config.xml` `inputNetworkFile` pointing to `network_osm.xml`.

> See `tools/pt2matsim-tools/README_transit_input_procedure.md` for detailed per-step instructions and the full pipeline diagram.

---

## Running the simulation

```bat
mvn -pl matsim exec:java ^
  -Dexec.mainClass=org.matsim.core.controler.Controler ^
  -Dexec.args=scenarios/MMUST/input/full_config.xml
```

Or double-click `run.bat` in `scenarios/MMUST/`. Simulation output: `scenarios/MMUST/output/`.

## Cadyts car-count calibration

`input/counts_car_2017.xml` (174 PCH stations, directionally matched to network links) enables count-based
calibration on top of the S2 mode-share ASC tuning. Cadyts needs Java wiring (a custom `ScoringFunctionFactory`)
that a config file alone can't express, so it does **not** run through the stock `Controler` - use the dedicated
`mmust-run` module and its config fork, `input/full_config_hermes_cadyts.xml` (S2-R20 baseline + `cadytsCar`
module + `counts.inputCountsFile`; `input/full_config_hermes.xml` itself is untouched, so the S2 R-sequence
stays intact).

Run locally from source:

```bat
mvn -pl mmust-run -am exec:java ^
  -Dexec.mainClass=org.matsim.run.mmust.RunMMUSTCadyts ^
  -Dexec.args=scenarios/MMUST/input/full_config_hermes_cadyts.xml
```

Build a self-contained jar (e.g. to copy to a server for a run on a larger population):

```bat
mvn -pl mmust-run -am package -DskipTests
```

produces `mmust-run/target/mmust-run.jar` (`Main-Class: org.matsim.run.mmust.RunMMUSTCadyts`). Run it directly:

```bat
java -Xmx<size>g -jar mmust-run.jar path\to\config.xml
```

**Before scaling up the population**, `counts.countsScaleFactor` (currently `100`, in both `full_config_hermes.xml`
and the cadyts fork) must be recomputed for the new sample rate - it scales simulated counts up to match the real
counts that both cadyts and the `counts` module compare against, and is currently set for the 1% sample
(`pop_to_reach=12620`).

Output: link cost offsets and flow-analysis diagnostics are written per iteration
(`linkCostOffsets.xml`, `flowAnalysis.txt`, at `counts.writeCountsInterval`); plain counts-vs-simulation comparison
files come from the standard `counts` module output.

