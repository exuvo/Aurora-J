package se.exuvo.settings;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
import org.xml.sax.SAXException;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.UnspecifiedParameterException;

import se.unlogic.standardutils.populators.FloatPopulator;
import se.unlogic.standardutils.populators.IntegerPopulator;
import se.unlogic.standardutils.xml.XMLUtils;

public class Settings {

	protected static final Logger log = Logger.getLogger(Settings.class);
	private static Document doc;
	private static XPathFactory pathFactory = XPathFactory.newInstance();
	private static XPath xpath = pathFactory.newXPath();
	private static Element rootElement;

	public enum Type {
		STRING('s'), BOOLEAN('b'), INTEGER('i'), FLOAT('f');

		private char code;

		private Type(char code) {
			this.code = code;
		}

		public char getCode() {
			return code;
		}

		public static Type valueOf(char charAt) {

			for (Type type : values()) {
				if (type.getCode() == charAt) {
					return type;
				}
			}

			throw new IllegalArgumentException("" + charAt);
		}
	};

	public static Element getNode(String path) {
		try {
			return (Element) xpath.evaluate(path, rootElement, XPathConstants.NODE);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public static String getNodeValue(String path, Type type) {
		try {
			Element node = (Element) xpath.evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {

				String actualType = node.getAttributes().getNamedItem("type").getNodeValue();

				if (actualType.charAt(0) != type.code) {
					throw new InvalidTypeException("Trying to read " + type + " from " + actualType + " setting!");
				}

				return node.getTextContent();
			}

		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}

		return null;
	}

	public static String getStr(String path) {
		String nodeValue = getNodeValue(path, Type.STRING);

		return nodeValue;
	}

	public static Boolean getBol(String path) {
		String nodeValue = getNodeValue(path, Type.BOOLEAN);

		if (nodeValue != null) {
			return Boolean.parseBoolean(nodeValue);
		}

		return null;
	}

	public static Integer getInt(String path) {
		String nodeValue = getNodeValue(path, Type.INTEGER);

		if (nodeValue != null) {
			return Integer.parseInt(nodeValue);
		}

		return null;
	}

	public static Float getFloat(String path) {
		String nodeValue = getNodeValue(path, Type.FLOAT);

		if (nodeValue != null) {
			return Float.parseFloat(nodeValue);
		}

		return null;
	}

	public static String getStr(String path, String defaultValue) {
		String nodeValue = getNodeValue(path, Type.STRING);

		if (nodeValue == null) {
			set(path, defaultValue);
		}

		return nodeValue;
	}

	public static Boolean getBol(String path, boolean defaultValue) {
		String nodeValue = getNodeValue(path, Type.BOOLEAN);

		if (nodeValue != null) {
			return Boolean.parseBoolean(nodeValue);
		}

		set(path, defaultValue);
		return defaultValue;
	}

	public static Integer getInt(String path, int defaultValue) {
		String nodeValue = getNodeValue(path, Type.INTEGER);

		if (nodeValue != null) {
			return Integer.parseInt(nodeValue);
		}

		set(path, defaultValue);
		return defaultValue;
	}

	public static Float getFloat(String path, float defaultValue) {
		String nodeValue = getNodeValue(path, Type.FLOAT);

		if (nodeValue != null) {
			return Float.parseFloat(nodeValue);
		}

		set(path, defaultValue);
		return defaultValue;
	}

	public static void remove(String path) {
		try {
			Element node = (Element) xpath.evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {
				node.getParentNode().removeChild(node);
			}
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	private static Element set(String path, String value, Type type) {
		try {
			Element node = (Element) xpath.evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {

				String actualType = node.getAttributes().getNamedItem("type").getNodeValue();

				if (actualType.charAt(0) != type.code) {
					throw new InvalidTypeException("Trying to read " + type + " from " + actualType + " setting!");
				}

				node.setNodeValue(value);

			} else {

				int lastPart = path.lastIndexOf('/');

				Element parentNode;
				String nodeName;

				if (lastPart == -1) {
					
					parentNode = rootElement;
					nodeName = path;
					
				} else if (lastPart == 0) {
					
					parentNode = doc.getDocumentElement();
					nodeName = path.substring(1);
					
				} else {
					
					String parentPath = path.substring(0, lastPart);
					nodeName = path.substring(1 + parentPath.length());
					parentNode = ensureNode(parentPath);
				}

				node = XMLUtils.createElement(nodeName, value, doc);
				node.setAttribute("type", String.valueOf(type.code));
				parentNode.appendChild(node);
			}

			return node;

		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	private static Element ensureNode(String path) {
		try {
			Element node = (Element) xpath.evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {
				return node;
			}

			int lastPart = path.lastIndexOf('/');

			if (lastPart == -1) {
				
				return XMLUtils.appendNewElement(doc, rootElement, path);
				
			} else if (lastPart == 0) {

				return XMLUtils.appendNewElement(doc, doc.getDocumentElement(), path.substring(1));

			} else {

				String parentPath = path.substring(0, lastPart);
				String nodeName = path.substring(1 + parentPath.length());

				return XMLUtils.appendNewElement(doc, ensureNode(parentPath), nodeName);
			}

		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public static void setMatching(String path, String value) {
		try {
			Element node = (Element) xpath.evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {

				String actualType = node.getAttributes().getNamedItem("type").getNodeValue();

				Type type = Type.valueOf(actualType.charAt(0));

				if (type == Type.BOOLEAN) {
					if (value != "true" && value != "false") {
						throw new InvalidTypeException("Trying to write " + value + " to " + actualType + " setting!");
					}

				} else if (type == Type.INTEGER) {
					if (!IntegerPopulator.getPopulator().validateFormat(value)) {
						throw new InvalidTypeException("Trying to write " + value + " to " + actualType + " setting!");
					}

				} else if (type == Type.FLOAT) {
					if (!FloatPopulator.getPopulator().validateFormat(value)) {
						throw new InvalidTypeException("Trying to write " + value + " to " + actualType + " setting!");
					}
				}

				node.setNodeValue(value);

			} else {

				throw new RuntimeException("Node missing " + path);
			}

		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public static Element set(String path, String value) {
		return set(path, value, Type.STRING);
	}

	public static Element set(String path, boolean value) {
		return set(path, Boolean.toString(value), Type.BOOLEAN);
	}

	public static Element set(String path, int value) {
		return set(path, Integer.toString(value), Type.INTEGER);
	}

	public static Element set(String path, float value) {
		return set(path, Float.toString(value), Type.FLOAT);
	}

	public static boolean save() {
		log.info("Saving settings");

		try {
			XMLUtils.writeXMLFile(doc, new File("settings.xml"), true, "UTF-8");
			return true;

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

	public static boolean load(String rootName) {
		File file = new File("settings.xml");

		if (!file.exists()) {

			doc = XMLUtils.createDomDocument();
			rootElement = doc.createElement(rootName);
			doc.appendChild(rootElement);
			return true;

		} else {

			try {
				doc = XMLUtils.parseXMLFile(file, false, false);
				try {
					rootElement = (Element) xpath.evaluate("/" + rootName, doc.getDocumentElement(), XPathConstants.NODE);
				} catch (XPathExpressionException e) {
					log.error("Error reading root node", e);
				}

				if (rootElement == null) {
					rootElement = doc.createElement(rootName);
					doc.appendChild(rootElement);
				}

				return true;

			} catch (SAXException e) {
				log.warn("SAXException: " + e + " while reading xml");
			} catch (IOException e) {
				log.warn("IOException: " + e + " while reading xml");
			} catch (ParserConfigurationException e) {
				log.warn("ParserConfigurationException: " + e + " while reading xml");
			}
		}

		return false;
	}

	public static boolean start(String rootName, JSAP jsap, JSAPResult conf) {
		if (!load(rootName)) {
			return false;
		}

		loadCommandLine(jsap, conf);
		return true;
	}

	@SuppressWarnings("unchecked")
	public static void loadCommandLine(JSAP jsap, JSAPResult config) {
		Iterator<String> it = jsap.getIDMap().idIterator();

		while (it.hasNext()) {
			String option = it.next();

			if (config.userSpecified(option)) {
				try {
					setMatching(option, config.getString(option));
				} catch (UnspecifiedParameterException e) {}
			}
		}
	}

	static class InvalidTypeException extends RuntimeException {

		private static final long serialVersionUID = -5958204037127245704L;

		InvalidTypeException() {
			super();
		}

		InvalidTypeException(String arg0) {
			super(arg0);
		}

		InvalidTypeException(Throwable arg0) {
			super(arg0);
		}

		InvalidTypeException(String arg0, Throwable arg1) {
			super(arg0, arg1);
		}

	}

}
