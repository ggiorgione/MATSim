package org.matsim.visum;

import java.io.File; // import File for path operations

/**
 * Simple runner for VISUM to MATSim conversion.
 * Usage: java RunVisumConversion [network|transit]
 *   network  — run only Visum2MATSimNetworkConverter
 *   transit  — run only Visum2MATSimTransitConverter
 *   (no arg) — run both converters in sequence
 */
public class RunVisumConversion {

	private static final String INPUT_CRS = "EPSG:3857"; // CRS of VISUM input coordinates — change to "EPSG:4326" for WGS84
	private static final boolean CAR_ONLY = false; // produce car-only cleaned network — set false for multimodal

	public static void main(String[] args) {
		String workingDir = System.getProperty("user.dir"); // get current working directory
		String inputDir = new File(workingDir, "scenarios/MMUST/VISUM/Input4MATSim").getAbsolutePath(); // construct absolute input path
		String outputDir = new File(workingDir, "scenarios/MMUST/VISUM/Output4MATSim").getAbsolutePath(); // construct absolute output path

		System.out.println("Working directory: " + workingDir); // print working directory for debugging
		System.out.println("Input directory: " + inputDir); // print input path
		System.out.println("Output directory: " + outputDir); // print output path

		String mode = args.length > 0 ? args[0].toLowerCase() : "all"; // read optional first arg to select which converter to run
		boolean runNetwork = mode.equals("all") || mode.equals("network"); // run network converter if mode is all or network
		boolean runTransit = mode.equals("all") || mode.equals("transit"); // run transit converter if mode is all or transit

		if (runNetwork) {
			Visum2MATSimNetworkConverter networkConverter = new Visum2MATSimNetworkConverter(inputDir, outputDir, INPUT_CRS); // create network converter with CRS
			networkConverter.setCarOnly(CAR_ONLY); // apply car-only flag from constant above
			networkConverter.convert(); // run network conversion
		}

		if (runTransit) {
			Visum2MATSimTransitConverter transitConverter = new Visum2MATSimTransitConverter(inputDir, outputDir); // create transit converter (reads outputDir/network.xml for stop coords)
			transitConverter.convert(); // run transit conversion
		}
	}
}
