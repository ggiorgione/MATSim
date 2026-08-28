package org.matsim.run.mmust;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.cadyts.car.CadytsCarModule;
import org.matsim.contrib.cadyts.car.CadytsContext;
import org.matsim.contrib.cadyts.general.CadytsConfigGroup;
import org.matsim.contrib.cadyts.general.CadytsScoring;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.scoring.SumScoringFunction;
import org.matsim.core.scoring.functions.CharyparNagelActivityScoring;
import org.matsim.core.scoring.functions.CharyparNagelAgentStuckScoring;
import org.matsim.core.scoring.functions.CharyparNagelLegScoring;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;

import jakarta.inject.Inject;

/**
 * MMUST entry point that layers cadyts car-count calibration on top of the normal Controler run.
 * The config is otherwise unchanged - this only adds the cadyts scoring correction (car link volumes vs.
 * {@code counts.inputCountsFile}) on top of the CharyparNagel activity/leg/stuck scoring already configured
 * via the {@code scoring} module. Everything else (mobsim, replanning, transit, ...) comes from the config file
 * exactly as with the stock Controler.
 *
 * Usage: same as the stock Controler, one arg - path to the config.xml.
 */
public class RunMMUSTCadyts {

	// CadytsScoring multiplies each calibrated link's offset by this weight and SUMS over every such link
	// traversed in a plan (see CadytsScoring.java: score = weightOfCadytsCorrection * calcLinearPlanEffect(plan)).
	// The MATSim-example default of 30 was never re-derived for this scenario, and R6 showed it dominates mode
	// choice, not just route choice: with car's ASC corrected to 3.5 (validated separately, with the stock
	// Controler, to give car=57.0%), turning cadyts on pulled it back down to 49.5% - almost exactly R1-R5's
	// uncorrected constant=3.0 level. Cause: the 292 calibrated links are mostly national roads/motorways that
	// most car trips cross, and the median measured offset (~0.5-2 raw, from R1/R3/R5) already sits in the same
	// 0.5-1 util range that a full day's mode-choice ASC difference operates in - so at weight=30 a single typical
	// link contributes 15-60 utils, 15-120x the signal that was shown (same three runs) to move car mode share by
	// 8pp on its own. Weight=2 aims to bring that back to the same order of magnitude as the mode-choice signal
	// for a typical link (still enough to matter for route choice) while leaving the extreme tail - offsets ~20+,
	// which is where PCH1407/1487/492's real network defects showed up before they were matching bugs, not
	// genuine demand gaps - still strongly weighted (40+ utils). Not yet empirically re-validated at this weight.
	private static final double CADYTS_WEIGHT = 2.0;

	public static void main(String[] args) {
		final Config config = ConfigUtils.loadConfig(args[0], new CadytsConfigGroup());

		final Scenario scenario = ScenarioUtils.loadScenario(config);

		final Controler controler = new Controler(scenario);
		controler.addOverridingModule(new CadytsCarModule());

		controler.setScoringFunctionFactory(new ScoringFunctionFactory() {
			@Inject
			CadytsContext cadytsContext;
			@Inject
			ScoringParametersForPerson parameters;

			@Override
			public ScoringFunction createNewScoringFunction(Person person) {
				final ScoringParameters params = parameters.getScoringParameters(person);

				SumScoringFunction scoringFunctionAccumulator = new SumScoringFunction();
				scoringFunctionAccumulator.addScoringFunction(
						new CharyparNagelLegScoring(params, controler.getScenario().getNetwork(), config.transit().getTransitModes()));
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelActivityScoring(params));
				scoringFunctionAccumulator.addScoringFunction(new CharyparNagelAgentStuckScoring(params));

				final CadytsScoring<Link> cadytsScoring = new CadytsScoring<>(person.getSelectedPlan(), config, cadytsContext);
				cadytsScoring.setWeightOfCadytsCorrection(CADYTS_WEIGHT * config.scoring().getBrainExpBeta());
				scoringFunctionAccumulator.addScoringFunction(cadytsScoring);

				return scoringFunctionAccumulator;
			}
		});

		controler.run();
	}

}
