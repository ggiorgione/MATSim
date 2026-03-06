# my-scenario

Minimal MATSim scenario scaffold created for local development.

Structure
- `input/` — `network.xml`, `plans.xml`
- `config.xml` — MATSim config referencing `input/`
- `output/` — simulation output (created at runtime)

Build and run (from workspace root):

```bash
mvn -pl matsim -am -DskipTests package
mvn -pl matsim -am exec:java -Dexec.mainClass=org.matsim.run.RunMatsim -Dexec.args="scenarios/my-scenario/config.xml"
```

Or run `org.matsim.run.RunMatsim` from your IDE and pass `scenarios/my-scenario/config.xml` as the program argument.
