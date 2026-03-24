/* *********************************************************************** *
 * project: org.matsim.*
 * Visum2MATSimTransitConverter.java
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
import java.io.File; // import File for path handling
import java.io.IOException; // import IOException for file operations
import java.nio.charset.StandardCharsets; // import StandardCharsets for file encoding
import java.util.ArrayList; // import ArrayList for building stop lists
import java.util.HashMap; // import HashMap for route-to-stops mapping
import java.util.List; // import List interface
import java.util.Map; // import Map interface
import org.apache.logging.log4j.LogManager; // import logger to track conversion progress
import org.apache.logging.log4j.Logger; // import Logger class for logging messages
import org.matsim.api.core.v01.Coord; // import Coord for stop coordinates
import org.matsim.api.core.v01.Id; // import Id for creating unique identifiers
import org.matsim.api.core.v01.network.Network; // import Network for stop coordinate lookup
import org.matsim.api.core.v01.network.Node; // import Node for coordinate lookup by NODENO
import org.matsim.core.config.ConfigUtils; // import ConfigUtils to create minimal config
import org.matsim.core.network.io.MatsimNetworkReader; // import reader to load existing network.xml
import org.matsim.core.network.NetworkUtils; // import NetworkUtils to create empty network
import org.matsim.core.scenario.ScenarioUtils; // import ScenarioUtils to create scenario
import org.matsim.core.utils.io.IOUtils; // import IOUtils for file operations
import org.matsim.pt.transitSchedule.api.Departure; // import Departure for PT departures
import org.matsim.pt.transitSchedule.api.TransitLine; // import TransitLine for transit lines
import org.matsim.pt.transitSchedule.api.TransitRoute; // import TransitRoute for transit routes
import org.matsim.pt.transitSchedule.api.TransitRouteStop; // import TransitRouteStop for stop-in-route
import org.matsim.pt.transitSchedule.api.TransitSchedule; // import TransitSchedule container
import org.matsim.pt.transitSchedule.api.TransitStopFacility; // import TransitStopFacility for PT stops
import org.matsim.vehicles.Vehicle; // import Vehicle for transit vehicles
import org.matsim.vehicles.VehicleType; // import VehicleType for vehicle capacity definitions
import org.matsim.vehicles.Vehicles; // import Vehicles container

/**
 * Converts VISUM CSV transit data to MATSim transitSchedule.xml and transitVehicles.xml.
 * Requires a network.xml (produced by Visum2MATSimNetworkConverter) to look up stop coordinates.
 * Run Visum2MATSimNetworkConverter first, then run this converter.
 *
 * @author GitHub Copilot
 */
public class Visum2MATSimTransitConverter {

	private static final Logger log = LogManager.getLogger(Visum2MATSimTransitConverter.class); // logger for progress tracking

	private final String visumInputDir; // path to directory containing VISUM CSV files
	private final String outputDir; // directory where transitSchedule.xml and transitVehicles.xml will be written
	private final String networkFile; // path to network.xml used for stop coordinate lookup

	/**
	 * Constructor using the default network.xml location (outputDir/network.xml).
	 * @param visumInputDir path to VISUM CSV directory containing StopPoints.csv, LineRoutes.csv, etc.
	 * @param outputDir directory where output files will be written (also where network.xml is expected)
	 */
	public Visum2MATSimTransitConverter(final String visumInputDir, final String outputDir) {
		this.visumInputDir = visumInputDir; // store input directory
		this.outputDir = outputDir; // store output directory
		this.networkFile = outputDir + File.separator + "network.xml"; // default: expect network.xml in outputDir
	}

	/**
	 * Constructor with explicit network file path.
	 * @param visumInputDir path to VISUM CSV directory
	 * @param outputDir directory where output files will be written
	 * @param networkFile path to the network.xml file for stop coordinate lookup
	 */
	public Visum2MATSimTransitConverter(final String visumInputDir, final String outputDir, final String networkFile) {
		this.visumInputDir = visumInputDir; // store input directory
		this.outputDir = outputDir; // store output directory
		this.networkFile = networkFile; // store explicit network file path
	}

