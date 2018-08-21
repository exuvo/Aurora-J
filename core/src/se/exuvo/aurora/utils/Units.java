package se.exuvo.aurora.utils;

public class Units {

	public static final long NANO_MICRO = 1000;
	public static final long NANO_MILLI = 1000 * NANO_MICRO;
	public static final long NANO_SECOND = 1000 * NANO_MILLI;
	
	public static final long KILOWATT = 1000;
	public static final long MEGAWATT = 1000 * KILOWATT;
	public static final long GIGAWATT = 1000 * MEGAWATT;
	public static final long TERAWATT = 1000 * GIGAWATT;

	public static String nanoToString(long nanotime) {

		int nanos = (int) (nanotime % NANO_MILLI);
		int milli = (int) (nanotime / NANO_MILLI);
		
		return String.format("%d.%06dms", milli, nanos);
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
		int days = (int) (time / (60 * 60 * 24));

		return String.format("%d.%02d:%02d:%02ds", days, hours, minutes, seconds);
	}
	
	public static String powerToString(long power) {
		
		if (power < KILOWATT) {
			return String.format("%d W", power);
			
		} else if (power < MEGAWATT) {
			return String.format("%d.%02d kW", power / KILOWATT, power % KILOWATT / 10);
			
		} else if (power < GIGAWATT) {
			return String.format("%d.%02d MW", power / MEGAWATT, power % MEGAWATT / 10 / KILOWATT);
			
		} else if (power < TERAWATT) {
			return String.format("%d.%02d GW", power / GIGAWATT, power % GIGAWATT / 10 / MEGAWATT);
			
		} else if (power < 1000 * TERAWATT) {
			return String.format("%d.%02d TW", power / TERAWATT, power % TERAWATT / 10 / GIGAWATT);
			
		} else {
			return String.format("%d TW", power / TERAWATT);
		}
	}
	
//	public static void main(String args[]) {
//		System.out.println(powerToString(1));
//		System.out.println(powerToString(12));
//		System.out.println(powerToString(123));
//		System.out.println(powerToString(1234));
//		System.out.println(powerToString(12345));
//		System.out.println(powerToString(123456));
//		System.out.println(powerToString(1234567));
//		System.out.println(powerToString(12345678));
//		System.out.println(powerToString(123456789));
//		System.out.println(powerToString(1234567890));
//		System.out.println(powerToString(12345678901L));
//		System.out.println(powerToString(123456789012L));
//		System.out.println(powerToString(1234567890123L));
//		System.out.println(powerToString(12345678901234L));
//		System.out.println(powerToString(123456789012345L));
//		System.out.println(powerToString(1234567890123456L));
//		System.out.println(powerToString(12345678901234567L));
//		System.out.println(powerToString(123456789012345678L));
//		System.out.println(powerToString(1234567890123456789L));
//	}
}
