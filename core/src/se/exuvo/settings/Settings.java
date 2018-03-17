package se.exuvo.settings;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import se.unlogic.standardutils.xml.XMLUtils;

import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.UnspecifiedParameterException;

public class Settings {

	private static Hashtable<String, Setting> settings = new Hashtable<String, Setting>();
	protected static final Logger log = Logger.getLogger(Settings.class);
	private static String name;

	public static String getSectionName() {
		return name;
	}

	public static String getStr(String name) {
		Setting s = settings.get(name);
		if (s != null && s.getName().equals(name)) {
			return s.getStr();
		}
		return "";
	}

	public static Boolean getBol(String name) {
		Setting s = settings.get(name);
		if (s != null && s.getName().equals(name)) {
			return s.getBol();
		}
		return false;
	}

	public static Integer getInt(String name) {
		Setting s = settings.get(name);
		if (s != null && s.getName().equals(name)) {
			return s.getInt();
		}
		return null;
	}

	public static Float getFloat(String name) {
		Setting s = settings.get(name);
		if (s != null && s.getName().equals(name)) {
			return s.getFloat();
		}
		return 0f;
	}

	public static void remove(String name) {
		settings.remove(name);
	}

	public static void set(String name, String value) {
		Setting s = settings.get(name);
		if (s != null && s.getName().equals(name)) {
			s.setStr(value);
			return;
		}
		add(name, value);
	}

	public static void set(String name, boolean value) {
		Setting s = settings.get(name);
		if (s != null && s.getName().equals(name)) {
			s.setBol(value);
			return;
		}
		add(name, value);
	}

	public static void set(String name, int value) {
		Setting s = settings.get(name);
		if (s != null && s.getName().equals(name)) {
			s.setInt(value);
			return;
		}
		add(name, value);
	}

	public static void set(String name, float value) {
		Setting s = settings.get(name);
		if (s != null && s.getName().equals(name)) {
			s.setFloat(value);
			return;
		}
		add(name, value);
	}

	public static void set(String name, String value, Setting.Type type) {
		Setting s = settings.get(name);
		if (s != null && s.getName().equals(name)) {
			switch (type) {
				case STRING:
					s.setStr(value);
					break;
				case BOOLEAN:
					s.setBol(Boolean.parseBoolean(value));
					break;
				case INTEGER:
					s.setInt(Integer.parseInt(value));
					break;
				case FLOAT:
					s.setFloat(Float.parseFloat(value));
					break;
				default:
					throw new Setting.InvalidTypeException("Trying to set unknown type \"" + type + "\" !");
			}
			return;
		}
		add(name, value, type);
	}

	public static void add(String name, String value) {
		settings.put(name, new Setting(name, value));
	}

	public static void add(String name, Boolean value) {
		settings.put(name, new Setting(name, value));
	}

	public static void add(String name, Integer value) {
		settings.put(name, new Setting(name, value));
	}

	public static void add(String name, Float value) {
		settings.put(name, new Setting(name, value));
	}

	public static void add(String name, String value, Setting.Type type) {
		settings.put(name, new Setting(name, value, type));
	}

	public static void add(Setting setting) {
		settings.put(name, setting);
	}

