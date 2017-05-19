/*******************************************************************************
 * Copyright (c) 2010 Robert "Unlogic" Olofsson (unlogic@unlogic.se).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-3.0-standalone.html
 ******************************************************************************/
package se.unlogic.standardutils.json;

/**
 * A JSON value component (no children)
 * 
 * Equalizes the "leaf" of the "composite pattern" design pattern.
 * @author sikstromj
 *
 */
public class JsonLeaf implements JsonNode {

	private static final long serialVersionUID = 1198871458505471824L;
	private String value;

	public JsonLeaf(Object value) {
		this(value, true);
	}

	public JsonLeaf(Object value, boolean appendQuotes) {
		
		StringBuilder stringBuilder = new StringBuilder();
		
		if(value == null) {
			stringBuilder.append("null");
			
		}else if(value instanceof Number || value instanceof Boolean) {
			stringBuilder.append(value.toString());
			
		} else {
			
			if(appendQuotes) {
				stringBuilder.append("\"");
			}
			
			stringBuilder.append(getEscapedValue(value.toString()));
			
			if(appendQuotes) {
				stringBuilder.append("\"");
			}
		}
		
		this.value = stringBuilder.toString();
	}

	public String toJson() {
		return value;
	}
	
	@Override
	public String toJson(StringBuilder stringBuilder) {

		return stringBuilder.append(value).toString();
	}

	// Code from codehaus/jettision Licensed under Apache 2.0
	// Relevant RFC http://www.ietf.org/rfc/rfc4627.txt
	private String getEscapedValue(String value) {

		int len = value.length();
		StringBuilder sb = new StringBuilder(len + 4);

		for (int i = 0; i < len; i += 1) {
			char c = value.charAt(i);

			switch(c){
				case '\\':
				case '"':
				case '/':
					sb.append('\\');
					sb.append(c);
					break;
				case '\b':
					sb.append("\\b");
					break;
				case '\t':
					sb.append("\\t");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\f':
					sb.append("\\f");
					break;
				case '\r':
					sb.append("\\r");
					break;
				default:
					if (c < ' ') {
						String t = "000" + Integer.toHexString(c);
						sb.append("\\u" + t.substring(t.length() - 4));
					} else {
						sb.append(c);
					}
			}
		}
		return sb.toString();
	}
}
