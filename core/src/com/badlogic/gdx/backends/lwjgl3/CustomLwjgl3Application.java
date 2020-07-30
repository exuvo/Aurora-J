package com.badlogic.gdx.backends.lwjgl3;

import java.io.File;
import java.io.PrintStream;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.AMDDebugOutput;
import org.lwjgl.opengl.ARBDebugOutput;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.system.Callback;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.lwjgl3.audio.Lwjgl3Audio;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio;
import com.badlogic.gdx.backends.lwjgl3.audio.mock.MockAudio;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Clipboard;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.SharedLibraryLoader;

import se.unlogic.standardutils.threads.ThreadUtils;

public class CustomLwjgl3Application implements Lwjgl3ApplicationBase {

	private final Lwjgl3ApplicationConfiguration config;
	final Array<CustomLwjgl3Window> windows = new Array<CustomLwjgl3Window>();
	private volatile CustomLwjgl3Window currentWindow;
	private Lwjgl3Audio audio;
	private final Files files;
	private final ObjectMap<String, Preferences> preferences = new ObjectMap<String, Preferences>();
	private final Lwjgl3Clipboard clipboard;
	private int logLevel = LOG_INFO;
	private ApplicationLogger applicationLogger;
	private volatile boolean running = true;
	private final Array<Runnable> runnables = new Array<Runnable>();
	private final Array<Runnable> executedRunnables = new Array<Runnable>();
	private final Array<LifecycleListener> lifecycleListeners = new Array<LifecycleListener>();
	private static GLFWErrorCallback errorCallback;
	private static GLVersion glVersion;
	private static Callback glDebugCallback;
	private long frameDelay;

	static void initializeGlfw() {
		if (errorCallback == null) {
			Lwjgl3NativesLoader.load();
			errorCallback = GLFWErrorCallback.createPrint(System.err);
			GLFW.glfwSetErrorCallback(errorCallback);
			GLFW.glfwInitHint(GLFW.GLFW_JOYSTICK_HAT_BUTTONS, GLFW.GLFW_FALSE);
			if (!GLFW.glfwInit()) {
				throw new GdxRuntimeException("Unable to initialize GLFW");
			}
		}
	}

	public CustomLwjgl3Application(ApplicationListener listener, Lwjgl3ApplicationConfiguration config, int framerate) {
		initializeGlfw();
		setApplicationLogger(new Lwjgl3ApplicationLogger());
		
		if (config.title == null) {
			config.title = listener.getClass().getSimpleName();
		}
		
		this.config = Lwjgl3ApplicationConfiguration.copy(config);
	
		Gdx.app = this;
		
		if (!config.disableAudio) {
			try {
				this.audio = createAudio(config);
			} catch (Throwable t) {
				log("Lwjgl3Application", "Couldn't initialize audio, disabling audio", t);
				this.audio = new MockAudio();
			}
		} else {
			this.audio = new MockAudio();
		}
		
		Gdx.audio = audio;
		
		this.files = Gdx.files = new Lwjgl3Files();
		this.clipboard = new Lwjgl3Clipboard();

		CustomLwjgl3Window window = createWindow(config, listener, 0);
		windows.add(window);
		
		frameDelay = 1000000000L / framerate; // 1 second in nanosecond units
		
		try {
			loop();
			cleanupWindows();
			
		} catch (Throwable t) {
			if (t instanceof RuntimeException) {
				throw (RuntimeException) t;
			} else  {
				throw new GdxRuntimeException(t);
			}
		} finally {
			cleanup();
		}
	}

