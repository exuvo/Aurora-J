package se.exuvo.aurora.desktop;

import java.nio.file.Paths;
import java.util.Iterator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Switch;

import se.exuvo.aurora.AuroraGame;
import se.exuvo.settings.Settings;

public class DesktopLauncher {

	private static Logger log;

	public static void main(String[] args) {
		DOMConfigurator.configure("log4j.xml");
		log = Logger.getLogger(DesktopLauncher.class);
		log.fatal("### Starting ###");

		JSAP jsap = new JSAP();
		arguments(jsap);

		JSAPResult jsapConfig = jsap.parse(args);
		// check whether the command line was valid, and if it wasn't, display usage information and exit.
		if (!jsapConfig.success()) {
			System.err.println();
			// print out specific error messages describing the problems
			// with the command line, THEN print usage, THEN print full
			// help. This is called "beating the user with a clue stick."
			for (Iterator<?> errs = jsapConfig.getErrorMessageIterator(); errs.hasNext();) {
				System.err.println("Error: " + errs.next());
			}

			System.err.println();
			System.err.println("Usage: java " + DesktopLauncher.class.getName());
			System.err.println("                " + jsap.getUsage());
			System.err.println("All parameters override config settings");
			System.err.println();
			// show full help as well
			System.err.println(jsap.getHelp());
			System.exit(1);
		}

		loadSettings(jsapConfig);

		Level level = Level.toLevel(Settings.getStr("loglvl"));
		Logger.getLogger("se.exuvo").setLevel(level);
		log.info("Changed log level to " + level);

		LwjglApplicationConfiguration windowConfig = new LwjglApplicationConfiguration();
		windowConfig.title = "Aurora J";
		windowConfig.width = Settings.getInt("G.Width");
		windowConfig.height = Settings.getInt("G.Height");
		windowConfig.foregroundFPS = Settings.getInt("G.FrameLimit");
		windowConfig.backgroundFPS = Settings.getInt("G.BackgroundFrameLimit");
		windowConfig.fullscreen = Settings.getBol("G.Fullscreen");
		windowConfig.vSyncEnabled = Settings.getBol("G.VSync");
		windowConfig.resizable = Settings.getBol("G.Resizable");
		windowConfig.preferencesDirectory = Paths.get("").toAbsolutePath().toString();

		new LwjglApplication(new AuroraGame(), windowConfig);
	}

	private static final void arguments(JSAP jsap) {
		Switch fscreen = new Switch("G.Fullscreen").setShortFlag('f').setLongFlag("fullscreen");
		fscreen.setHelp("Run in fullscreen.");

		try {
			jsap.registerParameter(fscreen);
		} catch (JSAPException e) {
			System.out.println("JSAP: Failed to register parameters due to: " + e);
		}
	}

	private static final void loadSettings(JSAPResult conf) {
		Settings.add("loglvl", "INFO");
		Settings.add("G.FrameLimit", 60);
		Settings.add("G.BackgroundFrameLimit", 20);
		Settings.add("G.Width", 1024);
		Settings.add("G.Height", 768);
		Settings.add("G.Fullscreen", false);
		Settings.add("G.VSync", false);
		Settings.add("G.Resizable", true);
		Settings.add("G.ShowFPS", true);

		if (!Settings.start(conf, "AuroraJ")) {
			System.out.println("Failed to read settings from file, please fix. Exiting.");
			System.exit(2);
		}
	}

}
