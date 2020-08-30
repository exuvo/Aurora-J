package se.exuvo.aurora;

import java.nio.file.Paths;
import java.util.Iterator;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.io.IoBuilder;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import com.badlogic.gdx.backends.lwjgl3.CustomLwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Switch;

import se.exuvo.aurora.ui.keys.KeyMappings;
import se.exuvo.settings.Settings;
import se.unlogic.standardutils.io.FileUtils;

public class DesktopLauncher {

	private static Logger log;

	public static void main(String[] args) {
		
		String logConfigURI = FileUtils.fileExists("log4j2.xml") ? "log4j2.xml" : DesktopLauncher.class.getResource("/log4j2.xml").toString();
		
		System.setProperty("log4j.configurationFile", logConfigURI);
		
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
//		Configuration.DEBUG.set(true);
//		Configuration.DEBUG_STREAM.set(IoBuilder.forLogger(glLog).setLevel(Level.INFO).buildPrintStream());
		
		Lwjgl3ApplicationConfiguration windowConfig = new Lwjgl3ApplicationConfiguration();
		windowConfig.setTitle("Aurora J");
		//TODO auto try latest or 4.4, 4.2, 3.2, 2.1
		windowConfig.useOpenGL3(true, 4, 2);
//		windowConfig.useOpenGL3(true, 3, 2); // for mac
//		windowConfig.useOpenGL3(false, 2, 1); // for laptop
		windowConfig.enableGLDebugOutput(true, IoBuilder.forLogger(glLog).setLevel(Level.WARN).buildPrintStream());
		
		final int defaultWidth = 1024;
		final int defaultHeight = 768;

		if (Settings.getBol("Window/fullscreen", false)) {

			Monitor monitor;
			Integer monitorIndex = Settings.getInt("Window/monitorIndex");

			if (monitorIndex == null) {
				monitor = Lwjgl3ApplicationConfiguration.getPrimaryMonitor();

			} else {
				monitor = Lwjgl3ApplicationConfiguration.getMonitors()[monitorIndex];
			}

			Integer refreshRate = Settings.getInt("Window/refreshRate");
			
			DisplayMode[] displayModes = Lwjgl3ApplicationConfiguration.getDisplayModes(monitor);
			DisplayMode displayMode = null;

			for (DisplayMode dm : displayModes) {
				if (dm.width == Settings.getInt("Window/width", defaultWidth) && dm.height == Settings.getInt("Window/height", defaultHeight) 
						&& ((refreshRate == null && (displayMode == null || displayMode.refreshRate < dm.refreshRate))
								|| dm.refreshRate == refreshRate)) {
					displayMode = dm;
					break;
				}
			}
			
			if (displayMode == null) {
				
				log.warn("Found no valid display mode for given display settings, using current monitor display mode instead.");
				displayMode = Lwjgl3ApplicationConfiguration.getDisplayMode(monitor);
			}

			if (displayMode == null) {

				throw new UnsupportedOperationException("Found no valid display mode");
			}

			windowConfig.setFullscreenMode(displayMode);

		} else {

			windowConfig.setWindowedMode(Settings.getInt("Window/width", defaultWidth), Settings.getInt("Window/height", defaultHeight));
//			windowConfig.setWindowPosition(2000, 100);

//			windowConfig.setMaximized(true);
//			windowConfig.setMaximizedMonitor(Lwjgl3ApplicationConfiguration.getMonitors()[0]);
			//TODO parts of window are not rendered until resize when on main GPU
		}

//		windowConfig.foregroundFPS = Settings.getInt("Window/FrameLimit", 60);
		windowConfig.useVsync(Settings.getBol("Window/vSync", false));
		windowConfig.setResizable(Settings.getBol("Window/resizable", true));
		windowConfig.setPreferencesConfig(Paths.get("").toAbsolutePath().toString(), FileType.Absolute);
		
//		windowConfig.setHdpiMode(HdpiMode.Pixels);

		try {
			new CustomLwjgl3Application(new AuroraGameMainWindow(), windowConfig, Settings.getInt("Window/FrameLimit", 60));
			
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
