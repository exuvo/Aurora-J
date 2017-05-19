/*******************************************************************************
 * Copyright (c) 2010 Robert "Unlogic" Olofsson (unlogic@unlogic.se).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-3.0-standalone.html
 ******************************************************************************/
package se.exuvo.aurora.utils;

public class NanoTimeUnits {

	public static final long MICRO = 1000;
	public static final long MILLI = 1000 * MICRO;
	public static final long SECOND = 1000 * MILLI;
	
	public static String nanoToString(long time) {

		int micros = (int)(time % 1000);
		int seconds = (int)(time / MILLI);
		
		return String.format("%d.%06ds", seconds, micros);
	}
}
