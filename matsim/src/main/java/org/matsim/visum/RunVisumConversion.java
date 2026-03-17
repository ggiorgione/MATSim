package org.matsim.visum;

/**
 * Simple runner for VISUM to MATSim conversion
 */
public class RunVisumConversion {
	public static void main(String[] args) {
		String inputFile = "scenarios/MMUST/VISUM/2024_MMUST_PPM.ver"; // input file path
		String outputDir = "scenarios/MMUST/VISUM/Output4MATSim"; // output directory path
		
		Visum2MATSimNetworkConverter converter = new Visum2MATSimNetworkConverter(inputFile, outputDir); // create converter
		converter.convert(); // run conversion
	}
}