	protected void loop() {
		Array<CustomLwjgl3Window> closedWindows = new Array<CustomLwjgl3Window>();
		
		long accumulator = 0L;
		long lastRun = System.nanoTime();
		
		while (running && windows.size > 0) {
			// FIXME put it on a separate thread
			audio.update();

			if (closedWindows.size > 0) {
				closedWindows.clear();
			}

			boolean shouldRender = false;

			{
				long now = System.nanoTime();
				accumulator += now - lastRun;

				if (accumulator >= frameDelay) {
					accumulator -= frameDelay;

					if (accumulator > frameDelay) {
						accumulator = frameDelay;
					}

					shouldRender = true;

//					println("frameDelay $frameDelay, diff $frameTime, accumulator $accumulator")

				} else if (accumulator < frameDelay && frameDelay > 1000000L) { // 1 millisecond in nanosecond units

					long sleepTime = (frameDelay - accumulator) / 1000000L;

					if (sleepTime > 1) {
						ThreadUtils.sleep(sleepTime - 1);
					} else {
						Thread.yield();
					}
				}

				lastRun = now;
			}

			for (CustomLwjgl3Window window : windows) {
				window.makeCurrent();
				currentWindow = window;
				
				synchronized (lifecycleListeners) {
					window.update(shouldRender);
				}
				
				if (window.shouldClose()) {
					closedWindows.add(window);
				}
			}
			
			GLFW.glfwPollEvents();

			synchronized (runnables) {
				executedRunnables.clear();
				executedRunnables.addAll(runnables);
				runnables.clear();
			}
			
			for (Runnable runnable : executedRunnables) {
				runnable.run();
			}

			if (closedWindows.size > 0) {
				for (CustomLwjgl3Window closedWindow : closedWindows) {
					if (windows.size == 1) {
						// Lifecycle listener methods have to be called before ApplicationListener methods. The
						// application will be disposed when _all_ windows have been disposed, which is the case,
						// when there is only 1 window left, which is in the process of being disposed.
						for (int i = lifecycleListeners.size - 1; i >= 0; i--) {
							LifecycleListener l = lifecycleListeners.get(i);
							l.pause();
							l.dispose();
						}
						lifecycleListeners.clear();
					}
					closedWindow.dispose();

					windows.removeValue(closedWindow, false);
				}
			}
		}
	}

	protected void cleanupWindows() {
		synchronized (lifecycleListeners) {
			for (LifecycleListener lifecycleListener : lifecycleListeners) {
				lifecycleListener.pause();
				lifecycleListener.dispose();
			}
		}
		for (CustomLwjgl3Window window : windows) {
			window.dispose();
		}
		windows.clear();
	}

	protected void cleanup() {
		Lwjgl3Cursor.disposeSystemCursors();
		audio.dispose();
		errorCallback.free();
		errorCallback = null;
		if (glDebugCallback != null) {
			glDebugCallback.free();
			glDebugCallback = null;
		}
		GLFW.glfwTerminate();
	}

	@Override
	public ApplicationListener getApplicationListener() {
		return currentWindow.getListener();
	}

	@Override
	public Graphics getGraphics() {
		return currentWindow.getGraphics();
	}

	@Override
	public Audio getAudio() {
		return audio;
	}

	@Override
	public Input getInput() {
		return currentWindow.getInput();
	}

	@Override
	public Files getFiles() {
		return files;
	}

	@Override
	public Net getNet() {
		return null;
	}

	@Override
	public void debug(String tag, String message) {
		if (logLevel >= LOG_DEBUG) getApplicationLogger().debug(tag, message);
	}

	@Override
	public void debug(String tag, String message, Throwable exception) {
		if (logLevel >= LOG_DEBUG) getApplicationLogger().debug(tag, message, exception);
	}

	@Override
	public void log(String tag, String message) {
		if (logLevel >= LOG_INFO) getApplicationLogger().log(tag, message);
	}

	@Override
	public void log(String tag, String message, Throwable exception) {
		if (logLevel >= LOG_INFO) getApplicationLogger().log(tag, message, exception);
	}

	@Override
	public void error(String tag, String message) {
		if (logLevel >= LOG_ERROR) getApplicationLogger().error(tag, message);
	}

	@Override
	public void error(String tag, String message, Throwable exception) {
		if (logLevel >= LOG_ERROR) getApplicationLogger().error(tag, message, exception);
	}

	@Override
	public void setLogLevel(int logLevel) {
		this.logLevel = logLevel;
	}

	@Override
	public int getLogLevel() {
		return logLevel;
	}

	@Override
	public void setApplicationLogger(ApplicationLogger applicationLogger) {
		this.applicationLogger = applicationLogger;
	}

