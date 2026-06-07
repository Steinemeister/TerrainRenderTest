package main.rendering.pipeline;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Example {
    public static void main(String[] args) {
        // 1. Minimalen OpenGL-Kontext über GLFW für den Test erstellen (Headless/Hidden Window)
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("GLFW konnte nicht initialisiert werden");
        }
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);

        long window = GLFW.glfwCreateWindow(100, 100, "Test", 0, 0);
        if (window == 0) {
            throw new RuntimeException("Test-Fenster konnte nicht erstellt werden");
        }
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();

        System.out.println("OpenGL Kontext erfolgreich erstellt: " + org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));

        // 2. Dummy Shader-Dateien im Classpath-Verzeichnis simulieren
        setupDummyShaderFiles();

        try {
            // 3. Render-Infrastruktur aufbauen
            RenderManager renderManager = new RenderManager();
            RenderContext context = new RenderContext();

            // 4. Pipeline über den Builder definieren
            System.out.println("Baue Shader-Pipeline...");
            ShaderPipeline worldPipeline = new ShaderPipelineBuilder(800, 600)
                    // Compute-Stufe
                    .compute("shaders/culling.comp")
                    .computeUniforms((ctx, id) -> ShaderProgram.setUniform(id, "viewProjection", ctx.getProjectionMatrix()))

                    // Geometrie-Stufe
                    .mainVertex("shaders/scene.vert")
                    .mainFragment("shaders/scene.frag")
                    .mainUniforms((ctx, id) -> {
                        ShaderProgram.setUniform(id, "projection", ctx.getProjectionMatrix());
                        ShaderProgram.setUniform(id, "view", ctx.getViewMatrix());
                    })

                    // Post-Processing-Stufe
                    .addPostEffect("shaders/blur.frag", (ctx, id) -> ShaderProgram.setUniform(id, "u_blurRadius", 2.0f))
                    .addPostEffect("shaders/fxaa.frag", (ctx, id) -> ShaderProgram.setUniform(id, "u_screenSize", new Vector2f(800, 600)))

                    .clearScreen(true)
                    .build();

            System.out.println("Pipeline erfolgreich kompilierte und gelinkt!");

            // 5. Test-Geometriedaten (VAO=1, Indices=36, Instanzen=1, Objekte im Buffer=100)
            TestMesh testMesh = new TestMesh(1, 36, 1, 100);

            // 6. Context mit Test-Werten und Schein-Buffern (IDs ungleich 0 aktivieren MDI)
            context.setProjectionMatrix(new Matrix4f().identity());
            context.setViewMatrix(new Matrix4f().identity());
            context.setObjectDataBuffer(999);     // Fake SSBO ID für den Test
            context.setIndirectCommandBuffer(888); // Fake Indirect Buffer ID für den Test

            // 7. Render-Command einreichen und ausführen
            System.out.println("Führe Test-Render-Durchlauf aus...");
            renderManager.submit(new PipelineRenderCommand(worldPipeline, testMesh, 0));

            // Führt intern Compute -> Geometrie -> Post-Processing aus
            renderManager.render(context);
            System.out.println("Render-Durchlauf fehlerfrei abgeschlossen!");

            // 8. Aufräumen
            worldPipeline.cleanup();
            System.out.println("Pipeline-Ressourcen erfolgreich freigegeben.");

        } catch (Exception e) {
            System.err.println("TEST FEHLGESCHLAGEN:");
            e.printStackTrace();
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void setupDummyShaderFiles() {
        // Findet das Target/Build-Verzeichnis der IDE heraus, um Shader direkt in den Classpath zu schreiben
        String baseDir = System.getProperty("user.dir");
        Path shadersPath = Paths.get(baseDir, "target", "classes", "shaders");

        // Fallback für Gradle-Projekte
        if (!Files.exists(shadersPath)) {
            shadersPath = Paths.get(baseDir, "build", "resources", "main", "shaders");
        }

        // Lokaler Fallback (erstellt einen Ordner direkt im Projekt-Stammverzeichnis, falls Classpath-Pfade variieren)
        if (!Files.exists(shadersPath.getParent())) {
            shadersPath = Paths.get(baseDir, "shaders");
        }

        try {
            Files.createDirectories(shadersPath);

            // Compute Shader schreiben
            Files.writeString(shadersPath.resolve("culling.comp"), """
                #version 430 core
                layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;
                struct DrawCommand { uint count; uint instanceCount; uint firstIndex; uint baseVertex; uint baseInstance; };
                layout(std430, binding = 1) writeonly buffer IndirectBuffer { DrawCommand commands[]; };
                uniform mat4 viewProjection;
                void main() {
                    uint gId = gl_GlobalInvocationID.x;
                    commands[gId].count = 36;
                    commands[gId].instanceCount = 1; // Sichtbar
                    commands[gId].firstIndex = 0;
                    commands[gId].baseVertex = 0;
                    commands[gId].baseInstance = gId;
                }
                """);

            // Vertex Shader schreiben
            Files.writeString(shadersPath.resolve("scene.vert"), """
                #version 430 core
                layout(location = 0) in vec3 inPosition;
                uniform mat4 projection;
                uniform mat4 view;
                void main() {
                    gl_Position = projection * view * vec4(inPosition, 1.0);
                }
                """);

            // Fragment Shader schreiben
            Files.writeString(shadersPath.resolve("scene.frag"), """
                #version 430 core
                out vec4 FragColor;
                void main() {
                    FragColor = vec4(1.0, 0.5, 0.0, 1.0);
                }
                """);

            // Post-Blur Shader schreiben
            Files.writeString(shadersPath.resolve("blur.frag"), """
                #version 430 core
                out vec4 FragColor;
                in vec2 TexCoords;
                layout(binding = 0) uniform sampler2D u_texture;
                uniform float u_blurRadius;
                void main() {
                    FragColor = texture(u_texture, TexCoords) * u_blurRadius * 0.5;
                }
                """);

            // Post-FXAA Shader schreiben
            Files.writeString(shadersPath.resolve("fxaa.frag"), """
                #version 430 core
                out vec4 FragColor;
                in vec2 TexCoords;
                layout(binding = 0) uniform sampler2D u_texture;
                uniform vec2 u_screenSize;
                void main() {
                    FragColor = texture(u_texture, TexCoords);
                }
                """);

            System.out.println("Temporäre Shader-Dateien erzeugt in: " + shadersPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Konnte Shader-Dateien für den Test nicht erzeugen", e);
        }
    }

    // Hilfs-Mesh Implementierung für den Test
    private static class TestMesh implements Renderable {
        private final int vao, count, instances, indirectCount;
        public TestMesh(int vao, int count, int instances, int indirectCount) {
            this.vao = vao; this.count = count; this.instances = instances; this.indirectCount = indirectCount;
        }
        @Override public int getVaoId() { return vao; }
        @Override public int getElementCount() { return count; }
        @Override public int getInstanceCount() { return instances; }
        @Override public int getIndirectDrawCount() { return indirectCount; }
    }
}
