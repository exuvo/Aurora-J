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
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.utils.GdxRuntimeException;
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

		loadSettings(jsapConfig);

		Level level = Level.toLevel(Settings.getStr("loglvl"));
		Logger.getLogger("se.exuvo").setLevel(level);
		log.info("Changed log level to " + level);

		Lwjgl3ApplicationConfiguration windowConfig = new Lwjgl3ApplicationConfiguration();
		windowConfig.setTitle("Aurora J");

		if (Settings.getBol("G.Fullscreen")) {

			Monitor monitor;
			Integer monitorIndex = Settings.getInt("G.MonitorIndex");

			if (monitorIndex == null) {
				monitor = Lwjgl3ApplicationConfiguration.getPrimaryMonitor();

			} else {
				monitor = Lwjgl3ApplicationConfiguration.getMonitors()[monitorIndex];
			}

			DisplayMode displayMode = null;

			if (Settings.getInt("G.Width") == null && Settings.getInt("G.Height") == null) {

				displayMode = Lwjgl3ApplicationConfiguration.getDisplayMode(monitor);

			} else {

				DisplayMode[] displayModes = Lwjgl3ApplicationConfiguration.getDisplayModes(monitor);

				Integer refreshRate = Settings.getInt("G.RefreshRate");

				for (DisplayMode dm : displayModes) {

					if (dm.width == Settings.getInt("G.Width") && dm.height == Settings.getInt("G.Height") && (refreshRate == null || dm.refreshRate == refreshRate)) {
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

			windowConfig.setWindowedMode(Settings.getInt("G.Width"), Settings.getInt("G.Height"));
		}

//		windowConfig.foregroundFPS = Settings.getInt("G.FrameLimit");
		windowConfig.setIdleFPS(Settings.getInt("G.IdleFrameLimit"));
		windowConfig.useVsync(Settings.getBol("G.VSync"));
		windowConfig.setResizable(Settings.getBol("G.Resizable"));
		windowConfig.setPreferencesConfig(Paths.get("").toAbsolutePath().toString(), FileType.Absolute);

		String assetsURI = FileUtils.fileExists("assets") ? "assets/" : "../core/assets/";

		try {
			new Lwjgl3Application(new AuroraGame(assetsURI), windowConfig);
			
		} catch (GdxRuntimeException e) {
			log.error("", e);
			System.exit(1);
		}
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

//		Settings.add("G.FrameLimit", 60);
		Settings.add("G.IdleFrameLimit", 20);
		Settings.add("G.Width", 1024);
		Settings.add("G.Height", 768);
		Settings.add("G.Fullscreen", false);
		Settings.add("G.RefreshRate", 60);
		Settings.add("G.MonitorIndex", 0);
		Settings.add("G.VSync", false);
		Settings.add("G.Resizable", true);
		Settings.add("G.ShowFPS", true);

		Settings.add("UI.zoomSensitivity", 1.25f);

		Settings.add("Orbits.DotsRepresentSpeed", true);
		Settings.add("Render.DebugPassiveSensors", false);
		Settings.add("Galaxy.Threads", Runtime.getRuntime().availableProcessors());

		if (!Settings.start(conf, "AuroraJ")) {
			System.out.println("Failed to read settings from file, please fix. Exiting.");
			System.exit(2);
		}
	}

}
