/* *********************************************************************** *
 * project: org.matsim.*
 * Visum2MATSimNetworkConverter.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2024 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.visum;

import java.io.BufferedReader; // import BufferedReader for reading CSV files
import java.io.IOException; // import IOException for file operations
import java.nio.charset.StandardCharsets; // import StandardCharsets for file encoding
import org.apache.logging.log4j.LogManager; // import logger to track conversion progress
import org.apache.logging.log4j.Logger; // import Logger class for logging messages
import org.matsim.api.core.v01.Coord; // import Coord to store node coordinates
import org.matsim.api.core.v01.Id; // import Id for creating unique identifiers
import org.matsim.api.core.v01.TransportMode; // import TransportMode for car mode constant
import org.matsim.api.core.v01.network.Link; // import Link to create network links
import org.matsim.api.core.v01.network.Network; // import Network to create the network
import org.matsim.api.core.v01.network.Node; // import Node to create network nodes
import org.matsim.core.config.ConfigUtils; // import ConfigUtils to create config
import org.matsim.core.network.NetworkUtils; // import NetworkUtils for public API node/link creation
import org.matsim.core.network.algorithms.NetworkCleaner; // import NetworkCleaner to remove disconnected components
import org.matsim.core.network.algorithms.TransportModeNetworkFilter; // import filter for car-only subnetwork
import org.matsim.api.core.v01.network.NetworkWriter; // import NetworkWriter API to save network to XML
import org.matsim.core.utils.io.IOUtils; // import IOUtils for file operations
import java.io.File; // import File for path handling
import java.util.ArrayList; // import ArrayList for CSV parsing
import java.util.List; // import List interface
import java.util.Set; // import Set interface for storing modes
import java.util.HashSet; // import HashSet for creating sets
import java.util.Map; // import Map for VISUM to MATSim mode mapping
import java.util.HashMap; // import HashMap for mode mapping static initializer

/**
 * Converts VISUM CSV files to MATSim network.xml.
 * Transit data (schedule, vehicles) is handled separately by Visum2MATSimTransitConverter.
 *
 * @author GitHub Copilot
 */
public class Visum2MATSimNetworkConverter {

	private static final Logger log = LogManager.getLogger(Visum2MATSimNetworkConverter.class); // logger instance for progress tracking

	private final String visumInputDir; // path to VISUM .csv directory
	private final String outputDir; // directory to write output XML files
	private String inputCRS = "EPSG:3857"; // CRS of the input CSV coordinates — change to "EPSG:4326" for WGS84 inputs
	private boolean carOnly = false; // when true, filter to car-only subnetwork and clean before writing


	/**
	 * Constructor initializes converter with input and output paths.
	 * @param visumInputDir path to VISUM .ver file to convert
	 * @param outputDir directory where output files will be written
	 */
	public Visum2MATSimNetworkConverter(final String visumInputDir, final String outputDir) {
		this.visumInputDir = visumInputDir; // store input file path for later use
		this.outputDir = outputDir; // store output directory path for later use
	}

	/**
	 * Constructor with explicit input CRS.
	 * @param visumInputDir path to VISUM CSV directory
	 * @param outputDir directory where output files will be written
	 * @param inputCRS CRS of the input CSV coordinates, e.g. "EPSG:4326" or "EPSG:3857"
	 */
	public Visum2MATSimNetworkConverter(final String visumInputDir, final String outputDir, final String inputCRS) {
		this.visumInputDir = visumInputDir; // store input directory
		this.outputDir = outputDir; // store output directory
		this.inputCRS = inputCRS; // store input CRS for coordinate handling
	}

	/**
	 * Sets whether to produce a car-only network.
	 * When true: filters to car links, then runs NetworkCleaner so every node is reachable.
	 * When false (default): keeps all modes, suitable for multimodal runs.
	 * @param carOnly true for car-only, false for multimodal
	 */
	public void setCarOnly(final boolean carOnly) {
		this.carOnly = carOnly; // store car-only flag for use in convert()
	}

