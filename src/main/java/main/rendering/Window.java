package main.rendering;

import main.loading.TextureLoader;
import main.plants.FoliageManager;
import main.rendering.pipeline.*;
import org.joml.Matrix4f;
import org.joml.Vector2f;
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

    // --- NEU: Pipeline Infrastruktur ---
    private RenderManager renderManager;
    private RenderContext renderContext;
    private ShaderPipeline foliagePipeline;

    private double lastFrameTime = 0.0;
    private float deltaTime = 0.0f;

    // Variablen für die Maussteuerung
    private double lastX = width / 2.0;
    private double lastY = height / 2.0;
    private boolean firstMouse = true;
    private final float mouseSensitivity = 0.1f;

    private int heightmapTextureId;

    public void run() {
        init();
        loop();

        // Ressourcen sauber freigeben
        foliagePipeline.cleanup();
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

        // 3. Fenster-Konfiguration (OpenGL 4.3 Core Profile zwingend für Compute Shader)
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GL11.GL_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, 4);

        // 4. Fenster erstellen
        windowHandle = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (windowHandle == MemoryUtil.NULL) {
            throw new RuntimeException("GLFW-Fenster konnte nicht erstellt werden!");
        }

        GLFW.glfwSetInputMode(windowHandle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);

        // --- KEY CALLBACK ---
        GLFW.glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE) {
                GLFW.glfwSetWindowShouldClose(window, true);
            }
        });

        // --- MAUS CALLBACK ---
        GLFW.glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            if (firstMouse) {
                lastX = xpos;
                lastY = ypos;
                firstMouse = false;
            }

            double xOffset = xpos - lastX;
            double yOffset = lastY - ypos; // Invertiert, da Y von oben nach unten läuft

            lastX = xpos;
            lastY = ypos;

            camera.yaw += (float) (xOffset * mouseSensitivity);
            camera.pitch += (float) (yOffset * mouseSensitivity);

            if (camera.pitch > 89.0f)  camera.pitch = 89.0f;
            if (camera.pitch < -89.0f) camera.pitch = -89.0f;
        });

        // 6. Den OpenGL-Kontext aktuell setzen
        GLFW.glfwMakeContextCurrent(windowHandle);
        GLFW.glfwSwapInterval(0); // Unbegrenzte FPS (V-Sync aus)
        GLFW.glfwShowWindow(windowHandle);

        // !! CRITICAL !! Bindet die OpenGL-Funktionen für LWJGL an den Kontext
        GL.createCapabilities();

        System.out.println("OpenGL Version: " + GL11.glGetString(GL11.GL_VERSION));

        heightmapTextureId = TextureLoader.loadTextureFromClasspath("textures/heightmap.png");

        // --- 7. INITIALISIERUNG DES FOLIAGE MANAGERS ---
        foliageManager = new FoliageManager();
        foliageManager.init();

        // --- 8. INITIALISIERUNG DER AUTOMATISIERTEN PIPELINE ---
        renderManager = new RenderManager();
        renderContext = new RenderContext();

        System.out.println("Kompiliere autonome Foliage-Pipeline...");
        foliagePipeline = new ShaderPipelineBuilder(width, height)
                // Compute-Shader Stufe für das GPU Frustum Culling
                .compute("shaders/foliage_culling.glsl")
                .computeUniforms((ctx, id) -> {
                    for (int i = 0; i < 6; i++) {
                        org.joml.Vector4f plane = camera.getFrustumPlanes()[i];
                        int loc = org.lwjgl.opengl.GL20.glGetUniformLocation(id, "frustumPlanes[" + i + "]");
                        if (loc != -1) {
                            org.lwjgl.opengl.GL20.glUniform4f(loc, plane.x, plane.y, plane.z, plane.w);
                        }
                    }
                    int locSize = org.lwjgl.opengl.GL20.glGetUniformLocation(id, "u_terrainSize");
                    if (locSize != -1) org.lwjgl.opengl.GL20.glUniform1f(locSize, 1000.0f);

                    int locHeight = org.lwjgl.opengl.GL20.glGetUniformLocation(id, "u_maxHeight");
                    if (locHeight != -1) org.lwjgl.opengl.GL20.glUniform1f(locHeight, 50.0f); // Hügel sind bis zu 50m hoch

                    // 3. Textur an Unit 3 aktivieren und binden
                    org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE3);
                    org.lwjgl.opengl.GL11.glBindTexture(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, heightmapTextureId);

                    int locCam = org.lwjgl.opengl.GL20.glGetUniformLocation(id, "u_cameraPosition");
                    if (locCam != -1) {
                        org.lwjgl.opengl.GL20.glUniform3f(locCam, ctx.getCameraPosition().x, ctx.getCameraPosition().y, ctx.getCameraPosition().z);
                    }

                    int locNear = org.lwjgl.opengl.GL20.glGetUniformLocation(id, "u_lodDistNear");
                    if (locNear != -1) org.lwjgl.opengl.GL20.glUniform1f(locNear, 200.0f); // 40 Meter voll dicht

                    int locMed = org.lwjgl.opengl.GL20.glGetUniformLocation(id, "u_lodDistMed");
                    if (locMed != -1) org.lwjgl.opengl.GL20.glUniform1f(locMed, 400.0f); // Ab 120 Meter sehr ausgedünnt

                    int locMaxDist = org.lwjgl.opengl.GL20.glGetUniformLocation(id, "u_maxVisibilityDist");
                    if (locMaxDist != -1) org.lwjgl.opengl.GL20.glUniform1f(locMaxDist, 250.0f); // Gras blendet bis 250 Meter komplett aus
                })
                // Haupt-Render Stufe für die Vegetation
                .mainVertex("shaders/grass_v.glsl")
                .mainFragment("shaders/grass_f.glsl")
                .mainUniforms((ctx, id) -> {
                    Matrix4f vp = new Matrix4f(ctx.getProjectionMatrix()).mul(ctx.getViewMatrix());
                    ShaderProgram.setUniform(id, "viewProjection", vp);
                    ShaderProgram.setUniform(id, "u_time", ctx.getTotalTime()); // Für Windbewegungen
                })
                // Post-Processing-Kette (Wird vollautomatisch via Ping-Pong FBOs verarbeitet)
                .addPostEffect("shaders/blur.frag", (ctx, id) -> ShaderProgram.setUniform(id, "u_blurRadius", 1.5f))
                .addPostEffect("shaders/fxaa.frag", (ctx, id) -> ShaderProgram.setUniform(id, "u_screenSize", new Vector2f(width, height)))
                .clearScreen(true)
                .build();

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

            // Kamera Matrizen neu berechnen
            camera.update(width, height);

            // 3. AUTOMATISIERTEN PIPELINE-CONTEXT SPEISEN
            renderContext.setProjectionMatrix(camera.getProjectionMatrix());
            renderContext.setViewMatrix(camera.getViewMatrix());
            renderContext.setDeltaTime(deltaTime);
            renderContext.setTotalTime((float) currentFrameTime);

            // Reiche alle 3 benötigten GPU-Puffer-IDs aus dem FoliageManager an den Pipeline-Context weiter
            renderContext.setObjectDataBuffer(foliageManager.getInputInstancesSSBO());
            renderContext.setVisibleInstancesBuffer(foliageManager.getVisibleInstancesSSBO());
            renderContext.setIndirectCommandBuffer(foliageManager.getIndirectCommandBufferId());

            renderContext.setCameraPosition(camera.position);

            // 4. RENDER-BEFEHL ANMELDEN (0 = Standard-Priorität für 3D Szene)
            renderManager.submit(new PipelineRenderCommand(foliagePipeline, foliageManager, 0));

            // 5. ZENTRALES EXECUTE: Führt Compute Culling, MDI & Post-Processing komplett autark aus
            renderManager.render(renderContext);

            GLFW.glfwSwapBuffers(windowHandle);
            GLFW.glfwPollEvents();
        }
        org.lwjgl.opengl.GL11.glDeleteTextures(heightmapTextureId);
    }

    private void handleKeyboardInput() {
        float cameraSpeed = 20.0f * deltaTime; // 20 m/s Fluggeschwindigkeit

        Vector3f front3D = new Vector3f();
        front3D.x = (float) (Math.cos(Math.toRadians(camera.yaw)) * Math.cos(Math.toRadians(camera.pitch)));
        front3D.y = (float) Math.sin(Math.toRadians(camera.pitch));
        front3D.z = (float) (Math.sin(Math.toRadians(camera.yaw)) * Math.cos(Math.toRadians(camera.pitch)));
        front3D.normalize();

        // Bewegung rein auf die XZ-Boden-Ebene zwingen
        Vector3f frontHorizontal = new Vector3f(front3D.x, 0.0f, front3D.z);
        if (frontHorizontal.lengthSquared() > 0.0f) {
            frontHorizontal.normalize();
        }

        Vector3f rightHorizontal = new Vector3f(frontHorizontal).cross(new Vector3f(0, 1, 0)).normalize();

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
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) {
            camera.position.y += cameraSpeed;
        }
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) {
            camera.position.y -= cameraSpeed;
        }
    }
}
