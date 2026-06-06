package main.rendering;

import main.plants.FoliageManager;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public class Window {
    private final int width = 1280;
    private final int height = 720;
    private final String title = "Horizon inspired Foliage Engine - LWJGL 3";

    private long windowHandle;

    private Camera camera = new Camera();
    private FoliageManager foliageManager;

    private double lastFrameTime = 0.0;
    private float deltaTime = 0.0f;

    // Variablen für die Maussteuerung
    private double lastX = width / 2.0;
    private double lastY = height / 2.0;
    private boolean firstMouse = true;
    private final float mouseSensitivity = 0.1f;

    public void run() {
        init();
        loop();

        foliageManager.cleanup();
        GLFW.glfwDestroyWindow(windowHandle);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
    }

    private void init() {
        // 1. Fehler-Callback für GLFW einrichten
        GLFWErrorCallback.createPrint(System.err).set();

        // 2. GLFW initialisieren
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("GLFW konnte nicht initialisiert werden!");
        }

        // 3. Fenster-Konfiguration (OpenGL 4.3 Core Profile für Compute Shader zwingend erforderlich)
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GL11.GL_TRUE);

        // 4. Fenster erstellen
        windowHandle = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (windowHandle == MemoryUtil.NULL) {
            throw new RuntimeException("GLFW-Fenster konnte nicht erstellt werden!");
        }

        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);

        // --- KEY CALLBACK (Einmalige Events wie ESC) ---
        GLFW.glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE) {
                GLFW.glfwSetWindowShouldClose(window, true);
            }
        });

        // --- MAUS CALLBACK (Umschauen) ---
        GLFW.glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            if (firstMouse) {
                lastX = xpos;
                lastY = ypos;
                firstMouse = false;
            }

            // Differenz zur letzten Mausposition berechnen
            double xOffset = xpos - lastX;
            double yOffset = lastY - ypos; // Invertiert, da Y von oben nach unten läuft

            lastX = xpos;
            lastY = ypos;

            // Kamera-Winkel updaten
            camera.yaw += (float) (xOffset * mouseSensitivity);
            camera.pitch += (float) (yOffset * mouseSensitivity);

            // Verhindern, dass die Kamera sich überschlägt (Überschlag-Schutz)
            if (camera.pitch > 89.0f)  camera.pitch = 89.0f;
            if (camera.pitch < -89.0f) camera.pitch = -89.0f;
        });

        // 6. Den OpenGL-Kontext aktuell setzen
        GLFW.glfwMakeContextCurrent(windowHandle);

        // V-Sync aktivieren (1) oder deaktivieren (0) für unbegrenzte FPS
        GLFW.glfwSwapInterval(0);

        // Fenster anzeigen
        GLFW.glfwShowWindow(windowHandle);

        // !! CRITICAL !! Bindet die OpenGL-Funktionen für LWJGL an den aktuellen Kontext
        GL.createCapabilities();

        // Basis OpenGL Einstellungen
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        // Hintergrundfarbe definieren (Horizon-artiges Hellblau)
        GL11.glClearColor(0.4f, 0.6f, 0.9f, 1.0f);

        System.out.println("OpenGL Version: " + GL11.glGetString(GL11.GL_VERSION));

        foliageManager = new FoliageManager();
        foliageManager.init();

        lastFrameTime = GLFW.glfwGetTime();
    }

    private void loop() {
        while (!GLFW.glfwWindowShouldClose(windowHandle)) {
            // 1. Delta Time berechnen
            double currentFrameTime = GLFW.glfwGetTime();
            deltaTime = (float) (currentFrameTime - lastFrameTime);
            lastFrameTime = currentFrameTime;

            // 2. Tastatur abfragen
            handleKeyboardInput();

            // 3. Szene rendern
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            // Kamera Matrizen und Frustum neu berechnen
            camera.update(width, height);

            // Grashalme berechnen (Culling) und zeichnen
            foliageManager.render(camera, width, height);

            GLFW.glfwSwapBuffers(windowHandle);
            GLFW.glfwPollEvents();
        }
    }

    private void handleKeyboardInput() {
        float cameraSpeed = 20.0f * deltaTime; // 20 Meter pro Sekunde Fluggeschwindigkeit

        // 1. Die echte Blickrichtung im 3D-Raum berechnen
        Vector3f front3D = new Vector3f();
        front3D.x = (float) (Math.cos(Math.toRadians(camera.yaw)) * Math.cos(Math.toRadians(camera.pitch)));
        front3D.y = (float) Math.sin(Math.toRadians(camera.pitch));
        front3D.z = (float) (Math.sin(Math.toRadians(camera.yaw)) * Math.cos(Math.toRadians(camera.pitch)));
        front3D.normalize();

        // 2. Den Y-Anteil komplett auf Null setzen (Bewegung rein auf die XZ-Boden-Ebene zwingen)
        Vector3f frontHorizontal = new Vector3f(front3D.x, 0.0f, front3D.z);

        // WICHTIG: Neu normalisieren, da der Vektor sonst kürzer wird, je steiler man nach oben/unten schaut
        if (frontHorizontal.lengthSquared() > 0.0f) {
            frontHorizontal.normalize();
        }

        // 3. Seitwärts-Vektor berechnen (basiert nun auch strikt auf dem horizontalen Vektor)
        Vector3f rightHorizontal = new Vector3f(frontHorizontal).cross(new Vector3f(0, 1, 0)).normalize();

        // 4. Tasten abfragen (WASD bewegt sich jetzt rein horizontal)
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
            camera.position.add(new Vector3f(frontHorizontal).mul(cameraSpeed));
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
            camera.position.sub(new Vector3f(frontHorizontal).mul(cameraSpeed));
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
            camera.position.sub(new Vector3f(rightHorizontal).mul(cameraSpeed));
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
            camera.position.add(new Vector3f(rightHorizontal).mul(cameraSpeed));
        }

        // Höhenänderung bleibt exakt und isoliert auf Space und Shift
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) {
            camera.position.y += cameraSpeed;
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
            camera.position.y -= cameraSpeed;
        }
    }
}