	/**
	 * Executes the full conversion: reads Nodes.csv and Links.csv, optionally filters to
	 * car-only, cleans the network, and writes network.xml.
	 */
	public void convert() {
		log.info("Starting VISUM to MATSim network conversion"); // log conversion start
		log.info("Input directory: " + visumInputDir); // log input directory
		log.info("Output directory: " + outputDir); // log output directory
		log.info("Input CRS: " + inputCRS); // log coordinate system being used
		log.info("Car-only mode: " + carOnly); // log whether car-only filter is active

		File outDir = new File(outputDir); // create File object for output directory
		if (!outDir.exists()) { // check if directory doesn't exist
			outDir.mkdirs(); // create directories (including parents) recursively
		}

		Network network = NetworkUtils.createNetwork(ConfigUtils.createConfig().network()); // create empty network directly (no full Scenario needed)

		readNodesFromCSV(network, visumInputDir + File.separator + "Nodes.csv"); // read nodes from Nodes.csv
		log.info("Nodes loaded: " + network.getNodes().size()); // log node count

		readLinksFromCSV(network, visumInputDir + File.separator + "Links.csv"); // read links from Links.csv
		log.info("Links loaded: " + network.getLinks().size()); // log link count

		if (carOnly) { // check if car-only output is requested
			log.info("Filtering to car-only subnetwork..."); // log filter start
			TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network); // create mode filter from full multimodal network
			Network carNetwork = NetworkUtils.createNetwork(ConfigUtils.createConfig().network()); // create empty target network for car links only
			Set<String> modes = new HashSet<>(); // create set for allowed modes
			modes.add(TransportMode.car); // add only car mode to the filter set
			filter.filter(carNetwork, modes); // extract car-only subnetwork into carNetwork
			network = carNetwork; // replace multimodal network with car-only result
			log.info("Car-only filter applied: " + network.getNodes().size() + " nodes, " + network.getLinks().size() + " links"); // log filtered size
		}

		new NetworkCleaner().run(network); // remove disconnected components so every remaining node is reachable
		log.info("Network cleaned: " + network.getNodes().size() + " nodes, " + network.getLinks().size() + " links remaining"); // log cleaned size

