package se.exuvo.aurora.utils;

public class TimeUnits {

	public static final long MICRO = 1000;
	public static final long NANO_MILLI = 1000 * MICRO;
	public static final long NANO_SECOND = 1000 * NANO_MILLI;

	public static String nanoToString(long time) {

		int micros = (int) (time % 1000);
		int seconds = (int) (time / NANO_MILLI);

		return String.format("%d.%06ds", seconds, micros);
	}

	public static String secondsToString(long time) {

		int seconds = (int) (time % 60);
		int minutes = (int) ((time / 60) % 60);
		int hours = (int) ((time / (60 * 60)) % 24);
		int days = (int) (time / (60 * 60 * 24));

		return String.format("%d.%02d:%02d:%02ds", days, hours, minutes, seconds);
	}
}
