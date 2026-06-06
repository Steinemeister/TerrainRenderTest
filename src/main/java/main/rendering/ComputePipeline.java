package main.rendering;

import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.util.HashMap;
import java.util.Map;

public class ComputePipeline {
    private final Shader shader;
    private final Map<Integer, Integer> bufferBindings;
    private int workGroupsX = 1;
    private int workGroupsY = 1;
    private int workGroupsZ = 1;
    private int memoryBarrierBits = 0;

    private ComputePipeline(Shader shader) {
        this.shader = shader;
        this.bufferBindings = new HashMap<>();
    }

    public static Builder create(String shaderPath) {
        return new Builder(new Shader(shaderPath));
    }

    public void execute() {
        shader.bind();

        // Alle registrierten Buffer an ihre Bindepunkte knüpfen
        for (Map.Entry<Integer, Integer> entry : bufferBindings.entrySet()) {
            int bindingPoint = entry.getKey();
            int bufferId = entry.getValue();
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingPoint, bufferId);
        }

        // Berechnen auf der GPU starten
        GL43.glDispatchCompute(workGroupsX, workGroupsY, workGroupsZ);

        // Falls Barrieren definiert wurden, hier darauf warten
        if (memoryBarrierBits != 0) {
            GL42.glMemoryBarrier(memoryBarrierBits);
        }

        shader.unbind();
    }

    public int getShaderId() {
        return this.shader.getId();
    }

    public void use() {
        this.shader.bind();
    }

    // --- DER BUILDER ---
    public static class Builder {
        private final ComputePipeline pipeline;

        public Builder(Shader shader) {
            this.pipeline = new ComputePipeline(shader);
        }

        /** Knüpft ein SSBO an das im GLSL definierte 'binding = X' */
        public Builder bindBuffer(int bindingPoint, int bufferId) {
            pipeline.bufferBindings.put(bindingPoint, bufferId);
            return this;
        }

        /** Definiert die exakte Anzahl der globalen Arbeitsgruppen */
        public Builder dispatchSize(int x, int y, int z) {
            pipeline.workGroupsX = x;
            pipeline.workGroupsY = y;
            pipeline.workGroupsZ = z;
            return this;
        }

        /** Sagt der Pipeline, worauf nachfolgende Shader warten müssen */
        public Builder waitForBarrier(int openGLBarrierBit) {
            pipeline.memoryBarrierBits |= openGLBarrierBit;
            return this;
        }

        public ComputePipeline build() {
            return pipeline;
        }
    }
}