	/**
	 * Executes the full transit conversion: loads network for coordinates, reads stop and route
	 * CSVs, then writes transitSchedule.xml and transitVehicles.xml.
	 */
	public void convert() {
		log.info("Starting VISUM to MATSim transit conversion"); // log start
		log.info("Input directory: " + visumInputDir); // log input directory
		log.info("Output directory: " + outputDir); // log output directory
		log.info("Network file: " + networkFile); // log which network will be used for stop coordinates

		Network network = loadNetwork(); // load network to look up NODENO -> coordinates for stops

		org.matsim.api.core.v01.Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig()); // create scenario to hold schedule and vehicles
		TransitSchedule schedule = scenario.getTransitSchedule(); // get transit schedule container from scenario
		Vehicles vehicles = scenario.getTransitVehicles(); // get transit vehicles container from scenario

		readStopsFromCSV(schedule, network, visumInputDir + File.separator + "StopPoints.csv"); // create stop facilities with real coordinates
		log.info("Transit stops created: " + schedule.getFacilities().size()); // log stop count

		readLineRoutesFromCSV(schedule, vehicles, visumInputDir + File.separator + "LineRoutes.csv"); // create transit lines and routes
		log.info("Transit lines created: " + schedule.getTransitLines().size()); // log line count

		writeTransitSchedule(schedule); // write transitSchedule.xml
		writeVehicles(vehicles); // write transitVehicles.xml
		log.info("Transit conversion completed successfully"); // log completion
	}

	/**
	 * Loads the network from networkFile so stop facilities can use real node coordinates.
	 * Returns an empty network if the file does not exist (stops will default to (0,0)).
	 */
	private Network loadNetwork() {
		Network network = NetworkUtils.createNetwork(ConfigUtils.createConfig().network()); // create empty network to populate
		File f = new File(networkFile); // create File object to check existence
		if (!f.exists()) { // check if network file was found
			log.warn("Network file not found: " + networkFile + " — stops will have coordinates (0,0)"); // warn that coordinates will be wrong
			return network; // return empty network so stop creation can still proceed
		}
		new MatsimNetworkReader(network).readFile(networkFile); // read network XML into the network object
		log.info("Network loaded: " + network.getNodes().size() + " nodes from " + networkFile); // log loaded node count
		return network; // return populated network
	}

	/**
	 * Reads StopPoints.csv and creates TransitStopFacility objects using NODENO to look up real coordinates.
	 * @param schedule the transit schedule to populate
	 * @param network the network for coordinate lookup (NODENO -> node.getCoord())
	 * @param stopPointsFile path to StopPoints.csv
	 */
	private void readStopsFromCSV(final TransitSchedule schedule, final Network network, final String stopPointsFile) {
		try (BufferedReader reader = IOUtils.getBufferedReader(new File(stopPointsFile).toURI().toURL(), StandardCharsets.ISO_8859_1)) { // open StopPoints.csv
			String headerLine = reader.readLine(); // read header row
			if (headerLine == null) return; // exit if file is empty

			String[] headers = Visum2MATSimNetworkConverter.parseCSVLine(headerLine); // parse header using shared helper
			int noIdx = Visum2MATSimNetworkConverter.getColumnIndex(headers, "NO"); // stop point number column
			int nodeNoIdx = Visum2MATSimNetworkConverter.getColumnIndex(headers, "NODENO"); // network node number — used for coordinates
			int nameIdx = Visum2MATSimNetworkConverter.getColumnIndex(headers, "NAME"); // stop name column (optional)

			if (noIdx == -1 || nodeNoIdx == -1) { // both NO and NODENO are required
				log.error("StopPoints.csv missing required columns NO or NODENO"); // log error
				return; // cannot proceed without these columns
			}

			String line; // variable for reading lines
			int stopCount = 0; // counter for created stop facilities
			int missingNodeCount = 0; // counter for stops where NODENO didn't match any network node
			while ((line = reader.readLine()) != null) { // loop through all stop point rows
				if (line.trim().isEmpty()) continue; // skip empty lines

				String[] parts = Visum2MATSimNetworkConverter.parseCSVLine(line); // parse this data row
				if (parts.length <= Math.max(noIdx, nodeNoIdx)) continue; // skip rows too short

				String stopPointNo = parts[noIdx].trim(); // extract stop point ID
				String nodeNo = parts[nodeNoIdx].trim(); // extract network node ID for coordinate lookup
				String stopName = (nameIdx >= 0 && nameIdx < parts.length) ? parts[nameIdx].trim() : stopPointNo; // use name if available, fall back to ID

				Coord coord = new Coord(0, 0); // default coordinate when node not found
				if (!nodeNo.isEmpty()) { // only look up if NODENO is present
					Node node = network.getNodes().get(Id.createNodeId(nodeNo)); // look up node by NODENO
					if (node != null) { // node found in network
						coord = node.getCoord(); // use the real coordinate from the network node
					} else { // node not found in network
						missingNodeCount++; // count stops with missing node (happens for nodes removed by NetworkCleaner)
					}
				}

				TransitStopFacility stop = schedule.getFactory().createTransitStopFacility(
						Id.create(stopPointNo, TransitStopFacility.class), coord, false); // create stop facility with looked-up coordinate
				stop.setName(stopName); // set human-readable stop name
				schedule.addStopFacility(stop); // register stop in the schedule
				stopCount++; // count created facilities
			}
			log.info("Created " + stopCount + " transit stop facilities"); // report total created
			if (missingNodeCount > 0) { // warn if some nodes were missing
				log.warn(missingNodeCount + " stops had NODENO not found in network — those stops remain at (0,0)"); // warn about (0,0) stops
			}
		} catch (IOException e) { // handle file reading errors
			log.error("Error reading StopPoints.csv: " + e.getMessage(), e); // log the error
		}
	}

	/**
	 * Reads LineRoutes.csv and creates TransitLine objects with TransitRoute entries.
	 * Stop sequences are read from LineRouteItems.csv if present; otherwise routes have no stops.
	 * @param schedule the transit schedule to populate
	 * @param vehicles the vehicles container for creating vehicle types and instances
	 * @param lineRoutesFile path to LineRoutes.csv
	 */
	private void readLineRoutesFromCSV(final TransitSchedule schedule, final Vehicles vehicles, final String lineRoutesFile) {
		Map<String, List<String>> routeStopSequences = readLineRouteItemsCSV(visumInputDir + File.separator + "LineRouteItems.csv"); // read stop sequences per route (may be empty if file missing)

		try (BufferedReader reader = IOUtils.getBufferedReader(new File(lineRoutesFile).toURI().toURL(), StandardCharsets.ISO_8859_1)) { // open LineRoutes.csv
			String headerLine = reader.readLine(); // read header row
			if (headerLine == null) return; // exit if file is empty

			String[] headers = Visum2MATSimNetworkConverter.parseCSVLine(headerLine); // parse header
			int lineNameIdx = Visum2MATSimNetworkConverter.getColumnIndex(headers, "LINENAME"); // column: line name
			int routeNameIdx = Visum2MATSimNetworkConverter.getColumnIndex(headers, "NAME"); // column: route name

			if (lineNameIdx == -1 || routeNameIdx == -1) { // both columns are required
				log.error("LineRoutes.csv missing required columns LINENAME or NAME"); // log error
				return; // cannot proceed without line/route names
			}

			createDefaultVehicleType(vehicles, "bus"); // ensure bus vehicle type exists before creating lines

			String line; // variable for reading lines
			int routeCount = 0; // counter for created routes
			while ((line = reader.readLine()) != null) { // loop through all line route rows
				if (line.trim().isEmpty()) continue; // skip empty lines

				String[] parts = Visum2MATSimNetworkConverter.parseCSVLine(line); // parse this data row
				if (parts.length <= Math.max(lineNameIdx, routeNameIdx)) continue; // skip rows too short

				String lineName = parts[lineNameIdx].trim(); // extract line name
				String routeName = parts[routeNameIdx].trim(); // extract route name
				String lineId = lineName + "_" + routeName; // build unique ID combining line and route name

				TransitLine transitLine = schedule.getTransitLines().get(Id.create(lineId, TransitLine.class)); // try to get already-created line
				if (transitLine == null) { // line doesn't exist yet
					transitLine = schedule.getFactory().createTransitLine(Id.create(lineId, TransitLine.class)); // create new transit line
					transitLine.setName(lineName); // set human-readable line name
					schedule.addTransitLine(transitLine); // register line in schedule
					createVehicleForLine(vehicles, lineId); // create a vehicle instance for this line
				}

				List<String> stopSequence = routeStopSequences.get(routeName); // get stop sequence for this route (null if LineRouteItems.csv missing)
				if (stopSequence != null && !stopSequence.isEmpty()) { // only create route if stop sequence is available
					createTransitRoute(transitLine, schedule, routeName, stopSequence); // create route with stops and departures
				}
				routeCount++; // count processed routes
			}
			log.info("Processed " + routeCount + " line routes from LineRoutes.csv"); // log total
		} catch (IOException e) { // handle file reading errors
			log.error("Error reading LineRoutes.csv: " + e.getMessage(), e); // log error
		}
	}

	/**
	 * Reads LineRouteItems.csv to build a map of route name -> ordered stop point number list.
	 * Returns an empty map if the file does not exist (routes will be created without stop sequences).
	 * @param lineRouteItemsFile path to LineRouteItems.csv
	 * @return map from route name to ordered list of stop point numbers
	 */
	private Map<String, List<String>> readLineRouteItemsCSV(final String lineRouteItemsFile) {
		Map<String, List<String>> routeStops = new HashMap<>(); // result map: route name -> stop list
		File f = new File(lineRouteItemsFile); // create File object to check existence
		if (!f.exists()) { // check if LineRouteItems.csv was exported from VISUM
			log.warn("LineRouteItems.csv not found at " + lineRouteItemsFile + " — routes will have no stop sequences"); // warn that stop sequences are missing
			return routeStops; // return empty map; routes will still be created but without stops
		}

		try (BufferedReader reader = IOUtils.getBufferedReader(f.toURI().toURL(), StandardCharsets.ISO_8859_1)) { // open LineRouteItems.csv
			String headerLine = reader.readLine(); // read header row
			if (headerLine == null) return routeStops; // exit if file is empty

			String[] headers = Visum2MATSimNetworkConverter.parseCSVLine(headerLine); // parse header
			int routeNameIdx = Visum2MATSimNetworkConverter.getColumnIndex(headers, "LINEROUTENAME"); // column: route name
			int indexIdx = Visum2MATSimNetworkConverter.getColumnIndex(headers, "INDEX"); // column: stop sequence index within route
			int stopPointNoIdx = Visum2MATSimNetworkConverter.getColumnIndex(headers, "STOPPOINTNO"); // column: stop point number

			if (routeNameIdx == -1 || stopPointNoIdx == -1) { // route name and stop point number are required
				log.error("LineRouteItems.csv missing required columns LINEROUTENAME or STOPPOINTNO"); // log error
				return routeStops; // cannot build sequences without these
			}

			// Read all rows into a temporary list, then sort by INDEX before inserting into map
			List<String[]> rows = new ArrayList<>(); // temporary list to hold all parsed rows
			String line; // variable for reading lines
			while ((line = reader.readLine()) != null) { // loop through all rows
				if (line.trim().isEmpty()) continue; // skip empty lines
				String[] parts = Visum2MATSimNetworkConverter.parseCSVLine(line); // parse row
				if (parts.length <= Math.max(routeNameIdx, stopPointNoIdx)) continue; // skip rows too short
				if (!parts[stopPointNoIdx].trim().isEmpty()) { // only include rows that have a stop point number
					rows.add(parts); // add to list for sorting
				}
			}

			// Sort by route name then by INDEX so stops are in the correct order
			if (indexIdx >= 0) { // sort by index only if the INDEX column exists
				rows.sort((a, b) -> { // lambda comparator: sort by route name, then by index
					String routeA = a[routeNameIdx].trim(); // get route name from row A
					String routeB = b[routeNameIdx].trim(); // get route name from row B
					int cmp = routeA.compareTo(routeB); // compare route names alphabetically
					if (cmp != 0) return cmp; // use route name comparison if different
					int idxA = a[indexIdx].trim().isEmpty() ? 0 : Integer.parseInt(a[indexIdx].trim()); // parse index for row A
					int idxB = b[indexIdx].trim().isEmpty() ? 0 : Integer.parseInt(b[indexIdx].trim()); // parse index for row B
					return Integer.compare(idxA, idxB); // compare indices numerically
				});
			}

			for (String[] parts : rows) { // iterate through sorted rows
				String routeName = parts[routeNameIdx].trim(); // get route name for this row
				String stopPointNo = parts[stopPointNoIdx].trim(); // get stop point number for this row
				routeStops.computeIfAbsent(routeName, k -> new ArrayList<>()).add(stopPointNo); // append stop to route's list (create list if needed)
			}
			log.info("Read stop sequences for " + routeStops.size() + " routes from LineRouteItems.csv"); // log how many routes have sequences
		} catch (IOException e) { // handle file reading errors
			log.error("Error reading LineRouteItems.csv: " + e.getMessage(), e); // log the error
		}
		return routeStops; // return populated map
	}

	/**
	 * Creates a default vehicle type (bus) in the vehicles container if it does not already exist.
	 * @param vehicles the vehicles container
	 * @param type the type name, e.g. "bus"
	 */
	private void createDefaultVehicleType(final Vehicles vehicles, final String type) {
		Id<VehicleType> typeId = Id.create(type, VehicleType.class); // create the vehicle type ID
		if (vehicles.getVehicleTypes().get(typeId) != null) return; // skip if type already registered
		VehicleType vType = vehicles.getFactory().createVehicleType(typeId); // create new vehicle type
		vType.setDescription("Default " + type + " vehicle"); // set human-readable description
		vType.getCapacity().setSeats(50); // set default seated capacity
		vType.getCapacity().setStandingRoom(20); // set default standing capacity
		vType.setPcuEquivalents(2.0); // set PCU equivalents for traffic capacity calculations
		vehicles.addVehicleType(vType); // register vehicle type in container
	}

	/**
	 * Creates a single vehicle instance for a transit line and registers it in the vehicles container.
	 * @param vehicles the vehicles container
	 * @param lineId the transit line ID (used to form a unique vehicle ID)
	 */
	private void createVehicleForLine(final Vehicles vehicles, final String lineId) {
		Id<VehicleType> typeId = Id.create("bus", VehicleType.class); // reference the bus vehicle type
		Id<Vehicle> vehicleId = Id.create(lineId + "_veh1", Vehicle.class); // create unique vehicle ID from line ID
		if (vehicles.getVehicles().get(vehicleId) != null) return; // skip if vehicle already exists
		Vehicle vehicle = vehicles.getFactory().createVehicle(vehicleId, vehicles.getVehicleTypes().get(typeId)); // create the vehicle instance
		vehicles.addVehicle(vehicle); // register vehicle in container
	}

	/**
	 * Creates a TransitRoute with stop sequence and departures every 15 min from 6:00 to 22:00.
	 * @param line the parent TransitLine
	 * @param schedule the schedule (used to look up stop facilities by ID)
	 * @param routeName the route name (used as route ID)
	 * @param stopSequence ordered list of stop point numbers
	 */
	private void createTransitRoute(final TransitLine line, final TransitSchedule schedule, final String routeName, final List<String> stopSequence) {
		List<TransitRouteStop> stops = new ArrayList<>(); // list of stops that will define the route

		for (int i = 0; i < stopSequence.size(); i++) { // iterate through ordered stop point numbers
			TransitStopFacility facility = schedule.getFacilities().get(Id.create(stopSequence.get(i), TransitStopFacility.class)); // look up stop facility by number
			if (facility == null) continue; // skip if stop facility not found in schedule
			int offsetSec = i * 30 * 60; // assign arrival time: 30-minute interval between each stop
			TransitRouteStop routeStop = schedule.getFactory().createTransitRouteStop(facility, offsetSec, offsetSec + 60); // create stop with arrival and departure offsets
			stops.add(routeStop); // add to route's stop list
		}

		if (stops.isEmpty()) return; // do not create route if there are no valid stops

		TransitRoute route = schedule.getFactory().createTransitRoute(Id.create(routeName, TransitRoute.class), null, stops, "pt"); // create route (null network route — PT teleportation mode)

		for (int hour = 6; hour < 22; hour++) { // add departures every 15 minutes from 06:00 to 22:00
			for (int quarter = 0; quarter < 4; quarter++) { // 4 departures per hour (every 15 minutes)
				int depTime = hour * 3600 + quarter * 15 * 60; // calculate departure time in seconds
				Departure departure = schedule.getFactory().createDeparture(Id.create("dep_" + depTime, Departure.class), depTime); // create departure at this time
				departure.setVehicleId(Id.create(line.getId().toString() + "_veh1", Vehicle.class)); // assign the line's vehicle to this departure
				route.addDeparture(departure); // register departure in the route
			}
		}

		line.addRoute(route); // add the completed route to its parent transit line
	}

	/**
	 * Writes the transit schedule to transitSchedule.xml in the output directory.
	 * @param schedule the transit schedule to write
	 */
	private void writeTransitSchedule(final TransitSchedule schedule) {
		String outputFile = outputDir + File.separator + "transitSchedule.xml"; // construct output file path
		log.info("Writing transit schedule to: " + outputFile); // log output location
		new org.matsim.pt.transitSchedule.TransitScheduleWriterV1(schedule).write(outputFile); // write schedule XML
		log.info("Transit schedule written: " + schedule.getFacilities().size() + " stops, " + schedule.getTransitLines().size() + " lines"); // log written counts
	}

	/**
	 * Writes the transit vehicles to transitVehicles.xml in the output directory.
	 * @param vehicles the vehicles container to write
	 */
	private void writeVehicles(final Vehicles vehicles) {
		if (vehicles.getVehicles().isEmpty()) { // check if vehicles exist before writing
			log.warn("No transit vehicles to write — skipping transitVehicles.xml"); // warn if container is empty
			return; // skip writing empty file
		}
		String outputFile = outputDir + File.separator + "transitVehicles.xml"; // construct output file path
		log.info("Writing transit vehicles to: " + outputFile); // log output location
		new org.matsim.vehicles.VehicleWriterV1(vehicles).writeFile(outputFile); // write vehicles XML
		log.info("Transit vehicles written: " + vehicles.getVehicles().size() + " vehicles"); // log written count
	}

	/**
	 * Main entry point.
	 * Usage: java Visum2MATSimTransitConverter &lt;input-dir&gt; &lt;output-dir&gt; [network-file]
	 *   input-dir    : directory containing StopPoints.csv, LineRoutes.csv, LineRouteItems.csv
	 *   output-dir   : directory where transitSchedule.xml and transitVehicles.xml will be written
	 *   network-file : path to network.xml for stop coordinate lookup (default: output-dir/network.xml)
	 */
	public static void main(final String[] args) {
		if (args.length < 2) { // check if required arguments are present
			System.err.println("Usage: java Visum2MATSimTransitConverter <input-dir> <output-dir> [network-file]"); // print usage
			System.exit(1); // exit with error code
		}

		String inputDir = args[0]; // extract input directory
		String outputDir = args[1]; // extract output directory
		String networkFile = args.length >= 3 ? args[2] : outputDir + File.separator + "network.xml"; // use 3rd arg or default to output-dir/network.xml

		Visum2MATSimTransitConverter converter = new Visum2MATSimTransitConverter(inputDir, outputDir, networkFile); // create converter
		converter.convert(); // execute conversion
	}
}
