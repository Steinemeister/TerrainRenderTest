package main.rendering.pipeline;

public interface Renderable {
    int getVaoId();
    int getElementCount();
    int getInstanceCount();
    int getIndirectDrawCount();
}