		writeNetwork(network); // save final network to network.xml
		log.info("Conversion completed successfully"); // log completion
	}

	/**
	 * Converts WGS84 (longitude, latitude in degrees) to EPSG:3857 (Web Mercator, meters).
	 * Plans use EPSG:3857; VISUM exports use WGS84 degrees — this aligns the coordinate systems.
	 */
	private Coord wgs84ToWebMercator(final double lon, final double lat) {
		double x = lon * 20037508.34 / 180.0; // convert longitude degrees to meters (x axis)
		double latRad = Math.toRadians(lat); // convert latitude to radians for the Mercator formula
		double y = Math.log(Math.tan(Math.PI / 4.0 + latRad / 2.0)) * 6378137.0; // Mercator y from latitude
		return new Coord(x, y); // return projected coordinate in EPSG:3857 meters
	}

	/**
	 * Reads nodes from Nodes.csv and adds them to the network.
	 * Maps VISUM columns: NO -> id, XCOORD -> x, YCOORD -> y, TYPENO -> type
	 * @param network the network to add nodes to
	 * @param nodesFile path to the Nodes.csv file
	 */
	private void readNodesFromCSV(final Network network, final String nodesFile) {
		try (BufferedReader reader = IOUtils.getBufferedReader(new File(nodesFile).toURI().toURL(), StandardCharsets.ISO_8859_1)) { // open CSV file with proper encoding
			String headerLine = reader.readLine(); // read header row
			if (headerLine == null) { // check if file is empty
				log.warn("Nodes.csv file is empty"); // warn about empty file
				return; // exit method
			}
			
			String[] headers = parseCSVLine(headerLine); // split header by comma handling quotes
			int noIdx = getColumnIndex(headers, "NO"); // find NO column index
			int xIdx = getColumnIndex(headers, "XCOORD"); // find XCOORD column index
			int yIdx = getColumnIndex(headers, "YCOORD"); // find YCOORD column index
			int typeIdx = getColumnIndex(headers, "TYPENO"); // find TYPENO column index
			
			// Validate required columns exist
			if (noIdx == -1 || xIdx == -1 || yIdx == -1) { // check if required columns found
				log.error("Required columns missing in Nodes.csv. Found NO: " + (noIdx >= 0) + ", XCOORD: " + (xIdx >= 0) + ", YCOORD: " + (yIdx >= 0)); // log error
				return; // exit method
			}
			
			String line; // variable for reading lines
			int nodeCount = 0; // counter for created nodes
			int lineNum = 1; // line counter for error reporting
			while ((line = reader.readLine()) != null) { // loop through all data rows
				lineNum++; // increment line counter
				if (line.trim().isEmpty()) continue; // skip empty lines
				
				String[] parts = parseCSVLine(line); // parse CSV line handling quotes
				if (parts.length <= Math.max(Math.max(noIdx, xIdx), yIdx)) { // check if row has enough columns
					log.warn("Line " + lineNum + " has insufficient columns"); // log warning
					continue; // skip incomplete rows
				}
				
				try {
				String nodeNo = parts[noIdx].trim(); // extract node number (NO -> id)
					double x = Double.parseDouble(parts[xIdx].trim()); // parse X coordinate value
					double y = Double.parseDouble(parts[yIdx].trim()); // parse Y coordinate value
					
					// Convert to EPSG:3857 only when input is WGS84 (EPSG:4326); otherwise use coordinates as-is
					Coord coord = "EPSG:4326".equals(inputCRS) ? wgs84ToWebMercator(x, y) : new Coord(x, y); // project if needed
					Node node = NetworkUtils.createNode(Id.create(nodeNo, Node.class), coord); // create node using public API
					
					// Set node type attribute (TYPENO -> type)
					if (typeIdx >= 0 && typeIdx < parts.length) { // check if TYPENO column exists
						String typeStr = parts[typeIdx].trim(); // extract type value
						if (!typeStr.isEmpty()) { // check if type is not empty
							node.getAttributes().putAttribute("type", typeStr); // set type attribute
						}
					}
					
					network.addNode(node); // add node to network
					nodeCount++; // increment counter
				} catch (NumberFormatException e) { // handle parsing errors
					log.warn("Line " + lineNum + ": Error parsing node coordinates: " + e.getMessage()); // log warning with line number
				}
			}
			log.info("Created " + nodeCount + " nodes from Nodes.csv"); // log number of nodes created
		} catch (IOException e) { // handle file reading errors
			log.error("Error reading Nodes.csv: " + e.getMessage(), e); // log error with exception
		}
	}

	/**
	 * Reads links from Links.csv and adds them to the network.
	 * Maps VISUM columns: FROMNODENO -> fromNode, TONODENO -> toNode, LENGTH -> length (km*1000->m),
	 * V0PRT -> freespeed (km/h/3.6->m/s), CAPPRT -> capacity, TSYSSET -> modes
	 * @param network the network to add links to
	 * @param linksFile path to the Links.csv file
	 */
	private void readLinksFromCSV(final Network network, final String linksFile) {
		try (BufferedReader reader = IOUtils.getBufferedReader(new File(linksFile).toURI().toURL(), StandardCharsets.ISO_8859_1)) { // open CSV file with proper encoding
			String headerLine = reader.readLine(); // read header row
			if (headerLine == null) { // check if file is empty
				log.warn("Links.csv file is empty"); // warn about empty file
				return; // exit method
			}
			
			String[] headers = parseCSVLine(headerLine); // split header by comma handling quotes
			int fromIdx = getColumnIndex(headers, "FROMNODENO"); // find FROMNODENO column index
			int toIdx = getColumnIndex(headers, "TONODENO"); // find TONODENO column index
			int lengthIdx = getColumnIndex(headers, "LENGTH"); // find LENGTH column index
			int v0Idx = getColumnIndex(headers, "V0PRT"); // find V0PRT (speed) column index
			int capIdx = getColumnIndex(headers, "CAPPRT"); // find CAPPRT (capacity) column index
			int tsysIdx = getColumnIndex(headers, "TSYSSET"); // find TSYSSET (modes) column index
			int numLanesIdx = getColumnIndex(headers, "NUMLANES"); // find NUMLANES column index
			int linkNoIdx = getColumnIndex(headers, "fid"); // find fid (link ID) column index — fid is unique per row, NO is shared between both directions
			
			// Validate required columns exist
			if (fromIdx == -1 || toIdx == -1 || lengthIdx == -1) { // check if required routing columns found
				log.error("Required columns missing in Links.csv. Found FROMNODENO: " + (fromIdx >= 0) + ", TONODENO: " + (toIdx >= 0) + ", LENGTH: " + (lengthIdx >= 0)); // log error
				return; // exit method
			}
			
			String line; // variable for reading lines
			int linkCount = 0; // counter for created links
			int lineNum = 1; // line counter for error reporting
			while ((line = reader.readLine()) != null) { // loop through all data rows
				lineNum++; // increment line counter
				if (line.trim().isEmpty()) continue; // skip empty lines
				
				String[] parts = parseCSVLine(line); // parse CSV line handling quotes
				int maxIdx = Math.max(Math.max(fromIdx, toIdx), lengthIdx); // find maximum required column index
				if (parts.length <= maxIdx) { // check if row has enough columns
					log.warn("Line " + lineNum + " has insufficient columns"); // log warning
					continue; // skip incomplete rows
				}
				
				try {
					String fromNodeNo = parts[fromIdx].trim(); // extract from-node number
					String toNodeNo = parts[toIdx].trim(); // extract to-node number
					String lengthStr = parts[lengthIdx].trim(); // get length string (may include unit like "km")
					lengthStr = lengthStr.replaceAll("[^0-9.]", ""); // strip all non-numeric characters (units like "km")
					double lengthKm = Double.parseDouble(lengthStr); // parse length value in km
					double lengthM = lengthKm * 1000.0; // convert length from km to m
					
					// Parse optional speed (V0PRT in km/h, convert to m/s)
					double freespeedMs = 13.89; // default speed (50 km/h = 13.89 m/s)
					if (v0Idx >= 0 && v0Idx < parts.length && !parts[v0Idx].trim().isEmpty()) { // check if V0PRT column exists and has value
						String speedStr = parts[v0Idx].trim(); // get speed string (may include unit)
						speedStr = speedStr.replaceAll("[^0-9.]", ""); // strip all non-numeric characters
						double v0KmH = Double.parseDouble(speedStr); // parse speed in km/h (V0PRT -> freespeed)
						freespeedMs = v0KmH / 3.6; // convert from km/h to m/s
					}
					
					// Parse optional capacity (vehicles per hour, convert to vehicles per 3600 seconds)
					double capacity = 2000.0; // default capacity
					if (capIdx >= 0 && capIdx < parts.length && !parts[capIdx].trim().isEmpty()) { // check if CAPPRT column exists and has value
						String capStr = parts[capIdx].trim(); // get capacity string (may include unit)
						capStr = capStr.replaceAll("[^0-9.]", ""); // strip all non-numeric characters
						capacity = Double.parseDouble(capStr); // extract capacity in vehicles per hour (CAPPRT -> capacity)
					}
					
					// Parse optional number of lanes
					double numLanes = 1.0; // default number of lanes
					if (numLanesIdx >= 0 && numLanesIdx < parts.length && !parts[numLanesIdx].trim().isEmpty()) { // check if NUMLANES column exists and has value
						String lanesStr = parts[numLanesIdx].trim(); // get lanes string (may include unit)
						lanesStr = lanesStr.replaceAll("[^0-9.]", ""); // strip all non-numeric characters
						numLanes = Double.parseDouble(lanesStr); // extract number of lanes
					}
					
					// Get from and to nodes from network
					Node fromNode = network.getNodes().get(Id.createNodeId(fromNodeNo)); // retrieve from-node by id
					Node toNode = network.getNodes().get(Id.createNodeId(toNodeNo)); // retrieve to-node by id
					
					if (fromNode == null || toNode == null) { // check if both nodes exist
						log.warn("Line " + lineNum + ": Node not found - from: " + fromNodeNo + ", to: " + toNodeNo); // log warning about missing nodes
						continue; // skip link if nodes missing
					}
					
					// Create link ID using fid if available (fid is unique per row), otherwise use from-to format
					String linkId; // variable for link id
					if (linkNoIdx >= 0 && linkNoIdx < parts.length && !parts[linkNoIdx].trim().isEmpty()) { // check if fid column exists
						linkId = parts[linkNoIdx].trim(); // use fid as link id (unique per direction)
					} else { // if fid not available
						linkId = fromNodeNo + "_" + toNodeNo; // create id from node numbers
					}
					
					// Create link using public API
					Link link = NetworkUtils.createLink(Id.createLinkId(linkId), fromNode, toNode, network, lengthM, freespeedMs, capacity, numLanes); // create link with transformed values
					
					// Parse TSYSSET and map to MATSim modes
					if (tsysIdx >= 0 && tsysIdx < parts.length && !parts[tsysIdx].trim().isEmpty()) { // check if TSYSSET column exists
						String visumModes = parts[tsysIdx].trim(); // extract TSYSSET string
						String matsimModes = mapVisumModesToMATSimModes(visumModes); // convert VISUM modes to MATSim modes
						if (!matsimModes.isEmpty()) { // check if any modes were mapped
							link.setAllowedModes(java.util.Arrays.stream(matsimModes.split(",")).collect(java.util.stream.Collectors.toSet())); // set modes on link
						}
					}
					
					network.addLink(link); // add link to network
					linkCount++; // increment counter

				} catch (NumberFormatException e) { // handle parsing errors
					log.warn("Line " + lineNum + ": Error parsing link data: " + e.getMessage()); // log warning with line number
				}
			}
			log.info("Created " + linkCount + " links from Links.csv"); // log number of links created
		} catch (IOException e) { // handle file reading errors
			log.error("Error reading Links.csv: " + e.getMessage(), e); // log error with exception
		}
	}

	/**
	 * Finds the index of a column by name (case-insensitive) in the header array.
	 */
	static int getColumnIndex(final String[] headers, final String columnName) {
		for (int i = 0; i < headers.length; i++) { // iterate through all header columns
			if (headers[i].trim().equalsIgnoreCase(columnName)) { // check for case-insensitive match
				return i; // return the index when found
			}
		}
		return -1; // return -1 if not found
	}

	/**
	 * Parses a CSV line handling quoted fields and escaped quotes.
	 */
	static String[] parseCSVLine(final String line) {
		List<String> fields = new ArrayList<>(); // create list to store fields
		StringBuilder current = new StringBuilder(); // build current field
		boolean inQuotes = false; // track if inside quoted field
		
		for (int i = 0; i < line.length(); i++) { // iterate through each character
			char c = line.charAt(i); // get current character
			
			if (c == '"') { // check for quote character
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') { // check for escaped quote
					current.append('"'); // append single quote
					i++; // skip next quote
				} else {
					inQuotes = !inQuotes; // toggle quote state
				}
			} else if (c == ',' && !inQuotes) { // check for comma outside quotes
				fields.add(current.toString()); // add field to list
				current = new StringBuilder(); // reset builder
			} else {
				current.append(c); // append character to current field
			}
		}
		fields.add(current.toString()); // add final field
		
		return fields.toArray(new String[0]); // convert list to array
	}

	/**
	 * Maps VISUM TSYSSET modes to MATSim standard modes.
	 * Mapping for MMUST Luxembourg dataset:
	 *   B           -> bus   (bus transport system)
	 *   Bike        -> bike
	 *   R           -> rail  (rail infrastructure)
	 *   M*          -> walk  (pedestrian paths)
	 *   Covoit*     -> ride  (carpool)
	 *   PL*         -> truck (poids lourds = heavy goods vehicles)
	 *   V*          -> car   (voiture = car, all sub-variants)
	 * @param visumModes comma-separated VISUM mode codes (TSYSSET)
	 * @return comma-separated MATSim mode codes (unique, sorted)
	 */
	private String mapVisumModesToMATSimModes(final String visumModes) {
		// Create static mapping table from VISUM codes to MATSim modes
		Map<String, String> modeMap = new HashMap<>(); // create mapping table
		modeMap.put("B", "bus"); // B -> bus (bus public transport line)
		modeMap.put("Bike", "bike"); // Bike -> bike mode
		modeMap.put("Covoit", "ride"); // Covoit -> ride (carpool)
		modeMap.put("Covoit_tribut", "ride"); // carpool tributary -> ride
		modeMap.put("Covoit_echange", "ride"); // carpool exchange -> ride
		modeMap.put("Covoit_front", "ride"); // carpool frontier -> ride
		modeMap.put("Covoit_non_tribut", "ride"); // carpool non-tributary -> ride
		modeMap.put("Covoit_transit", "ride"); // carpool transit -> ride
		modeMap.put("PL", "truck"); // PL -> truck (poids lourds = heavy goods vehicle)
		modeMap.put("PL_tribut", "truck"); // heavy vehicle tributary -> truck
		modeMap.put("PL_nonTribut", "truck"); // heavy vehicle non-tributary -> truck
		modeMap.put("V", "car"); // V -> car (voiture = car)
		modeMap.put("VS_Tribut", "car"); // car tributary sub-type -> car
		modeMap.put("V_echange", "car"); // car exchange -> car
		modeMap.put("V_front_BE_FR", "car"); // car Belgium-France frontier -> car
		modeMap.put("V_front_BE_LU", "car"); // car Belgium-Luxembourg frontier -> car
		modeMap.put("V_front_fr_1", "car"); // car France frontier 1 -> car
		modeMap.put("V_front_fr_2", "car"); // car France frontier 2 -> car
		modeMap.put("V_front_fr_3", "car"); // car France frontier 3 -> car
		modeMap.put("V_interne_LU", "car"); // car internal Luxembourg -> car
		modeMap.put("V_non_frontalier", "car"); // car non-frontier -> car
		modeMap.put("V_transit", "car"); // car transit -> car
		modeMap.put("R", "rail"); // R -> rail (rail infrastructure)
		modeMap.put("M", "walk"); // M -> walk (pedestrian paths)
		modeMap.put("M_foot", "walk"); // pedestrian foot variant -> walk
		modeMap.put("M_pedestrian", "walk"); // pedestrian variant -> walk
		modeMap.put("M_shared", "walk"); // shared pedestrian path -> walk
		
		Set<String> matsimModes = new HashSet<>(); // create set for mapped modes (auto removes duplicates)
		String[] visumModeArray = visumModes.split(","); // split TSYSSET by comma
		for (String visumMode : visumModeArray) { // iterate through each VISUM mode
			String trimmed = visumMode.trim(); // remove whitespace
			String matsimMode = modeMap.get(trimmed); // lookup MATSim mode
			if (matsimMode != null) { // check if mapping exists
				matsimModes.add(matsimMode); // add MATSim mode to set (auto removes duplicates)
			} else if (!trimmed.isEmpty()) { // unknown code — warn once
				log.warn("Unknown VISUM TSYSSET code (no MATSim mapping): '" + trimmed + "'"); // log unknown code
			}
		}
		
		// Return comma-separated modes sorted for consistency
		return matsimModes.stream() // convert set to stream
			.sorted() // sort alphabetically for consistency
			.reduce((a, b) -> a + "," + b) // join with commas
			.orElse(""); // return empty string if no modes mapped
	}

	/**
	 * Writes the network to network.xml in the output directory.
	 */
	private void writeNetwork(final Network network) {
		String outputFile = outputDir + File.separator + "network.xml"; // construct output file path
		log.info("Writing network to: " + outputFile); // log output location
		new NetworkWriter(network).write(outputFile); // write network (v2 format includes node attributes inline)
		log.info("Wrote " + network.getNodes().size() + " nodes, " + network.getLinks().size() + " links"); // log written size
	}

	/**
	 * Main entry point.
	 * Usage: java Visum2MATSimNetworkConverter &lt;input-dir&gt; &lt;output-dir&gt; [input-crs] [car-only]
	 *   input-crs : EPSG:3857 (default) or EPSG:4326 for WGS84 inputs
	 *   car-only  : true/yes — filter to car-only network and run NetworkCleaner (default: false/multimodal)
	 */
	public static void main(final String[] args) {
		if (args.length < 2) { // check if required arguments are present
			System.err.println("Usage: java Visum2MATSimNetworkConverter <input-dir> <output-dir> [input-crs] [car-only]"); // print usage
			System.exit(1); // exit with error code
		}

		String inputDir = args[0]; // extract input directory
		String outputDir = args[1]; // extract output directory
		String inputCRS = args.length >= 3 ? args[2] : "EPSG:3857"; // use 3rd arg as CRS or default to EPSG:3857
		boolean carOnly = args.length >= 4 && (args[3].equalsIgnoreCase("true") || args[3].equalsIgnoreCase("yes")); // parse car-only flag from optional 4th arg

		Visum2MATSimNetworkConverter converter = new Visum2MATSimNetworkConverter(inputDir, outputDir, inputCRS); // create converter with CRS
		converter.setCarOnly(carOnly); // apply car-only setting
		converter.convert(); // execute conversion
	}
}

