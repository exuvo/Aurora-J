package se.exuvo.aurora.desktop;

import java.nio.file.Paths;
import java.util.Iterator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Switch;

import se.exuvo.aurora.AuroraGame;
import se.exuvo.settings.Settings;
import se.unlogic.standardutils.io.FileUtils;

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

		if (!Settings.start("Aurora", jsap, jsapConfig)) {
			System.out.println("Failed to read settings from file, please fix. Exiting.");
			System.exit(2);
		}

		Level level = Level.toLevel(Settings.getStr("loglevel", "INFO"));
		Logger.getLogger("se.exuvo").setLevel(level);
		log.info("Changed log level to " + level);

		Lwjgl3ApplicationConfiguration windowConfig = new Lwjgl3ApplicationConfiguration();
		windowConfig.setTitle("Aurora J");
		
		final int defaultWidth = 1024;
		final int defaultHeight = 768;

		if (Settings.getBol("Window/fullscreen", false)) {

			Monitor monitor;
			Integer monitorIndex = Settings.getInt("Window/monitorIndex", 0);

			if (monitorIndex == null) {
				monitor = Lwjgl3ApplicationConfiguration.getPrimaryMonitor();

			} else {
				monitor = Lwjgl3ApplicationConfiguration.getMonitors()[monitorIndex];
			}

			DisplayMode displayMode = null;

			if (Settings.getInt("Window/width", defaultWidth) == null && Settings.getInt("Window/height", defaultHeight) == null) {

				displayMode = Lwjgl3ApplicationConfiguration.getDisplayMode(monitor);

			} else {

				DisplayMode[] displayModes = Lwjgl3ApplicationConfiguration.getDisplayModes(monitor);

				Integer refreshRate = Settings.getInt("Window/refreshRate", 60);

				for (DisplayMode dm : displayModes) {

					if (dm.width == Settings.getInt("Window/width", defaultWidth) && dm.height == Settings.getInt("Window/height", defaultHeight) && (refreshRate == null || dm.refreshRate == refreshRate)) {
						displayMode = dm;
						break;
					}
				}

				if (displayMode == null) {

					throw new UnsupportedOperationException("Found no valid display mode for given display settings");
				}
			}

			windowConfig.setFullscreenMode(displayMode);

		} else {

			windowConfig.setWindowedMode(Settings.getInt("Window/width", defaultWidth), Settings.getInt("Window/height", defaultHeight));
		}

//		windowConfig.foregroundFPS = Settings.getInt("G.FrameLimit", 60);
		windowConfig.setIdleFPS(Settings.getInt("Window/idleFrameLimit", 20));
		windowConfig.useVsync(Settings.getBol("Window/vSync", false));
		windowConfig.setResizable(Settings.getBol("Window/resizable", true));
		windowConfig.setPreferencesConfig(Paths.get("").toAbsolutePath().toString(), FileType.Absolute);

		String assetsURI = FileUtils.fileExists("assets") ? "assets/" : "../core/assets/";

		try {
			new Lwjgl3Application(new AuroraGame(assetsURI), windowConfig);
			
		} catch (Throwable e) {
			log.error("", e);
			System.exit(1);
		}
	}

	private static final void arguments(JSAP jsap) {
		Switch fscreen = new Switch("Window/fullscreen").setShortFlag('f').setLongFlag("fullscreen");
		fscreen.setHelp("Run in fullscreen.");

		try {
			jsap.registerParameter(fscreen);
		} catch (JSAPException e) {
			System.out.println("JSAP: Failed to register parameters due to: " + e);
		}
	}

}
