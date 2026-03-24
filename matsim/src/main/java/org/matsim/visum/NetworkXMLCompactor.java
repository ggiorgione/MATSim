package org.matsim.visum;

import java.io.BufferedReader; // read file line by line
import java.io.BufferedWriter; // write file efficiently
import java.io.File; // file handling
import java.io.FileInputStream; // read file with encoding
import java.io.FileOutputStream; // write file with encoding
import java.io.InputStreamReader; // read with specific charset
import java.io.OutputStreamWriter; // write with specific charset
import java.nio.charset.StandardCharsets; // UTF-8 charset
import java.util.regex.Pattern; // regex patterns
import java.util.regex.Matcher; // regex matching
import org.apache.logging.log4j.LogManager; // logger setup
import org.apache.logging.log4j.Logger; // logger interface

/**
 * Converts MATSim verbose network XML to compact format using regex.
 * Transforms from: <node id="1" x="100" y="200"><attributes>...</attributes></node>
 * To: <node id="1" x="100" y="200" type="50"/>
 * Uses streaming regex instead of DOM for performance on large files.
 */
public class NetworkXMLCompactor {
	
	private static final Logger log = LogManager.getLogger(NetworkXMLCompactor.class); // logger instance
	
	/**
	 * Converts verbose network XML to compact format using regex streaming.
	 * @param networkXmlPath path to network.xml file to compact
	 */
	public static void compactNetworkXML(final String networkXmlPath) {
		try {
			File xmlFile = new File(networkXmlPath); // create File object
			if (!xmlFile.exists()) { // check if file exists
				log.warn("Network XML file not found: " + networkXmlPath); // log warning
				return; // exit method
			}
			
			log.info("Compacting network XML (streaming regex): " + networkXmlPath); // log compacting start
			long start = System.currentTimeMillis(); // record start time
			
			StringBuilder content = new StringBuilder(); // buffer for entire file
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(xmlFile), StandardCharsets.UTF_8))) { // open file with explicit UTF-8
				String line; // variable for reading lines
				while ((line = reader.readLine()) != null) { // read each line
					content.append(line).append("\n"); // append line to buffer
				}
			}
			
			String result = content.toString(); // convert to string
			
			// Pattern 1: Convert nodes with type attribute from <node...><attributes>..type..</attributes></node> to <node ... type="X"/>
			result = compactNodeWithType(result); // extract type attribute from nested attributes
			
			// Pattern 2: Convert empty link elements <link ...>\s*</link> to <link .../>
			result = result.replaceAll("(<link[^>]*?)>\\s*</link>", "$1/>"); // self-closing tag for empty links (handle whitespace/newlines)
			
			// Pattern 3: Convert empty node elements <node ...>\s*</node> to <node .../>
			result = result.replaceAll("(<node[^>]*?)>\\s*</node>", "$1/>"); // self-closing tag for empty nodes (handle whitespace/newlines)
			
			// Write back to file with explicit UTF-8 encoding
			try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(xmlFile), StandardCharsets.UTF_8))) { // open file with explicit UTF-8
				writer.write(result); // write compacted content
			}
			
			long end = System.currentTimeMillis(); // record end time
			log.info("XML compacting completed in " + (end - start) / 1000 + " seconds"); // log completion time
		} catch (Exception e) { // handle file reading/writing errors
			log.error("Error compacting network XML: " + e.getMessage(), e); // log error with exception
		}
	}
	
	/**
	 * Extracts type attribute from nested <attributes> tag and moves to parent element.
	 * Transforms: <node ... ><attributes><attribute name="type" ...>50</attribute></attributes></node>
	 * To: <node ... type="50"/>
	 * @param content the XML content
	 * @return XML with type attribute extracted
	 */
	private static String compactNodeWithType(String content) {
		// Pattern to match node with type in attributes: <node id="X" ...><attributes><attribute name="type" ...>VALUE</attribute></attributes></node>
		// Use [^<]* instead of .*? to avoid crossing tag boundaries
		Pattern pattern = Pattern.compile( // create pattern for type attribute in nested attributes
			"(<node[^>]*?)>\\s*<attributes>\\s*<attribute name=\"type\"[^>]*>([^<]*)</attribute>\\s*</attributes>\\s*</node>", // regex to find type in attributes without crossing tags
			Pattern.DOTALL // allow matching across lines
		);
		Matcher matcher = pattern.matcher(content); // create matcher for this pattern
		StringBuffer result = new StringBuffer(); // buffer for result
		while (matcher.find()) { // find all matches
			String nodeTag = matcher.group(1).trim(); // get node opening tag and trim trailing whitespace to avoid double spaces
			String typeValue = matcher.group(2).trim(); // get type value (trimmed to remove whitespace)
			String replacement = nodeTag + " type=\"" + typeValue + "\"/>"; // create replacement with type as attribute with single space
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement)); // append replacement
		}
		matcher.appendTail(result); // append rest of content
		return result.toString(); // return compacted content
	}
}
