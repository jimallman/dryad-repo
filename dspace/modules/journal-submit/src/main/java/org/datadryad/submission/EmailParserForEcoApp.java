package org.datadryad.submission;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import nu.xom.Element;

import org.datadryad.submission.xml.AddressLine1;
import org.datadryad.submission.xml.AddressLine2;
import org.datadryad.submission.xml.AddressLine3;
import org.datadryad.submission.xml.Authors;
import org.datadryad.submission.xml.City;
import org.datadryad.submission.xml.Classification;
import org.datadryad.submission.xml.CorrespondingAuthor;
import org.datadryad.submission.xml.JournalEmbargoPeriod;
import org.datadryad.submission.xml.State;
import org.datadryad.submission.xml.SubmissionMetadata;
import org.datadryad.submission.xml.Zip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailParserForEcoApp extends EmailParser {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(EmailParserForEcoApp.class);

	private static final Map<String, String> ELEMENT_MAP;
	static {
		ELEMENT_MAP = new HashMap<String, String>();
		ELEMENT_MAP.put("journal name", "Journal");
                ELEMENT_MAP.put("journal code", "JournalCode");
		ELEMENT_MAP.put("journal admin email", "JournalEditorEmail");
		ELEMENT_MAP.put("journal editor", "JournalEditor");
		ELEMENT_MAP.put("journal senior editor", "JournalEditor");  //for Paleobiology, senior editor is only editor provided in sample.
		ELEMENT_MAP.put("journal embargo period", "JournalEmbargoPeriod");
		ELEMENT_MAP.put("ms authors", "Authors");
		ELEMENT_MAP.put("abstract", "Abstract");
		ELEMENT_MAP.put("article status", "ArticleStatus");
	}

	private static final Map<String, String> NESTED_ELEMENT_MAP;
	static {
		NESTED_ELEMENT_MAP = new HashMap<String, String>();
		NESTED_ELEMENT_MAP.put("contact author", "CorrespondingAuthor");
		NESTED_ELEMENT_MAP.put("ms reference number", "Manuscript");
		NESTED_ELEMENT_MAP.put("ms title", "ArticleTitle");
        NESTED_ELEMENT_MAP.put("keywords", "Classification");
	}

	private static final Set<String> SKIPPED;
	static {
		SKIPPED = new HashSet<String>();
		SKIPPED.add("dryad author url");
		SKIPPED.add("to");
		SKIPPED.add("subject");
	}

	private String myManuscriptID;
	
	@Override
	public ParsingResult parseMessage(List<String> aMessage) {
		StringBuilder resultXML = new StringBuilder();
		ParsingResult result = new ParsingResult();
		StringBuilder value = new StringBuilder();
		String lastLabel = "";

		for (String line : aMessage) {
			if (!line.equals("")) {
				final String[] parts = line.split(":");
				final String label = parts[0].toLowerCase();
				final int size = parts.length;

				if (!SKIPPED.contains(label)) {
					if (ELEMENT_MAP.containsKey(label)
							|| NESTED_ELEMENT_MAP.containsKey(label)) {
						if (!lastLabel.equals("") && !SKIPPED.contains(lastLabel)) {
							final String resultValue = value.toString().trim();

							if ("Journal Name".equalsIgnoreCase(lastLabel)) {
								result.setJournalName(resultValue);
							}
							else
								if ("ms reference number".equalsIgnoreCase(lastLabel)) {
                                                                    result.setSubmissionId(resultValue);
                                                                }

							try {
								final String xml = makeElement(lastLabel, resultValue);
								resultXML.append(xml);
							}
							catch (RuntimeException details) {
								result.setStatus(details.getMessage());
							}
						}

						value = new StringBuilder();
						lastLabel = label;

						for (String val : Arrays.copyOfRange(parts, 1, size)) {
							value.append(val.trim()).append(' ');
						}
					}
					else {
						value.append(EOL);

						for (String valuePart : parts) {
							value.append(valuePart.trim()).append(' ');
						}
					}
				}
				else {
                    try {
                        String resultValue = value.toString().trim();
                        String xml = makeElement(lastLabel, resultValue);
                        resultXML.append(xml);
                        value = new StringBuilder();
                        lastLabel = label;
                    }
                    catch (RuntimeException details) {
                        result.setStatus(details.getMessage());
                    }

				}
			} // else ignore
		}

		if (lastLabel != null) {
		    resultXML.append(makeElement(lastLabel, value.toString()));
                }
		result.setSubmissionData(resultXML);

		if (myManuscriptID.equals("")) {
			result.setHasFlawedId(true);
		}
		
		return result;
	}

	private String makeElement(String aName, String aValue) {
	    String name = ELEMENT_MAP.get(aName.toLowerCase());

	    if (name == null) {
	        StringBuilder xml = new StringBuilder();
	        name = NESTED_ELEMENT_MAP.get(aName.toLowerCase());

	        if ("CorrespondingAuthor".equalsIgnoreCase(name)) {
	            String[] parts = aValue.split("\\r|\\n|\\t");
	            String location;

	            if (parts.length == 5) {
	                xml.append(new CorrespondingAuthor(parts[0].trim()).toXML());
	                xml.append(new AddressLine1(parts[1].trim()).toXML());
	                xml.append(new AddressLine2(parts[2].trim()).toXML());
	                xml.append(new AddressLine3(parts[3].trim()).toXML());

	                location = parts[4];
	                parts = location.split(", | ");

	                if (parts.length == 3) {
	                    xml.append(new City(parts[0].trim()).toXML());
	                    xml.append(new State(parts[1].trim()).toXML());
	                    xml.append(new Zip(parts[2].trim()).toXML());
	                }
	                else
	                    if (LOGGER.isWarnEnabled()) {
	                        LOGGER.warn("Unexpected number of address location parts: "
	                                + parts.length + "(" + location + ")");
	                    }
	            }
	            else
	                if (LOGGER.isWarnEnabled()) {
	                    LOGGER.warn("Unexpected number of address parts: "
	                            + parts.length + "(" + aValue + ")");
	                }

	            return xml.toString();
	        }
	        else if ("Manuscript".equalsIgnoreCase(name)) {
                    Matcher m = Pattern4MS_Dryad_ID.matcher(aValue.trim());
                    if(m.matches()) {
                        myManuscriptID = m.group(1);
                    }
                    return ""; // we don't build xml element yet
	        }
	        else if ("ArticleTitle".equalsIgnoreCase(name)) {
	            return new SubmissionMetadata(myManuscriptID, aValue.trim()).toXML();
	        }
	        else if ("Classification".equalsIgnoreCase(name)){
	            String[] keywords = EmailParser.processKeywordList(aValue);
	            xml = new StringBuilder();
	            xml.append(new Classification(keywords).toXML());
	            return xml.toString();

	        }
            else if (name == null){
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Received null as element name; value was: " + aValue);
                }
                return "";
            }
	        else {
	            return "<" + name + ">" + aValue + "</" + name + ">";
	        }
	    }

	    try {
	        if ("Authors".equalsIgnoreCase(name)) {
	            return new Authors(EmailParser.processAuthorList(aValue)).toXML();
	        }
	        else if ("Journal Embargo Period".equalsIgnoreCase(name)) {
	            if (aValue.trim().equalsIgnoreCase("1 year")) {
	                return new JournalEmbargoPeriod("oneyear").toXML();
	            }
	            else {
	                return new JournalEmbargoPeriod(aValue).toXML();
	            }
	        }
	        else {
	            String path = getClass().getPackage().getName();
	            Class<?> clazz = Class.forName(path + ".xml." + name);
	            Constructor<?> constructor = clazz.getConstructor(String.class);
	            return ((Element) constructor.newInstance(aValue)).toXML();
	        }
	    }
	    catch (Exception details) {
	        LOGGER.error(details.getMessage(), details);
	        throw new RuntimeException(details);
	    }
	}

	private static final String EOL = System.getProperty("line.separator");
}
