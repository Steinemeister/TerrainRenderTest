package main.rendering.pipeline;

import org.joml.Matrix4f;

public class RenderContext {
    private Matrix4f projectionMatrix = new Matrix4f();
    private Matrix4f viewMatrix = new Matrix4f();
    private float deltaTime;
    private float totalTime;

    // OpenGL Buffer-IDs für automatisiertes Compute / Indirect Rendering
    private int objectDataBuffer = 0;
    private int indirectCommandBuffer = 0;

    // Getters & Setters
    public Matrix4f getProjectionMatrix() { return projectionMatrix; }
    public void setProjectionMatrix(Matrix4f m) { this.projectionMatrix.set(m); }
    public Matrix4f getViewMatrix() { return viewMatrix; }
    public void setViewMatrix(Matrix4f m) { this.viewMatrix.set(m); }
    public float getDeltaTime() { return deltaTime; }
    public void setDeltaTime(float dt) { this.deltaTime = dt; }
    public float getTotalTime() { return totalTime; }
    public void setTotalTime(float tt) { this.totalTime = tt; }
    public int getObjectDataBuffer() { return objectDataBuffer; }
    public void setObjectDataBuffer(int id) { this.objectDataBuffer = id; }
    public int getIndirectCommandBuffer() { return indirectCommandBuffer; }
    public void setIndirectCommandBuffer(int id) { this.indirectCommandBuffer = id; }
}
