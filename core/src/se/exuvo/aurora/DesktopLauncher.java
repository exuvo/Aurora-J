package se.exuvo.aurora;

import java.nio.file.Paths;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class DesktopLauncher {

	public static void main(String[] args) {
		
		Lwjgl3ApplicationConfiguration windowConfig = new Lwjgl3ApplicationConfiguration();
		windowConfig.setTitle("test");
		windowConfig.useOpenGL3(true, 3, 2);
		windowConfig.setWindowedMode(1024, 1024);
		windowConfig.useVsync(false);
		windowConfig.setResizable(true);
		windowConfig.setPreferencesConfig(Paths.get("").toAbsolutePath().toString(), FileType.Absolute);

		try {
			new Lwjgl3Application(new AuroraGameMainWindow(), windowConfig);
			
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
