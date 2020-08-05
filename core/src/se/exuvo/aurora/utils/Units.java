package se.exuvo.aurora.utils;

public class Units {

	public static final double AU = 149597870.7; // In km
	public static final double C =   299792.458; // In km
	
	public static final long NANO_MICRO = 1000;
	public static final long NANO_MILLI = 1000 * NANO_MICRO;
	public static final long NANO_SECOND = 1000 * NANO_MILLI;
	
	public static final long KILO = 1000;
	public static final long MEGA = 1000 * KILO;
	public static final long GIGA = 1000 * MEGA;
	public static final long TERA = 1000 * GIGA;
	
	public static final long CUBIC_DECIMETRE = 1000;
	public static final long CUBIC_METRE     = 1000 * CUBIC_DECIMETRE;
	
	public static String nanoToString(long nanotime) {

		int nanos = (int) (nanotime % NANO_MILLI);
		int milli = (int) (nanotime / NANO_MILLI);
		
		return String.format("%d.%06dms", milli, nanos);
	}
	
	public static String nanoToMicroString(long nanotime) {

		int nanos = (int) (nanotime % NANO_MICRO);
		int micro = (int) (nanotime / NANO_MICRO);
		
		return String.format("%d.%03dus", micro, nanos);
	}
	
	public static String milliToString(long millitime) {

		int micros = (int) (millitime % 1000);
		int seconds = (int) (millitime / 1000);

		return String.format("%d.%06ds", seconds, micros);
	}

	public static String secondsToString(long time) {

		int seconds = (int) (time % 60);
		int minutes = (int) ((time / 60) % 60);
		int hours = (int) ((time / (60 * 60)) % 24);

		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}
	
	public static String powerToString(long power) {
		
		if (power < KILO) {
			return String.format("%d W", power);
			
		} else if (power < MEGA) {
			return String.format("%d.%02d kW", power / KILO, power % KILO / 10);
			
		} else if (power < GIGA) {
			return String.format("%d.%02d MW", power / MEGA, power % MEGA / 10 / KILO);
			
		} else if (power < TERA) {
			return String.format("%d.%02d GW", power / GIGA, power % GIGA / 10 / MEGA);
			
		} else if (power < 1000 * TERA) {
			return String.format("%d.%02d TW", power / TERA, power % TERA / 10 / GIGA);
			
		} else {
			return String.format("%d TW", power / TERA);
		}
	}
	
	public static String capacityToString(long capacity) {
		
		if (capacity < KILO) {
			return String.format("%d J", capacity);
			
		} else if (capacity < MEGA) {
			return String.format("%d.%02d kJ", capacity / KILO, capacity % KILO / 10);
			
		} else if (capacity < GIGA) {
			return String.format("%d.%02d MJ", capacity / MEGA, capacity % MEGA / 10 / KILO);
			
		} else if (capacity < TERA) {
			return String.format("%d.%02d GJ", capacity / GIGA, capacity % GIGA / 10 / MEGA);
			
		} else if (capacity < 1000 * TERA) {
			return String.format("%d.%02d TJ", capacity / TERA, capacity % TERA / 10 / GIGA);
			
		} else {
			return String.format("%d TJ", capacity / TERA);
		}
	}
	
	public static String volumeToString(long volume) {
		
		if (volume < CUBIC_DECIMETRE) {
			return String.format("%d cm³", volume);
			
		} else if (volume < CUBIC_METRE) {
			return String.format("%d.%02d dm³", volume / CUBIC_DECIMETRE, volume % CUBIC_DECIMETRE / 10);
			
		} else {
			return String.format("%d m³", volume / CUBIC_METRE);
		}
	}

	public static String massToString(long mass) { // Base mass is in kg

		if (mass < KILO) {
			return String.format("%d kg", mass);

		} else if (mass < MEGA) {
			return String.format("%d.%02d Mg", mass / KILO, mass % KILO / 10);

		} else if (mass < GIGA) {
			return String.format("%d.%02d Gg", mass / MEGA, mass % MEGA / 10 / KILO);

		} else if (mass < TERA) {
			return String.format("%d.%02d Tg", mass / GIGA, mass % GIGA / 10 / MEGA);

		} else if (mass < 1000 * TERA) {
			return String.format("%d.%02d Pg", mass / TERA, mass % TERA / 10 / GIGA);

		} else {
			return String.format("%d Pg", mass / TERA);
		}
	}
	
	public static String distanceToString(long distance) { // in m

		if (distance < KILO) {
			return String.format("%d m", distance);

		} else if (distance < MEGA) {
			return String.format("%d.%02d km", distance / KILO, distance % KILO / 10);

		} else if (distance < GIGA) {
			return String.format("%d.%02d Mm", distance / MEGA, distance % MEGA / 10 / KILO);

		} else if (distance < TERA) {
			return String.format("%d.%02d Gm", distance / GIGA, distance % GIGA / 10 / MEGA);

		} else if (distance < 1000 * TERA) {
			return String.format("%d.%02d Tm", distance / TERA, distance % TERA / 10 / GIGA);

		} else {
			return String.format("%d Pm", distance / TERA);
		}
	}
	
	public static String daysToRemaining(int days) {

		if (days <= 365) {
			
			return String.format("%3d days", days);
			
		} else {
			
			int year = days / 365;
			
			return String.format("%d years %3d days", year, days % 365);
		}
	}
	
	public static String daysToDate(int days) {

		int year = 2100 + days / 365;
		
		return String.format("%04d-%03d", year, 1 + days % 365);
	}
	
	public static String daysToYear(int days) {

		int year = 2100 + days / 365;
		
		return String.format("%04d", year);
	}
	
	public static String daysToSubYear(int days) {

		int year = days / 365;
		
		return String.format("%02d", year % 100);
	}
	
}