	@Override
	public ApplicationLogger getApplicationLogger() {
		return applicationLogger;
	}

	@Override
	public ApplicationType getType() {
		return ApplicationType.Desktop;
	}

	@Override
	public int getVersion() {
		return 0;
	}

	@Override
	public long getJavaHeap() {
		return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
	}

	@Override
	public long getNativeHeap() {
		return getJavaHeap();
	}

	@Override
	public Preferences getPreferences(String name) {
		if (preferences.containsKey(name)) {
			return preferences.get(name);
		} else {
			Preferences prefs = new Lwjgl3Preferences(new Lwjgl3FileHandle(new File(config.preferencesDirectory, name), config.preferencesFileType));
			preferences.put(name, prefs);
			return prefs;
		}
	}

	@Override
	public Clipboard getClipboard() {
		return clipboard;
	}

	@Override
	public void postRunnable(Runnable runnable) {
		synchronized (runnables) {
			runnables.add(runnable);
		}
	}

	@Override
	public void exit() {
		running = false;
	}

	@Override
	public void addLifecycleListener(LifecycleListener listener) {
		synchronized (lifecycleListeners) {
			lifecycleListeners.add(listener);
		}
	}

	@Override
	public void removeLifecycleListener(LifecycleListener listener) {
		synchronized (lifecycleListeners) {
			lifecycleListeners.removeValue(listener, true);
		}
	}
	
	@Override
	public Lwjgl3Audio createAudio (Lwjgl3ApplicationConfiguration config) {
		return new OpenALLwjgl3Audio(config.audioDeviceSimultaneousSources,
			config.audioDeviceBufferCount, config.audioDeviceBufferSize);
	}

	@Override
	public Lwjgl3Input createInput (Lwjgl3Window window) {
		return new DefaultLwjgl3Input(window);
	}

	/**
	 * Creates a new {@link CustomLwjgl3Window} using the provided listener and {@link Lwjgl3WindowConfiguration}. This function only just
	 * instantiates a {@link CustomLwjgl3Window} and returns immediately. The actual window creation is postponed with
	 * {@link Application#postRunnable(Runnable)} until after all existing windows are updated.
	 */
	public CustomLwjgl3Window newWindow(ApplicationListener listener, Lwjgl3WindowConfiguration config) {
		Lwjgl3ApplicationConfiguration appConfig = Lwjgl3ApplicationConfiguration.copy(this.config);
		appConfig.setWindowConfiguration(config);
		return createWindow(appConfig, listener, windows.get(0).getWindowHandle());
	}

	private CustomLwjgl3Window createWindow(Lwjgl3ApplicationConfiguration config, ApplicationListener listener, long sharedContext) {
		CustomLwjgl3Window window = new CustomLwjgl3Window(listener, config, this);
		if (sharedContext == 0) {
			// the main window is created immediately
			createWindow(window, config, sharedContext);
		} else {
			// creation of additional windows is deferred to avoid GL context trouble
			postRunnable(() -> {
				createWindow(window, config, sharedContext);
				windows.add(window);
			});
		}
		return window;
	}

	private void createWindow(CustomLwjgl3Window window, Lwjgl3ApplicationConfiguration config, long sharedContext) {
		long windowHandle = createGlfwWindow(config, sharedContext);
		window.create(windowHandle);
		window.setVisible(config.initialVisible);

		for (int i = 0; i < 2; i++) {
			GL11.glClearColor(config.initialBackgroundColor.r, config.initialBackgroundColor.g, config.initialBackgroundColor.b, config.initialBackgroundColor.a);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
			GLFW.glfwSwapBuffers(windowHandle);
		}
	}

	static long createGlfwWindow(Lwjgl3ApplicationConfiguration config, long sharedContextWindow) {
		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, config.windowResizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, config.windowMaximized ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_AUTO_ICONIFY, config.autoIconify ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);

