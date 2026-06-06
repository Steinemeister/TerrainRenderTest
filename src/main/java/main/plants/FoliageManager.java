package main.plants;

import main.rendering.Camera;
import main.rendering.ComputePipeline;
import main.rendering.Shader;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class FoliageManager {
    private final int totalPlants = 100_000; // Starten wir zum Testen mit 100k Pflanzen
    private final int bytesPerInstance = 32;  // 8 Floats * 4 Bytes

    // GPU Buffer IDs
    private int inputInstancesSSBO;
    private int visibleInstancesSSBO;
    private int indirectCommandBuffer;

    private int foliageVAO;
    private Shader grassRenderShader;

    private ComputePipeline cullingPipeline;

    public void init() {
        // 1. Zufällige Pflanzendaten auf der CPU generieren (z. B. auf einem 1000x1000m Feld)
        List<FoliageInstance> plants = new ArrayList<>();
        for (int i = 0; i < totalPlants; i++) {
            float x = (float) (Math.random() * 1000.0 - 500.0);
            float z = (float) (Math.random() * 1000.0 - 500.0);
            float y = 0.0f; // Wird später durch die Heightmap bestimmt
            float scale = (float) (0.5 + Math.random() * 1.0);
            float rotation = (float) (Math.random() * Math.PI * 2.0);
            float typeId = 0.0f; // 0 = Gras

            plants.add(new FoliageInstance(x, y, z, scale, rotation, typeId));
        }

        // 2. Daten in einen nativen FloatBuffer konvertieren
        FloatBuffer inputBufferData = BufferUtils.createFloatBuffer(totalPlants * 8);
        for (FoliageInstance p : plants) {
            inputBufferData.put(p.x).put(p.y).put(p.z).put(p.scale);
            inputBufferData.put(p.rotX).put(p.rotY).put(p.rotZ).put(p.typeId);
        }
        inputBufferData.flip();

        // 3. Input SSBO erstellen und befüllen
        inputInstancesSSBO = GL15.glGenBuffers();
        GL30.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, inputInstancesSSBO);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, inputBufferData, GL15.GL_STATIC_DRAW);

        // 4. Output SSBO erstellen (Zunächst leer, bietet Platz für alle Pflanzen)
        visibleInstancesSSBO = GL15.glGenBuffers();
        GL30.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, visibleInstancesSSBO);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) totalPlants * bytesPerInstance, GL15.GL_DYNAMIC_DRAW);

        // 5. Indirect Command Buffer erstellen
        // Struktur: count, instanceCount, firstIndex, baseVertex, baseInstance (5 Ints)
        indirectCommandBuffer = GL15.glGenBuffers();
        GL30.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, indirectCommandBuffer);

        IntBuffer commandData = BufferUtils.createIntBuffer(5);
        commandData.put(3); // count: Unser Grashalm hat im Test 3 Vertices (1 Triangle)
        commandData.put(0); // instanceCount: Wird vom Compute Shader hochgezählt
        commandData.put(0); // firstIndex
        commandData.put(0); // baseVertex
        commandData.put(0); // baseInstance
        commandData.flip();
        GL15.glBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, commandData, GL15.GL_DYNAMIC_DRAW);

        // 6. Die Culling-Pipeline mit unserem Builder aufbauen!
        // Der Shader wird in Schritt 3 erstellt
        int numGroups = (int) Math.ceil((double) totalPlants / 256.0); // 256 Threads pro Gruppe

        cullingPipeline = ComputePipeline.create("/shaders/foliage_culling.glsl")
                .bindBuffer(0, inputInstancesSSBO)
                .bindBuffer(1, visibleInstancesSSBO)
                .bindBuffer(2, indirectCommandBuffer)
                .dispatchSize(numGroups, 1, 1)
                // Wichtig: Wir warten darauf, dass der Indirect-Befehl und das SSBO bereit zum Rendern sind
                .waitForBarrier(GL43.GL_COMMAND_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT)
                .build();

        foliageVAO = GL30.glGenVertexArrays();

        grassRenderShader = new Shader("/shaders/grass_v.glsl", "/shaders/grass_f.glsl");
    }

    public void render(Camera camera, int width, int height) {
        // --- SCHRITT 1: CULLING PASS ---
        // Uniforms an den Compute-Shader der Pipeline übergeben
        cullingPipeline.use();

        // Frustum Ebenen an den Shader senden
        for (int i = 0; i < 6; i++) {
            Vector4f plane = camera.getFrustumPlanes()[i];
            GL20.glUniform4f(GL20.glGetUniformLocation(cullingPipeline.getShaderId(), "frustumPlanes[" + i + "]"),
                    plane.x, plane.y, plane.z, plane.w);
        }

        // Culling ausführen (setzt Instanz-Counter zurück und startet Compute Shader)
        updateAndCull();

        // --- SCHRITT 2: RENDER PASS ---
        grassRenderShader.bind();

        // View-Projection Matrix an Grafik-Shader senden
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            camera.getViewProjection().get(fb);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(grassRenderShader.getId(), "viewProjection"), false, fb);
        }

        // Den sichtbaren Buffer auch an den Grafik-Shader binden (binding = 1)
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, visibleInstancesSSBO);

        // Zeichnen über den Indirect Buffer
        GL30.glBindVertexArray(foliageVAO);
        GL30.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, indirectCommandBuffer);

        // 3 Vertices pro Instanz zeichnen. Der Instance-Count wird magisch von der GPU bestimmt!
        GL43.glDrawArraysIndirect(GL11.GL_TRIANGLES, 0);

        GL30.glBindVertexArray(0);
        grassRenderShader.unbind();
    }

    public void updateAndCull() {
        // A. Vor dem Culling: Setzen Sie den 'instanceCount' im Command-Buffer zurück auf 0
        // 'instanceCount' ist das zweite Integer-Element im Buffer (Offset: 4 Bytes)
        GL30.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, indirectCommandBuffer);
        int zero = 0;
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 4, BufferUtils.createIntBuffer(1).put(zero).flip());

        // B. Compute Shader ausführen (Culling & Sortierung auf der GPU)
        cullingPipeline.execute();

        GL42.glMemoryBarrier(GL43.GL_COMMAND_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
    }

    public void cleanup() {
        GL15.glDeleteBuffers(inputInstancesSSBO);
        GL15.glDeleteBuffers(visibleInstancesSSBO);
        GL15.glDeleteBuffers(indirectCommandBuffer);
    }
}
