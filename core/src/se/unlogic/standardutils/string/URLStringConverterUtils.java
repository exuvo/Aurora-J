package se.unlogic.standardutils.string;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

public class URLStringConverterUtils {

	private static final StringConverter ISO_TO_UTF8_STRING_DECODER = new SimpleStringConverter(Charset.forName("ISO-8859-1"), Charset.forName("UTF-8"));
	private static final StringConverter UTF8_TO_ISO_STRING_DECODER = new SimpleStringConverter(Charset.forName("UTF-8"), Charset.forName("ISO-8859-1"));;

	/** Use when you know the input is supposed to be UTF-8 but has been incorrectly parsed as ISO-8859-1 */
	public static StringConverter getUTF8StringDecoder() {

		return ISO_TO_UTF8_STRING_DECODER;
	}

	/** Use when you know the input is supposed to be ISO-8859-1 but has been incorrectly parsed as UTF-8. Characters might already have been replaced and be non recoverable. */
	public static StringConverter getISOStringDecoder() {

		return UTF8_TO_ISO_STRING_DECODER;
	}

	/** Prints text to System.out in system default encoding, UTF-8, Cp1252 and ISO-8859-1.
	 * Text should look correct in each mode when your terminal is set to read in that mode.
	 * If text instead looks correct with a mismatched mode (ex terminal ISO and writing UTF8) then you have an encoding mismatch. */
	public static void debugEncoding(String input) {

		System.out.println();
		System.out.println(input);
		new PrintWriter(new OutputStreamWriter(System.out, Charset.forName("UTF-8")), true).println(input + " UTF-8");
		new PrintWriter(new OutputStreamWriter(System.out, Charset.forName("Cp1252")), true).println(input + " Cp1252");
		new PrintWriter(new OutputStreamWriter(System.out, Charset.forName("ISO-8859-1")), true).println(input + " ISO-8859-1");
	}
}
