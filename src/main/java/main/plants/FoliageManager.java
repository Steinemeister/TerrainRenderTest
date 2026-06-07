package main.plants;

import main.rendering.Camera;
import main.rendering.ComputePipeline;
import main.rendering.Shader;
import main.rendering.pipeline.Renderable;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class FoliageManager implements Renderable {
    private final int totalPlants = 100_000;
    private final int bytesPerInstance = 32;

    // GPU Buffer IDs
    private int inputInstancesSSBO;
    private int visibleInstancesSSBO;
    private int indirectCommandBuffer;
    private int foliageVAO;

    public void init() {
        // 1. Zufällige Pflanzendaten generieren
        List<FoliageInstance> plants = new ArrayList<>();
        for (int i = 0; i < totalPlants; i++) {
            float x = (float) (Math.random() * 1000.0 - 500.0);
            float z = (float) (Math.random() * 1000.0 - 500.0);
            float y = 0.0f;
            float scale = (float) (0.5 + Math.random() * 1.0);
            float rotation = (float) (Math.random() * Math.PI * 2.0);
            float typeId = 0.0f;

            plants.add(new FoliageInstance(x, y, z, scale, rotation, typeId));
        }

        // 2. In FloatBuffer konvertieren
        FloatBuffer inputBufferData = BufferUtils.createFloatBuffer(totalPlants * 8);
        for (FoliageInstance p : plants) {
            inputBufferData.put(p.x).put(p.y).put(p.z).put(p.scale);
            inputBufferData.put(p.rotX).put(p.rotY).put(p.rotZ).put(p.typeId);
        }
        inputBufferData.flip();

        // 3. Input SSBO
        inputInstancesSSBO = GL15.glGenBuffers();
        GL30.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, inputInstancesSSBO);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, inputBufferData, GL15.GL_STATIC_DRAW);

        // 4. Output SSBO
        visibleInstancesSSBO = GL15.glGenBuffers();
        GL30.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, visibleInstancesSSBO);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) totalPlants * bytesPerInstance, GL15.GL_DYNAMIC_DRAW);

        // 5. Indirect Command Buffer
        indirectCommandBuffer = GL15.glGenBuffers();
        GL30.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, indirectCommandBuffer);

        IntBuffer commandData = BufferUtils.createIntBuffer(5);
        commandData.put(3); // 3 Vertices pro Grashalm (1 Triangle)
        commandData.put(0); // instanceCount (wird von der GPU hochgezählt)
        commandData.put(0);
        commandData.put(0);
        commandData.put(0);
        commandData.flip();
        GL15.glBufferData(GL43.GL_DRAW_INDIRECT_BUFFER, commandData, GL15.GL_DYNAMIC_DRAW);
        GL30.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, 0);

        // 6. Leeres Dummy-VAO für das Gras-Rendering generieren
        foliageVAO = GL30.glGenVertexArrays();
    }

    // --- Implementierung des Renderable Interfaces ---
    @Override public int getVaoId() { return foliageVAO; }
    @Override public int getElementCount() { return 3; } // Vertices pro Instanz
    @Override public int getInstanceCount() { return 0; } // 0 signalisiert der Pipeline: Nutze glDrawArraysIndirect
    @Override public int getIndirectDrawCount() { return totalPlants; }

    // --- Getter für die Puffer (Werden vom Window-Loop in den Context geladen) ---
    public int getInputInstancesSSBO() { return inputInstancesSSBO; }
    public int getVisibleInstancesSSBO() { return visibleInstancesSSBO; }
    public int getIndirectCommandBufferId() { return indirectCommandBuffer; }

    public void cleanup() {
        GL15.glDeleteBuffers(inputInstancesSSBO);
        GL15.glDeleteBuffers(visibleInstancesSSBO);
        GL15.glDeleteBuffers(indirectCommandBuffer);
        GL30.glDeleteVertexArrays(foliageVAO);
    }
}
