package main.rendering.pipeline;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

import java.util.ArrayList;
import java.util.List;

public class ShaderPipelineBuilder {
    private final ShaderPipelineBuilderInternal computePass = new ShaderPipelineBuilderInternal();
    private final ShaderPipelineBuilderInternal mainPass = new ShaderPipelineBuilderInternal();
    private final List<PostProcessingStep> postSteps = new ArrayList<>();

    private UniformBinder computeUniformBinder = null;
    private UniformBinder mainUniformBinder = null;

    private final int width, height;
    private boolean shouldClear = true;

    public ShaderPipelineBuilder(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public ShaderPipelineBuilder compute(String path) { computePass.add(GL43.GL_COMPUTE_SHADER, path); return this; }
    public ShaderPipelineBuilder computeUniforms(UniformBinder b) { this.computeUniformBinder = b; return this; }

    public ShaderPipelineBuilder mainVertex(String path) { mainPass.add(GL20.GL_VERTEX_SHADER, path); return this; }
    public ShaderPipelineBuilder mainTessControl(String path) { mainPass.add(GL40.GL_TESS_CONTROL_SHADER, path); return this; }
    public ShaderPipelineBuilder mainTessEvaluation(String path) { mainPass.add(GL40.GL_TESS_EVALUATION_SHADER, path); return this; }
    public ShaderPipelineBuilder mainGeometry(String path) { mainPass.add(GL32.GL_GEOMETRY_SHADER, path); return this; }
    public ShaderPipelineBuilder mainFragment(String path) { mainPass.add(GL20.GL_FRAGMENT_SHADER, path); return this; }
    public ShaderPipelineBuilder mainUniforms(UniformBinder b) { this.mainUniformBinder = b; return this; }

    public ShaderPipelineBuilder clearScreen(boolean value) { this.shouldClear = value; return this; }

    public ShaderPipelineBuilder addPostEffect(String fragmentPath, UniformBinder b) {
        List<Shader> shaders = new ArrayList<>();
        shaders.add(new Shader(GL20.GL_VERTEX_SHADER, ShaderConstants.DEFAULT_POST_VERTEX_SHADER, true));
        shaders.add(new Shader(GL20.GL_FRAGMENT_SHADER, fragmentPath));
        postSteps.add(new PostProcessingStep(shaders, b));
        return this;
    }

    public ShaderPipeline build() {
        ShaderProgram compProg = computePass.hasShaders() ? computePass.build() : null;
        if (compProg != null) compProg.addUniformBinder(computeUniformBinder);

        ShaderProgram mainProg = mainPass.hasShaders() ? mainPass.build() : null;
        if (mainProg != null) mainProg.addUniformBinder(mainUniformBinder);

        return new ShaderPipeline(compProg, mainProg, postSteps, width, height, shouldClear);
    }

    private static class ShaderPipelineBuilderInternal {
        private final List<Shader> shaders = new ArrayList<>();
        public void add(int type, String path) { shaders.add(new Shader(type, path)); }
        public boolean hasShaders() { return !shaders.isEmpty(); }
        public ShaderProgram build() {
            ShaderProgram prog = new ShaderProgram(shaders);
            for (Shader s : shaders) s.cleanup();
            return prog;
        }
    }
}
