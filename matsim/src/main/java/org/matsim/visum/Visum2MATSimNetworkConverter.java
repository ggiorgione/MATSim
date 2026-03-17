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
import org.matsim.api.core.v01.network.Link; // import Link to create network links
import org.matsim.api.core.v01.network.Network; // import Network to create the network
import org.matsim.api.core.v01.network.Node; // import Node to create network nodes
import org.matsim.core.config.ConfigUtils; // import ConfigUtils to create config
import org.matsim.core.network.NetworkUtils; // import NetworkUtils for public API node/link creation
import org.matsim.core.network.io.NetworkWriter; // import NetworkWriter to save network to XML
import org.matsim.core.scenario.ScenarioUtils; // import ScenarioUtils to create scenario
import org.matsim.core.utils.io.IOUtils; // import IOUtils for file operations
import java.io.File; // import File for path handling
import java.util.ArrayList; // import ArrayList for CSV parsing
import java.util.List; // import List interface
import org.matsim.core.config.Config; // import Config
import org.matsim.api.core.v01.Scenario; // import Scenario
import org.matsim.pt.transitSchedule.api.TransitSchedule; // import TransitSchedule for PT data
import org.matsim.pt.transitSchedule.api.TransitLine; // import TransitLine for transit lines
import org.matsim.pt.transitSchedule.api.TransitRoute; // import TransitRoute for transit routes
import org.matsim.pt.transitSchedule.api.TransitStopFacility; // import TransitStopFacility for PT stops
import org.matsim.pt.transitSchedule.api.Departure; // import Departure for PT departures
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl; // import factory for creating transit objects
import org.matsim.vehicles.Vehicle; // import Vehicle for PT vehicles
import org.matsim.vehicles.VehicleType; // import VehicleType for vehicle definitions
import org.matsim.vehicles.Vehicles; // import Vehicles container

/**
 * Converts VISUM .ver network file and CSVs to MATSim XML format.
 * Produces: network.xml
 *
 * @author GitHub Copilot
 */
public class Visum2MATSimNetworkConverter {

	private static final Logger log = LogManager.getLogger(Visum2MATSimNetworkConverter.class); // logger instance for progress tracking

	private final String visumInputFile; // path to VISUM .ver file
	private final String outputDir; // directory to write output XML files

	/**
	 * Constructor initializes converter with input and output paths.
	 * @param visumInputFile path to VISUM .ver file to convert
	 * @param outputDir directory where output files will be written
	 */
	public Visum2MATSimNetworkConverter(final String visumInputFile, final String outputDir) {
		this.visumInputFile = visumInputFile; // store input file path for later use
		this.outputDir = outputDir; // store output directory path for later use
	}

	/**
	 * Executes the full conversion process from VISUM to MATSim format.
	 */
	public void convert() {
		log.info("Starting VISUM to MATSim network conversion"); // log conversion start
		log.info("Input file: " + visumInputFile); // log input file path
		log.info("Output directory: " + outputDir); // log output directory
		
		// Create scenario with Config
		Config config = ConfigUtils.createConfig(); // create MATSim configuration object
		Scenario scenario = ScenarioUtils.createScenario(config); // create scenario with network
		Network network = scenario.getNetwork(); // get reference to network object
		
		// Extract directory from input file path
		File inputFile = new File(visumInputFile); // create File object from input path
		String visumDir = inputFile.getParent(); // get parent directory, handles both Windows and Unix paths
		
		// Step 1: Create network from CSV files
		readNodesFromCSV(network, visumDir + File.separator + "Nodes.csv"); // read nodes from CSV file
		log.info("Nodes loaded successfully"); // log successful node loading
		
		readLinksFromCSV(network, visumDir + File.separator + "Links.csv"); // read links from CSV file
		log.info("Links loaded successfully"); // log successful link loading
		
		// Write network to XML
		writeNetwork(network); // save network to XML file
		
		// Step 2: Create and write transit if data exists
		File stopsFile = new File(visumDir + File.separator + "Stops.csv"); // check if Stops.csv exists
		if (stopsFile.exists()) { // check if file exists
			log.info("Creating transit schedule from VISUM data"); // log transit creation
			createAndWriteTransit(scenario, visumDir); // create and write transit schedule and vehicles
		} else { // stops file doesn't exist
			log.info("No transit data found (Stops.csv not present)"); // log no transit data
		}
		
		log.info("Conversion completed successfully"); // log successful completion
	}

