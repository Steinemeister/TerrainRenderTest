package main.rendering.pipeline;


public interface RenderCommand {
    void execute(RenderContext context);
    int getLayerPriority();
}