		if (sharedContextWindow == 0) {
			GLFW.glfwWindowHint(GLFW.GLFW_RED_BITS, config.r);
			GLFW.glfwWindowHint(GLFW.GLFW_GREEN_BITS, config.g);
			GLFW.glfwWindowHint(GLFW.GLFW_BLUE_BITS, config.b);
			GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, config.a);
			GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, config.stencil);
			GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, config.depth);
			GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, config.samples);
		}

		if (config.useGL30) {
			GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, config.gles30ContextMajorVersion);
			GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, config.gles30ContextMinorVersion);
			if (SharedLibraryLoader.isMac) {
				// hints mandatory on OS X for GL 3.2+ context creation, but fail on Windows if the
				// WGL_ARB_create_context extension is not available
				// see: http://www.glfw.org/docs/latest/compat.html
				GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
				GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
			}
		}
		
		if (config.transparentFramebuffer) {
			GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, GLFW.GLFW_TRUE);
		}

		if (config.debug) {
			GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE);
		}

		long windowHandle = 0;

		if (config.fullscreenMode != null) {
			GLFW.glfwWindowHint(GLFW.GLFW_REFRESH_RATE, config.fullscreenMode.refreshRate);
			windowHandle = GLFW.glfwCreateWindow(config.fullscreenMode.width, config.fullscreenMode.height, config.title, config.fullscreenMode.getMonitor(), sharedContextWindow);
		} else {
			GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, config.windowDecorated ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
			windowHandle = GLFW.glfwCreateWindow(config.windowWidth, config.windowHeight, config.title, 0, sharedContextWindow);
		}
		if (windowHandle == 0) {
			throw new GdxRuntimeException("Couldn't create window");
		}
		CustomLwjgl3Window.setSizeLimits(windowHandle, config.windowMinWidth, config.windowMinHeight, config.windowMaxWidth, config.windowMaxHeight);
		if (config.fullscreenMode == null) {
			if (config.windowX == -1 && config.windowY == -1) {
				int windowWidth = Math.max(config.windowWidth, config.windowMinWidth);
				int windowHeight = Math.max(config.windowHeight, config.windowMinHeight);
				if (config.windowMaxWidth > -1) windowWidth = Math.min(windowWidth, config.windowMaxWidth);
				if (config.windowMaxHeight > -1) windowHeight = Math.min(windowHeight, config.windowMaxHeight);
				
				long monitorHandle = GLFW.glfwGetPrimaryMonitor();
				if (config.windowMaximized && config.maximizedMonitor != null) {
					monitorHandle = config.maximizedMonitor.monitorHandle;
				}

				IntBuffer areaXPos = BufferUtils.createIntBuffer(1);
				IntBuffer areaYPos = BufferUtils.createIntBuffer(1);
				IntBuffer areaWidth = BufferUtils.createIntBuffer(1);
				IntBuffer areaHeight = BufferUtils.createIntBuffer(1);
				GLFW.glfwGetMonitorWorkarea(monitorHandle, areaXPos, areaYPos, areaWidth, areaHeight);

				GLFW.glfwSetWindowPos(windowHandle,
				         areaXPos.get(0) + areaWidth.get(0) / 2 - windowWidth / 2,
				         areaYPos.get(0) + areaHeight.get(0) / 2 - windowHeight / 2);
				
			} else {
				GLFW.glfwSetWindowPos(windowHandle, config.windowX, config.windowY);
			}
			
			if (config.windowMaximized) {
				GLFW.glfwMaximizeWindow(windowHandle);
			}
		}
		if (config.windowIconPaths != null) {
			CustomLwjgl3Window.setIcon(windowHandle, config.windowIconPaths, config.windowIconFileType);
		}
		GLFW.glfwMakeContextCurrent(windowHandle);
		GLFW.glfwSwapInterval(config.vSyncEnabled ? 1 : 0);
		GL.createCapabilities();

		initiateGL();
		if (!glVersion.isVersionEqualToOrHigher(2, 0))
			throw new GdxRuntimeException("OpenGL 2.0 or higher with the FBO extension is required. OpenGL version: " + GL11.glGetString(GL11.GL_VERSION) + "\n" + glVersion.getDebugVersionString());

		if (!supportsFBO()) {
			throw new GdxRuntimeException(
					"OpenGL 2.0 or higher with the FBO extension is required. OpenGL version: " + GL11.glGetString(GL11.GL_VERSION) + ", FBO extension: false\n" + glVersion.getDebugVersionString());
		}

		if (config.debug) {
			glDebugCallback = GLUtil.setupDebugMessageCallback(config.debugStream);
			setGLDebugMessageControl(GLDebugMessageSeverity.NOTIFICATION, false);
		}
		
		return windowHandle;
	}

	private static void initiateGL() {
		String versionString = GL11.glGetString(GL11.GL_VERSION);
		String vendorString = GL11.glGetString(GL11.GL_VENDOR);
		String rendererString = GL11.glGetString(GL11.GL_RENDERER);
		glVersion = new GLVersion(Application.ApplicationType.Desktop, versionString, vendorString, rendererString);
	}

	private static boolean supportsFBO() {
		// FBO is in core since OpenGL 3.0, see https://www.opengl.org/wiki/Framebuffer_Object
		return glVersion.isVersionEqualToOrHigher(3, 0) || GLFW.glfwExtensionSupported("GL_EXT_framebuffer_object") || GLFW.glfwExtensionSupported("GL_ARB_framebuffer_object");
	}

	public enum GLDebugMessageSeverity {
		HIGH(
				GL43.GL_DEBUG_SEVERITY_HIGH,
				KHRDebug.GL_DEBUG_SEVERITY_HIGH,
				ARBDebugOutput.GL_DEBUG_SEVERITY_HIGH_ARB,
				AMDDebugOutput.GL_DEBUG_SEVERITY_HIGH_AMD),
		MEDIUM(
				GL43.GL_DEBUG_SEVERITY_MEDIUM,
				KHRDebug.GL_DEBUG_SEVERITY_MEDIUM,
				ARBDebugOutput.GL_DEBUG_SEVERITY_MEDIUM_ARB,
				AMDDebugOutput.GL_DEBUG_SEVERITY_MEDIUM_AMD),
		LOW(
				GL43.GL_DEBUG_SEVERITY_LOW,
				KHRDebug.GL_DEBUG_SEVERITY_LOW,
				ARBDebugOutput.GL_DEBUG_SEVERITY_LOW_ARB,
				AMDDebugOutput.GL_DEBUG_SEVERITY_LOW_AMD),
		NOTIFICATION(
				GL43.GL_DEBUG_SEVERITY_NOTIFICATION,
				KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION,
				-1,
				-1);

		final int gl43, khr, arb, amd;

		GLDebugMessageSeverity(int gl43, int khr, int arb, int amd) {
			this.gl43 = gl43;
			this.khr = khr;
			this.arb = arb;
			this.amd = amd;
		}
	}

	/**
	 * Enables or disables GL debug messages for the specified severity level. Returns false if the severity level could not be set (e.g. the
	 * NOTIFICATION level is not supported by the ARB and AMD extensions). See
	 * {@link Lwjgl3ApplicationConfiguration#enableGLDebugOutput(boolean, PrintStream)}
	 */
	public static boolean setGLDebugMessageControl(GLDebugMessageSeverity severity, boolean enabled) {
		GLCapabilities caps = GL.getCapabilities();
		final int GL_DONT_CARE = 0x1100; // not defined anywhere yet

		if (caps.OpenGL43) {
			GL43.glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, severity.gl43, (IntBuffer) null, enabled);
			return true;
		}

		if (caps.GL_KHR_debug) {
			KHRDebug.glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, severity.khr, (IntBuffer) null, enabled);
			return true;
		}

		if (caps.GL_ARB_debug_output && severity.arb != -1) {
			ARBDebugOutput.glDebugMessageControlARB(GL_DONT_CARE, GL_DONT_CARE, severity.arb, (IntBuffer) null, enabled);
			return true;
		}

		if (caps.GL_AMD_debug_output && severity.amd != -1) {
			AMDDebugOutput.glDebugMessageEnableAMD(GL_DONT_CARE, severity.amd, (IntBuffer) null, enabled);
			return true;
		}

		return false;
	}

	public Lwjgl3ApplicationConfiguration getConfig() {
		return config;
	}

}