	/**
	 * Saves settings list to xml file.
	 */
	public static boolean save() {
		log.info("Saving settings");
		File file = new File("settings.xml");
		Document doc;
		XPath xPath = XPathFactory.newInstance().newXPath();

		if (!file.exists()) {
			doc = XMLUtils.createDomDocument();
			log.info("Creating new settings file");
		} else {
			try {
				doc = XMLUtils.parseXMLFile(file, false, false);
			} catch (SAXException e) {
				log.error("SAXException: " + e + " while parsing " + file);
				// doc = Xml.newxml();
				return false;
			} catch (IOException e) {
				log.error("IOException: " + e + " while opening " + file);
				return false;
			} catch (ParserConfigurationException e) {
				log.error("ParserConfigurationException: " + e + " while parsing " + file);
				return false;
			}
		}

		try {
			// Root
			Node root = (Node) xPath.evaluate("/" + name, doc, XPathConstants.NODE);
			if (root == null) {
				root = doc.createElement(name);
				doc.appendChild(root);
			}

			// Settings
			Node configElement = (Node) xPath.evaluate("/" + name + "/settings", doc, XPathConstants.NODE);
			if (configElement == null) {
				configElement = doc.createElement("settings");
				root.appendChild(configElement);
			}

			for (Iterator<Setting> it = settings.values().iterator(); it.hasNext();) {
				Setting s = it.next();
				Element n = (Element) xPath.evaluate("/" + name + "/settings/" + s.getName(), doc, XPathConstants.NODE);
				if (n == null) {
					Element e = XMLUtils.createElement(s.getName(), s.getValue(), doc);
					e.setAttribute("type", "" + s.getType().getCode());
					configElement.appendChild(e);
				} else {
					n.setTextContent(s.getValue());
					n.setAttribute("type", "" + s.getType().getCode());
				}
			}

			XMLUtils.writeXMLFile(doc, file, true, "UTF-8");
			return true;
		} catch (XPathExpressionException e) {
			log.error("XPathExpressionException: " + e + " while writing xml");
			return false;
		} catch (TransformerFactoryConfigurationError e) {
			log.error("TransformerFactoryConfigurationError: " + e + " while writing xml");
			return false;
		} catch (TransformerException e) {
			log.error("TransformerException: " + e + " while writing xml");
			return false;
		} catch (FileNotFoundException e) {
			log.error("FileNotFoundException: " + e + " while writing xml");
			return false;
		}

	}

	/**
	 * Loads settings from xml file.
	 */
	public static boolean load() {
		File file = new File("settings.xml");

		if (!file.exists()) {
			save();
		}

		try {
			Document doc = XMLUtils.parseXMLFile(file, false, false);
			XPath xPath = XPathFactory.newInstance().newXPath();

			NodeList nodeList = (NodeList) xPath.evaluate("/" + name + "/settings/*", doc, XPathConstants.NODESET);
			for (int i = 0; i < nodeList.getLength(); i++) {
				Element e = (Element) nodeList.item(i);

				String value = nodeList.item(i).getTextContent();

				if ("null".equals(value)) {
					value = null;
				}

				set(nodeList.item(i).getNodeName(), value, Setting.Type.valueOf(e.getAttribute("type").charAt(0)));
			}

			return true;
		} catch (SAXException e) {
			log.warn("SAXException: " + e + " while reading xml");
		} catch (IOException e) {
			log.warn("IOException: " + e + " while reading xml");
		} catch (ParserConfigurationException e) {
			log.warn("ParserConfigurationException: " + e + " while reading xml");
		} catch (XPathExpressionException e) {
			log.warn("XPathExpressionException: " + e + " while reading xml");
		}
		return false;
	}

	public static boolean start(JSAPResult conf, String name) {
		Settings.name = name;
		if (!load()) {
			return false;
		}
		loadconfig(conf);
		return true;
	}

	/**
	 * Loads settings specified at command line
	 */
	public static void loadconfig(JSAPResult config) {
		for (Iterator<Setting> it = settings.values().iterator(); it.hasNext();) {
			Setting s = it.next();
			if (config.userSpecified(s.getName())) {
				try {
					switch (s.getType()) {
						case STRING:
							s.setStr(config.getString(s.getName()));
							break;
						case BOOLEAN:
							s.setBol(config.getBoolean(s.getName()));
							break;
						case INTEGER:
							s.setInt(config.getInt(s.getName()));
							break;
						default:
							throw new Setting.InvalidTypeException("Trying to set unknown type!");
					}
				} catch (UnspecifiedParameterException e) {}
			}
		}
	}

}
