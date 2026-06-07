package main.rendering.pipeline;

@FunctionalInterface
public interface UniformBinder {
    void bind(RenderContext context, int programId);
}
