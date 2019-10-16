package se.exuvo.aurora.desktop;

import java.nio.file.Paths;
import java.util.Iterator;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.io.IoBuilder;
import org.lwjgl.system.Configuration;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.backends.lwjgl3.CustomLwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Switch;

import se.exuvo.aurora.AuroraGameMainWindow;
import se.exuvo.aurora.utils.keys.KeyMappings;
import se.exuvo.settings.Settings;
import se.unlogic.standardutils.io.FileUtils;

public class DesktopLauncher {

	private static Logger log;

	public static void main(String[] args) {
		System.setProperty("log4j.configurationFile", "log4j2.xml");
		log = LogManager.getLogger(DesktopLauncher.class);
		log.fatal("### Starting ###");
		Logger glLog = LogManager.getLogger("org.opengl");
		glLog.fatal("### Starting ###");

		JSAP jsap = new JSAP();
		arguments(jsap);

		JSAPResult jsapConfig = jsap.parse(args);
		// check whether the command line was valid, and if it wasn't, display usage information and exit.
		if (!jsapConfig.success()) {
			System.err.println();
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
		Configurator.setLevel("se.exuvo", level);
		log.info("Changed log level to " + level);
		
		//https://github.com/LWJGL/lwjgl3-wiki/wiki/2.5.-Troubleshooting
		Configuration.DEBUG.set(true);
//		Configuration.DEBUG_STREAM.set(IoBuilder.forLogger(glLog).setLevel(Level.INFO).buildPrintStream());
		
		Lwjgl3ApplicationConfiguration windowConfig = new Lwjgl3ApplicationConfiguration();
		windowConfig.setTitle("Aurora J");
//		windowConfig.useOpenGL3(true, 4, 4);
		windowConfig.useOpenGL3(false, 2, 1);
		windowConfig.enableGLDebugOutput(true, IoBuilder.forLogger(glLog).setLevel(Level.WARN).buildPrintStream());
		
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

//		windowConfig.foregroundFPS = Settings.getInt("Window/FrameLimit", 60);
		windowConfig.useVsync(Settings.getBol("Window/vSync", false));
		windowConfig.setResizable(Settings.getBol("Window/resizable", true));
		windowConfig.setPreferencesConfig(Paths.get("").toAbsolutePath().toString(), FileType.Absolute);

		String assetsURI = FileUtils.fileExists("assets") ? "assets/" : "../core/assets/";

		try {
			new CustomLwjgl3Application(new AuroraGameMainWindow(assetsURI), windowConfig, Settings.getInt("Window/FrameLimit", 60));
			
		} catch (Throwable e) {
			log.error("", e);
			
			if (KeyMappings.loaded) {
				KeyMappings.save();
			}
			
			Settings.save();
			
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
