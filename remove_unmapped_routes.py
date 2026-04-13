"""Clean pt2matsim output schedule: remove unmapped routes, orphan stops, and orphan transfers."""

import xml.etree.ElementTree as ET  # XML parsing library for reading and modifying the schedule
import os  # standard library for file operations like removing temp files

INPUT_FILE = "scenarios/MMUST/input/transitSchedule_mapped.xml"  # pt2matsim output schedule to clean
OUTPUT_FILE = "scenarios/MMUST/input/transitSchedule.xml"  # final simulation-ready schedule
TMP_FILE = "scenarios/MMUST/input/_tmp_schedule.xml"  # temp file for writing before DOCTYPE injection
DOCTYPE_LINE = '<!DOCTYPE transitSchedule SYSTEM "http://www.matsim.org/files/dtd/transitSchedule_v2.dtd">'  # required for MATSim XML parser


def remove_unmapped_routes(root):
    """Remove transit routes with no <route> element (not mapped to network links)."""
    removed = 0  # counter for removed routes
    for line in list(root.findall("transitLine")):  # iterate over every transit line
        for route in list(line.findall("transitRoute")):  # iterate over routes in this line
            if route.find("route") is None:  # route has no link sequence = unmapped
                line.remove(route)  # remove the unmapped route
                removed += 1  # count it
    return removed  # return total removed count


def remove_empty_lines(root):
    """Remove transit lines that have no routes left after cleaning."""
    removed = 0  # counter for removed lines
    for line in list(root.findall("transitLine")):  # iterate over every transit line
        if len(line.findall("transitRoute")) == 0:  # line has no routes left
            root.remove(line)  # remove the empty line
            removed += 1  # count it
    return removed  # return total removed count


def remove_stops_without_link(root):
    """Remove stop facilities with no linkRefId (parent stops not assigned to any link)."""
    removed_ids = set()  # set to track removed stop IDs for transfer cleanup
    stops_elem = root.find("transitStops")  # find the transitStops container element
    for fac in list(stops_elem):  # iterate over all stopFacility elements
        if fac.get("linkRefId") is None:  # stop has no network link assigned
            removed_ids.add(fac.get("id"))  # record ID for transfer cleanup
            stops_elem.remove(fac)  # remove the orphan stop
    return removed_ids  # return set of removed stop IDs


def remove_orphan_transfers(root, removed_stop_ids):
    """Remove minimalTransferTimes entries that reference removed stops."""
    min_transfers = root.find("minimalTransferTimes")  # find the transfers section
    if min_transfers is None:  # section may not exist in all schedules
        return 0  # nothing to remove
    removed = 0  # counter for removed transfer entries
    for rel in list(min_transfers):  # iterate over all relation elements
        if rel.get("fromStop") in removed_stop_ids or rel.get("toStop") in removed_stop_ids:  # references a removed stop
            min_transfers.remove(rel)  # remove the orphan transfer
            removed += 1  # count it
    return removed  # return total removed count


def write_with_doctype(tree, output_path):
    """Write the XML tree to file with the required DOCTYPE header."""
    tree.write(TMP_FILE, encoding="utf-8", xml_declaration=True)  # write to temp file first via ElementTree
    content = open(TMP_FILE, encoding="utf-8").read()  # read back the temp file as text
    xml_decl = '<?xml version=\'1.0\' encoding=\'utf-8\'?>'  # ElementTree uses single quotes in declaration
    new_header = '<?xml version="1.0" encoding="UTF-8"?>\n' + DOCTYPE_LINE  # use double quotes and add DOCTYPE
    content = content.replace(xml_decl, new_header, 1)  # replace the XML declaration with our header
    open(output_path, "w", encoding="utf-8").write(content)  # write the final file
    os.remove(TMP_FILE)  # delete the temp file


tree = ET.parse(INPUT_FILE)  # parse the mapped schedule produced by pt2matsim
root = tree.getroot()  # get the XML root element

removed_routes = remove_unmapped_routes(root)  # remove routes without link sequences
removed_lines = remove_empty_lines(root)  # remove lines that became empty
removed_stops = remove_stops_without_link(root)  # remove parent stops with no linkRefId
removed_transfers = remove_orphan_transfers(root, removed_stops)  # remove transfers referencing removed stops

routes_left = sum(len(l.findall("transitRoute")) for l in root.findall("transitLine"))  # count remaining routes
print(f"Removed routes (unmapped):     {removed_routes}")  # report removed unmapped routes
print(f"Removed lines (empty):         {removed_lines}")  # report removed empty lines
print(f"Removed stops (no linkRefId):  {len(removed_stops)}")  # report removed parent stops
print(f"Removed transfers (orphan):    {removed_transfers}")  # report removed orphan transfers
print(f"Routes remaining:              {routes_left}")  # report how many routes will be in simulation
print(f"Writing to {OUTPUT_FILE}...")  # inform user about output

write_with_doctype(tree, OUTPUT_FILE)  # write cleaned schedule with DOCTYPE header
print("Done.")  # confirm completion