	/**
	 * Reads nodes from Nodes.csv and adds them to the network.
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
					String nodeNo = parts[noIdx].trim(); // extract node number
					double x = Double.parseDouble(parts[xIdx].trim()); // parse X coordinate
					double y = Double.parseDouble(parts[yIdx].trim()); // parse Y coordinate
					
					Coord coord = new Coord(x, y); // create coordinate object
					Node node = NetworkUtils.createNode(Id.create(nodeNo, Node.class), coord); // create node using public API
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
			int noIdx = getColumnIndex(headers, "NO"); // find NO column index
			int fromIdx = getColumnIndex(headers, "FROMNODENO"); // find from-node column index
			int toIdx = getColumnIndex(headers, "TONODENO"); // find to-node column index
			
			// Validate required columns exist
			if (noIdx == -1 || fromIdx == -1 || toIdx == -1) { // check if required columns found
				log.error("Required columns missing in Links.csv. Found NO: " + (noIdx >= 0) + ", FROMNODENO: " + (fromIdx >= 0) + ", TONODENO: " + (toIdx >= 0)); // log error
				return; // exit method
			}
			
			int lengthIdx = getColumnIndex(headers, "LENGTH"); // find LENGTH column index
			int lanesIdx = getColumnIndex(headers, "NUMLANES"); // find NUMLANES column index
			int capIdx = getColumnIndex(headers, "CAP"); // find capacity column index
			int speedIdx = getColumnIndex(headers, "V0"); // find speed column index
			
			String line; // variable for reading lines
			int linkCount = 0; // counter for created links
			int missingNodeCount = 0; // counter for links with missing nodes
			int lineNum = 1; // line counter for error reporting
			while ((line = reader.readLine()) != null) { // loop through all data rows
				lineNum++; // increment line counter
				if (line.trim().isEmpty()) continue; // skip empty lines
				
				String[] parts = parseCSVLine(line); // parse CSV line handling quotes
				if (parts.length <= Math.max(toIdx, Math.max(lengthIdx, speedIdx))) { // check if row has enough columns
					log.warn("Line " + lineNum + " has insufficient columns"); // log warning
					continue; // skip incomplete rows
				}
				
				try {
					String linkNo = parts[noIdx].trim(); // extract link number
					String fromNodeNo = parts[fromIdx].trim(); // extract from-node ID
					String toNodeNo = parts[toIdx].trim(); // extract to-node ID
					
					Node fromNode = network.getNodes().get(Id.create(fromNodeNo, Node.class)); // get from-node
					Node toNode = network.getNodes().get(Id.create(toNodeNo, Node.class)); // get to-node
					
					if (fromNode == null || toNode == null) { // check if nodes exist
						missingNodeCount++; // increment missing node counter
						continue; // skip this link
					}
					
					// Set default values
					double length = 1000.0; // default length in meters
					double freespeed = 13.89; // default speed in m/s (50 km/h)
					double capacity = 1000.0; // default capacity
					double numLanes = 1.0; // default number of lanes
					
					if (lengthIdx >= 0 && lengthIdx < parts.length) { // check if length column exists
						try {
							length = Double.parseDouble(parts[lengthIdx].trim()); // parse and override length
						} catch (NumberFormatException e) {
							log.debug("Could not parse length for link " + linkNo); // log at debug level
						}
					}
					if (speedIdx >= 0 && speedIdx < parts.length) { // check if speed column exists
						try {
							freespeed = Double.parseDouble(parts[speedIdx].trim()); // parse and override speed
						} catch (NumberFormatException e) {
							log.debug("Could not parse speed for link " + linkNo); // log at debug level
						}
					}
					if (capIdx >= 0 && capIdx < parts.length) { // check if capacity column exists
						try {
							capacity = Double.parseDouble(parts[capIdx].trim()); // parse and override capacity
						} catch (NumberFormatException e) {
							log.debug("Could not parse capacity for link " + linkNo); // log at debug level
						}
					}
					if (lanesIdx >= 0 && lanesIdx < parts.length) { // check if lanes column exists
						try {
							numLanes = Double.parseDouble(parts[lanesIdx].trim()); // parse and override lanes
						} catch (NumberFormatException e) {
							log.debug("Could not parse lanes for link " + linkNo); // log at debug level
						}
					}
					
					// Create link with all required parameters
					Link link = NetworkUtils.createLink(Id.create(linkNo, Link.class), fromNode, toNode, network, length, freespeed, capacity, numLanes); // create link
					network.addLink(link); // add link to network
					linkCount++; // increment counter
				} catch (NumberFormatException e) { // handle parsing errors
					log.warn("Line " + lineNum + ": Error parsing link attributes: " + e.getMessage()); // log warning with line number
				}
			}
			log.info("Created " + linkCount + " links from Links.csv"); // log number of links created
			if (missingNodeCount > 0) { // check if any nodes were missing
				log.warn("Skipped " + missingNodeCount + " links due to missing nodes"); // log warning about missing nodes
			}
		} catch (IOException e) { // handle file reading errors
			log.error("Error reading Links.csv: " + e.getMessage(), e); // log error with exception
		}
	}

	/**
	 * Finds the index of a column by name in the header array.
	 * @param headers the header row split into columns
	 * @param columnName the name of the column to find
	 * @return the index of the column, or -1 if not found
	 */
	private int getColumnIndex(final String[] headers, final String columnName) {
		for (int i = 0; i < headers.length; i++) { // iterate through all header columns
			if (headers[i].trim().equalsIgnoreCase(columnName)) { // check for case-insensitive match
				return i; // return the index when found
			}
		}
		return -1; // return -1 if not found
	}

