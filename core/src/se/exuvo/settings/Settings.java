package se.exuvo.settings;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.UnspecifiedParameterException;

import se.unlogic.standardutils.numbers.NumberUtils;
import se.unlogic.standardutils.xml.PooledXPathFactory;
import se.unlogic.standardutils.xml.XMLUtils;

public class Settings {

	protected static final Logger log = LogManager.getLogger(Settings.class);
	private static Document doc;
	private static Element rootElement;

	public static Element getNode(String path) {
		try {
			return (Element) PooledXPathFactory.newXPath().evaluate(path, rootElement, XPathConstants.NODE);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public static String getNodeValue(String path) {
		try {
			Element node = (Element) PooledXPathFactory.newXPath().evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {
				return node.getTextContent();
			}

		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}

		return null;
	}

	public static String getStr(String path) {
		String nodeValue = getNodeValue(path);

		return nodeValue;
	}

	public static Boolean getBol(String path) {
		String nodeValue = getNodeValue(path);

		if (nodeValue != null) {
			
			if ("true".equals(nodeValue)) {
				return true;
				
			} else if ("false".equals(nodeValue)) {
				return false;
			}
			
			throw new InvalidTypeException("Trying to read boolean from \"" + nodeValue + "\" at " + path + "!");
		}

		return null;
	}

	public static Integer getInt(String path) {
		String nodeValue = getNodeValue(path);

		if (nodeValue != null) {
			try {
				return Integer.parseInt(nodeValue);

			} catch (NumberFormatException e) {
				throw new InvalidTypeException("Trying to read integer from \"" + nodeValue + "\" at " + path + "!", e);
			}
		}

		return null;
	}

	public static Float getFloat(String path) {
		String nodeValue = getNodeValue(path);

		if (nodeValue != null) {
			try {
				return Float.parseFloat(nodeValue);
				
			} catch (NumberFormatException e) {
				throw new InvalidTypeException("Trying to read float from \"" + nodeValue + "\" at " + path + "!", e);
			}
		}

		return null;
	}

	public static Double getDouble(String path) {
		String nodeValue = getNodeValue(path);

		if (nodeValue != null) {
			try {
				return Double.parseDouble(nodeValue);
				
			} catch (NumberFormatException e) {
				throw new InvalidTypeException("Trying to read double from \"" + nodeValue + "\" at " + path + "!", e);
			}
		}

		return null;
	}

	public static String getStr(String path, String defaultValue) {
		String nodeValue = getNodeValue(path);

		if (nodeValue == null) {
			set(path, defaultValue);
		}

		return nodeValue;
	}

	public static boolean getBol(String path, boolean defaultValue) {
		String nodeValue = getNodeValue(path);

		if (nodeValue != null) {
			return Boolean.parseBoolean(nodeValue);
		}

		set(path, defaultValue);
		return defaultValue;
	}

	public static int getInt(String path, int defaultValue) {
		String nodeValue = getNodeValue(path);

		if (nodeValue != null) {
			return Integer.parseInt(nodeValue);
		}

		set(path, defaultValue);
		return defaultValue;
	}

	public static float getFloat(String path, float defaultValue) {
		String nodeValue = getNodeValue(path);

		if (nodeValue != null) {
			return Float.parseFloat(nodeValue);
		}

		set(path, defaultValue);
		return defaultValue;
	}
	
	public static double getDouble(String path, double defaultValue) {
		String nodeValue = getNodeValue(path);

		if (nodeValue != null) {
			return Double.parseDouble(nodeValue);
		}

		set(path, defaultValue);
		return defaultValue;
	}

	public static void remove(String path) {
		try {
			Element node = (Element) PooledXPathFactory.newXPath().evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {
				node.getParentNode().removeChild(node);
			}
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	private static Element createNode(String path, String value) {
		
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

		Element node = XMLUtils.createElement(nodeName, value, doc);
		parentNode.appendChild(node);
		
		return node;
	}
	
	public static Element set(String path, String value) {
		try {
			Element node = (Element) PooledXPathFactory.newXPath().evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {

				String currentValue = node.getTextContent();

				if ("true".equals(currentValue) || "false".equals(currentValue)) {
					throw new InvalidTypeException("Trying to write " + value + " to boolean setting!");

				} else if (NumberUtils.toInt(currentValue) != null) {
					throw new InvalidTypeException("Trying to write " + value + " to int setting!");

				} else if (NumberUtils.isFloat(currentValue)) {
					throw new InvalidTypeException("Trying to write " + value + " to float setting!");

				} else if (NumberUtils.isDouble(currentValue)) {
					throw new InvalidTypeException("Trying to write " + value + " to double setting!");
				}

				node.setNodeValue(value);
				return node;

			} else {

				return createNode(path, value);
			}

		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void setMatching(String path, String value) {
		try {
			Element node = (Element) PooledXPathFactory.newXPath().evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {

				String currentValue = node.getTextContent();

				if ("true".equals(currentValue) || "false".equals(currentValue)) {
					if (value != "true" && value != "false") {
						throw new InvalidTypeException("Trying to write " + value + " to boolean setting!");
					}

				} else if (NumberUtils.toInt(currentValue) != null) {
					if (!NumberUtils.isInt(value)) {
						throw new InvalidTypeException("Trying to write " + value + " to int setting!");
					}

				} else if (NumberUtils.isFloat(currentValue)) {
					if (!NumberUtils.isFloat(value)) {
						throw new InvalidTypeException("Trying to write " + value + " to float setting!");
					}
					
				} else if (NumberUtils.isDouble(currentValue)) {
					if (!NumberUtils.isDouble(value)) {
						throw new InvalidTypeException("Trying to write " + value + " to double setting!");
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
	
	public static Element set(String path, boolean value) {
		try {
			Element node = (Element) PooledXPathFactory.newXPath().evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {
				
				String currentValue = node.getTextContent();
				
				if (!"true".equals(currentValue) && !"false".equals(currentValue)) {
					throw new InvalidTypeException("Trying to write " + value + " to non boolean setting!");
				}

				node.setNodeValue(Boolean.toString(value));
				return node;

			} else {

				return createNode(path, Boolean.toString(value));
			}

		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public static Element set(String path, int value) {
		try {
			Element node = (Element) PooledXPathFactory.newXPath().evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {
				
				String currentValue = node.getTextContent();
				
				if (NumberUtils.toInt(currentValue) == null) {
					throw new InvalidTypeException("Trying to write " + value + " to non int setting!");
				}

				node.setNodeValue(Integer.toString(value));
				return node;

			} else {

				return createNode(path, Integer.toString(value));
			}

		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	public static Element set(String path, float value) {
		try {
			Element node = (Element) PooledXPathFactory.newXPath().evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {
				
				String currentValue = node.getTextContent();
				
				if (!NumberUtils.isFloat(currentValue)) {
					throw new InvalidTypeException("Trying to write " + value + " to non float setting!");
				}

				node.setNodeValue(Float.toString(value));
				return node;

			} else {

				return createNode(path, Float.toString(value));
			}

		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static Element set(String path, double value) {
		try {
			Element node = (Element) PooledXPathFactory.newXPath().evaluate(path, rootElement, XPathConstants.NODE);

			if (node != null) {

				String currentValue = node.getTextContent();
				
				if (!NumberUtils.isDouble(currentValue)) {
					throw new InvalidTypeException("Trying to write " + value + " to non double setting!");
				}
				
				node.setNodeValue(Double.toString(value));
				return node;

			} else {

				return createNode(path, Double.toString(value));
			}

		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	private static Element ensureNode(String path) {
		try {
			Element node = (Element) PooledXPathFactory.newXPath().evaluate(path, rootElement, XPathConstants.NODE);

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

	public static boolean save() {
		log.info("Saving settings");

		try {
			doc.normalize();
			NodeList emptyTextNodes = (NodeList) PooledXPathFactory.newXPath().evaluate("//text()[normalize-space(.) = '']", rootElement, XPathConstants.NODESET);

			for (int i = 0; i < emptyTextNodes.getLength(); i++) {
				Node emptyTextNode = emptyTextNodes.item(i);
				emptyTextNode.getParentNode().removeChild(emptyTextNode);
			}
		} catch (XPathExpressionException e) {
			log.error("Error removing whitespace", e);
		}
		
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
				doc.normalize();
				try {
					rootElement = (Element) PooledXPathFactory.newXPath().evaluate("/" + rootName, doc.getDocumentElement(), XPathConstants.NODE);
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
