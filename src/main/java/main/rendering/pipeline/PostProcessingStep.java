package main.rendering.pipeline;

import java.util.List;

public class PostProcessingStep {
    private final ShaderProgram program;

    public PostProcessingStep(List<Shader> shaders, UniformBinder binder) {
        this.program = new ShaderProgram(shaders);
        this.program.addUniformBinder(binder);
    }

    public ShaderProgram getProgram() { return program; }
    public void cleanup() { program.cleanup(); }
}