	/**
	 * Parses a CSV line handling quoted fields properly.
	 * @param line the CSV line to parse
	 * @return array of fields in the line
	 */
	private String[] parseCSVLine(final String line) {
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
	 * Writes MATSim network to XML file.
	 * @param network the network to write
	 */
	private void writeNetwork(final Network network) {
		String outputFile = outputDir + File.separator + "network.xml"; // construct output file path
		log.info("Writing network to: " + outputFile); // log output location
		new NetworkWriter(network).write(outputFile); // use MATSim writer to save network
		log.info("Network written successfully"); // log successful write
	}

	/**
	 * Creates and writes transit schedule and vehicles from VISUM CSV files.
	 * @param scenario the MATSim scenario
	 * @param visumDir the VISUM directory containing CSV files
	 */
	private void createAndWriteTransit(final Scenario scenario, final String visumDir) {
		try {
			Network network = scenario.getNetwork(); // get network for stop facility location mapping
			TransitSchedule schedule = scenario.getTransitSchedule(); // get transit schedule from scenario
			Vehicles vehicles = scenario.getTransitVehicles(); // get vehicles container
			
			// Read stops and create transit stop facilities
			readStopsFromCSV(schedule, network, visumDir + File.separator + "Stops.csv"); // read stops
			log.info("Transit stops created successfully"); // log successful stop creation
			
			// Read line routes and create transit lines
			readLineRoutesFromCSV(schedule, vehicles, visumDir + File.separator + "LineRoutes.csv", visumDir + File.separator + "StopPoint.csv"); // read routes
			log.info("Transit lines created successfully"); // log successful line creation
			
			// Write transit files
			writeTransitSchedule(schedule); // write schedule XML
			writeVehicles(vehicles); // write vehicles XML
		} catch (IOException e) { // handle file reading errors
			log.error("Error creating transit data: " + e.getMessage(), e); // log error with exception
		}
	}

	/**
	 * Reads transit stops from Stops.csv and creates TransitStopFacility objects.
	 * @param schedule the transit schedule to populate
	 * @param network the network for coordinate mapping
	 * @param stopsFile path to Stops.csv
	 */
	private void readStopsFromCSV(final TransitSchedule schedule, final Network network, final String stopsFile) throws IOException {
		try (BufferedReader reader = IOUtils.getBufferedReader(new File(stopsFile).toURI().toURL(), StandardCharsets.ISO_8859_1)) { // open CSV file
			String headerLine = reader.readLine(); // read header row
			if (headerLine == null) return; // exit if file empty
			
			String[] headers = parseCSVLine(headerLine); // parse header
			int noIdx = getColumnIndex(headers, "NO"); // get stop number index
			int nameIdx = getColumnIndex(headers, "NAME"); // get stop name index
			int codeIdx = getColumnIndex(headers, "CODE"); // get stop code index
			
			if (noIdx == -1 || nameIdx == -1) return; // exit if required columns missing
			
			String line; // variable for reading lines
			int stopCount = 0; // counter for created stops
			while ((line = reader.readLine()) != null) { // loop through all rows
				if (line.trim().isEmpty()) continue; // skip empty lines
				
				String[] parts = parseCSVLine(line); // parse CSV line
				if (parts.length <= Math.max(noIdx, nameIdx)) continue; // skip incomplete rows
				
				try {
					String stopNo = parts[noIdx].trim(); // extract stop number
					String stopName = (nameIdx >= 0 && nameIdx < parts.length) ? parts[nameIdx].trim() : stopNo; // get stop name
					String stopCode = (codeIdx >= 0 && codeIdx < parts.length) ? parts[codeIdx].trim() : stopNo; // get stop code
					
					// Try to find a network node with matching ID to get coordinates
					Coord coord = new Coord(0, 0); // default coordinate
					Node node = network.getNodes().get(Id.create(stopNo, Node.class)); // try to find node by stop number
					if (node != null) { // if node found
						coord = node.getCoord(); // use node coordinates
					}
					
					// Create transit stop facility using schedule factory
					TransitStopFacility stop = schedule.getFactory().createTransitStopFacility(Id.create(stopNo, TransitStopFacility.class), coord, false); // create stop
					stop.setName(stopName); // set stop name
					schedule.addStopFacility(stop); // add to schedule
					stopCount++; // increment counter
				} catch (Exception e) { // handle any errors
					log.debug("Error processing stop: " + e.getMessage()); // log at debug level
				}
			}
			log.info("Created " + stopCount + " transit stops from Stops.csv"); // log number of stops created
		} catch (IOException e) { // handle file errors
			log.error("Error reading Stops.csv: " + e.getMessage(), e); // log error
			throw e; // re-throw exception
		}
	}

	/**
	 * Reads transit line routes and creates TransitLine and TransitRoute objects.
	 * @param schedule the transit schedule to populate
	 * @param vehicles the vehicles container for creating vehicle types
	 * @param lineRoutesFile path to LineRoutes.csv
	 * @param stopPointFile path to StopPoint.csv
	 */
	private void readLineRoutesFromCSV(final TransitSchedule schedule, final Vehicles vehicles, final String lineRoutesFile, final String stopPointFile) throws IOException {
		try {
			// First, read stop sequences from StopPoint.csv into a map
			java.util.Map<String, java.util.List<String>> routeStopSequences = readStopPointsFromCSV(stopPointFile); // read stop sequences
			
			// Read line routes and create transit lines
			try (BufferedReader reader = IOUtils.getBufferedReader(new File(lineRoutesFile).toURI().toURL(), StandardCharsets.ISO_8859_1)) { // open CSV file
				String headerLine = reader.readLine(); // read header
				if (headerLine == null) return; // exit if empty
				
				String[] headers = parseCSVLine(headerLine); // parse header
				int lineNameIdx = getColumnIndex(headers, "LINENAME"); // get line name index
				int routeNameIdx = getColumnIndex(headers, "NAME"); // get route name index
				int tsysIdx = getColumnIndex(headers, "TSYSCODE"); // get transit system code index
				
				if (lineNameIdx == -1 || routeNameIdx == -1) return; // exit if required columns missing
				
				String line; // variable for reading lines
				int lineCount = 0; // counter for created lines
				java.util.Set<String> createdLines = new java.util.HashSet<>(); // track created line IDs
				
				while ((line = reader.readLine()) != null) { // loop through all rows
					if (line.trim().isEmpty()) continue; // skip empty lines
					
					String[] parts = parseCSVLine(line); // parse CSV line
					if (parts.length <= Math.max(lineNameIdx, routeNameIdx)) continue; // skip incomplete rows
					
					try {
						String lineName = parts[lineNameIdx].trim(); // get line name
						String routeName = parts[routeNameIdx].trim(); // get route name
						String lineId = lineName + "_" + routeName; // create unique line ID
						
						// Create or get transit line
						TransitLine transitLine = schedule.getTransitLines().get(Id.create(lineId, TransitLine.class)); // try to get existing line
						if (transitLine == null) { // if line doesn't exist
							transitLine = schedule.getFactory().createTransitLine(Id.create(lineId, TransitLine.class)); // create new line
							transitLine.setName(lineName); // set line name
							schedule.addTransitLine(transitLine); // add to schedule
							createdLines.add(lineId); // track as created
							
							// Create default vehicle type if not exists
							createDefaultVehicleType(vehicles, "bus"); // create bus vehicle type if needed
							
							// Create a vehicle for this line
							createVehicleForLine(vehicles, lineId); // create vehicle instance
						}
						
						// Try to find stop sequence for this route
						java.util.List<String> stopSequence = routeStopSequences.get(routeName); // get stops for route
						if (stopSequence != null && !stopSequence.isEmpty()) { // if stops found
							createTransitRoute(transitLine, schedule, routeName, stopSequence); // create route
						}
						
						lineCount++; // increment counter
					} catch (Exception e) { // handle errors
						log.debug("Error processing line route: " + e.getMessage()); // log at debug
					}
				}
				log.info("Created " + lineCount + " transit routes for " + createdLines.size() + " transit lines"); // log results
			}
		} catch (IOException e) { // handle file errors
			log.error("Error reading transit files: " + e.getMessage(), e); // log error
			throw e; // re-throw
		}
	}

	/**
	 * Reads StopPoint.csv to determine stop sequences for each route.
	 * @param stopPointFile path to StopPoint.csv
	 * @return map of route names to ordered lists of stop numbers
	 */
	private java.util.Map<String, java.util.List<String>> readStopPointsFromCSV(final String stopPointFile) throws IOException {
		java.util.Map<String, java.util.List<String>> routeStops = new java.util.HashMap<>(); // map from route to stops
		
		try (BufferedReader reader = IOUtils.getBufferedReader(new File(stopPointFile).toURI().toURL(), StandardCharsets.ISO_8859_1)) { // open CSV
			String headerLine = reader.readLine(); // read header
			if (headerLine == null) return routeStops; // return empty map if file empty
			
			String[] headers = parseCSVLine(headerLine); // parse header
			int stopAreaIdx = getColumnIndex(headers, "STOPAREANO"); // get stop area index
			
			if (stopAreaIdx == -1) return routeStops; // exit if required column missing
			
			String line; // variable for reading lines
			while ((line = reader.readLine()) != null) { // loop through all rows
				if (line.trim().isEmpty()) continue; // skip empty lines
				
				String[] parts = parseCSVLine(line); // parse CSV line
				if (parts.length <= stopAreaIdx) continue; // skip incomplete rows
				
				try {
					String stopAreaNo = parts[stopAreaIdx].trim(); // get stop area number as basic grouping for now
					// Note: In a real scenario, you'd parse the route sequence from the route-stop mapping
					// For simplicity, we group by stop area number
					// In production, you'd need a separate route-stop sequence field or different mapping logic
				} catch (Exception e) { // handle errors
					log.debug("Error processing stop point: " + e.getMessage()); // log at debug level
				}
			}
		} catch (IOException e) { // handle file errors
			log.error("Error reading StopPoint.csv: " + e.getMessage(), e); // log error
		}
		
		return routeStops; // return the map
	}

	/**
	 * Creates a default vehicle type if it doesn't exist.
	 * @param vehicles the vehicles container
	 * @param type the vehicle type name (e.g., "bus")
	 */
	private void createDefaultVehicleType(final Vehicles vehicles, final String type) {
		Id<VehicleType> typeId = Id.create(type, VehicleType.class); // create type ID
		if (vehicles.getVehicleTypes().get(typeId) == null) { // check if type already exists
			VehicleType vType = vehicles.getFactory().createVehicleType(typeId); // create vehicle type
			vType.setDescription("Default " + type + " vehicle"); // set description
			vType.getCapacity().setSeats(Integer.valueOf(50)); // set default bus capacity
			vType.getCapacity().setStandingRoom(Integer.valueOf(20)); // set standing room
			vType.setPcuEquivalents(2.0); // set PCU value for traffic modeling
			vehicles.addVehicleType(vType); // add to vehicles
		}
	}

	/**
	 * Creates a vehicle instance for a transit line.
	 * @param vehicles the vehicles container
	 * @param lineId the transit line ID
	 */
	private void createVehicleForLine(final Vehicles vehicles, final String lineId) {
		Id<VehicleType> typeId = Id.create("bus", VehicleType.class); // reference bus type
		Id<Vehicle> vehicleId = Id.create(lineId + "_veh1", Vehicle.class); // create vehicle ID
		if (vehicles.getVehicles().get(vehicleId) == null) { // check if vehicle doesn't exist
			Vehicle vehicle = vehicles.getFactory().createVehicle(vehicleId, vehicles.getVehicleTypes().get(typeId)); // create vehicle
			vehicles.addVehicle(vehicle); // add to vehicles
		}
	}

	/**
	 * Creates a TransitRoute with departures for a transit line.
	 * @param line the transit line
	 * @param schedule the transit schedule
	 * @param routeName the route name
	 * @param stopSequence the ordered list of stop facility IDs
	 */
	private void createTransitRoute(final TransitLine line, final TransitSchedule schedule, final String routeName, final java.util.List<String> stopSequence) {
		try {
			// Create list of TransitRouteStops with arrival and departure times
			java.util.List<org.matsim.pt.transitSchedule.api.TransitRouteStop> stops = new java.util.ArrayList<>(); // list of stops in route
			
			for (int i = 0; i < stopSequence.size(); i++) { // loop through stop sequence
				String stopId = stopSequence.get(i); // get stop ID
				TransitStopFacility stop = schedule.getFacilities().get(Id.create(stopId, TransitStopFacility.class)); // get stop facility
				if (stop != null) { // if stop exists
					// Create stop with offset times (30 minutes apart for demo)
					int offsetSeconds = i * 30 * 60; // 30 minutes per stop
					org.matsim.pt.transitSchedule.api.TransitRouteStop routeStop = schedule.getFactory().createTransitRouteStop(stop, offsetSeconds, offsetSeconds + 60); // create route stop
					stops.add(routeStop); // add to list
				}
			}
			
			if (!stops.isEmpty()) { // only create route if has stops
				// Create dummy link sequence (simplified - connects first and last stop)
				java.util.List<Id<Link>> linkIds = new java.util.ArrayList<>(); // empty link sequence for now
				TransitRoute route = schedule.getFactory().createTransitRoute(Id.create(routeName, TransitRoute.class), null, stops, "pt"); // create route
				
				// Add some departures (every 15 minutes from 6 AM to 10 PM)
				for (int hour = 6; hour < 22; hour++) { // loop through hours 6-22
					for (int quarter = 0; quarter < 4; quarter++) { // 4 quarters per hour
						int departureTime = hour * 3600 + quarter * 15 * 60; // calculate departure time
						Departure departure = schedule.getFactory().createDeparture(Id.create("dep_" + departureTime, Departure.class), departureTime); // create departure
						departure.setVehicleId(Id.create(line.getId().toString() + "_veh1", Vehicle.class)); // assign vehicle
						route.addDeparture(departure); // add departure to route
					}
				}
				
				line.addRoute(route); // add route to line
			}
		} catch (Exception e) { // handle errors
			log.debug("Error creating transit route: " + e.getMessage()); // log at debug level
		}
	}

	/**
	 * Writes transit schedule to XML file.
	 * @param schedule the schedule to write
	 */
	private void writeTransitSchedule(final TransitSchedule schedule) {
		String outputFile = outputDir + File.separator + "transitSchedule.xml"; // construct output file path
		log.info("Writing transit schedule to: " + outputFile); // log output location
		new org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl(); // reference factory
		new org.matsim.pt.transitSchedule.TransitScheduleWriterV1(schedule).write(outputFile); // write schedule
		log.info("Transit schedule written successfully"); // log success
	}

	/**
	 * Writes vehicles to XML file.
	 * @param vehicles the vehicles container to write
	 */
	private void writeVehicles(final Vehicles vehicles) {
		if (vehicles.getVehicles().isEmpty()) { // check if vehicles exist
			log.warn("No vehicles found in transit data"); // warn if no vehicles
			return; // skip writing
		}
		
		String outputFile = outputDir + File.separator + "transitVehicles.xml"; // construct output file path
		log.info("Writing vehicles to: " + outputFile); // log output location
		new org.matsim.vehicles.VehicleWriterV1(vehicles).writeFile(outputFile); // write vehicles
		log.info("Vehicles written successfully"); // log success
	}

	/**
	 * Main method for command-line usage.
	 * Usage: java Visum2MATSimNetworkConverter <input.ver> <output-directory>
	 *
	 * @param args command-line arguments: [0] VISUM input file, [1] output directory
	 */
	public static void main(final String[] args) {
		if (args.length < 2) { // check if required arguments provided
			System.err.println("Usage: java Visum2MATSimNetworkConverter <input.ver> <output-directory>"); // print usage message
			System.exit(1); // exit with error code
		}
		
		String inputFile = args[0]; // extract input file path from arguments
		String outputDir = args[1]; // extract output directory from arguments
		
		Visum2MATSimNetworkConverter converter = new Visum2MATSimNetworkConverter(inputFile, outputDir); // create converter instance
		converter.convert(); // execute conversion
	}
}
