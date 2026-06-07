package main.rendering.pipeline;

public class PipelineRenderCommand implements RenderCommand {
    private final ShaderPipeline pipeline;
    private final Renderable renderable;
    private final int priority;

    public PipelineRenderCommand(ShaderPipeline pipeline, Renderable renderable, int priority) {
        this.pipeline = pipeline;
        this.renderable = renderable;
        this.priority = priority;
    }

    @Override
    public void execute(RenderContext context) {
        // Die Pipeline führt autark ihre internen Schritte aus
        pipeline.run(context, renderable);
    }

    @Override
    public int getLayerPriority() {
        return priority;
    }
}
