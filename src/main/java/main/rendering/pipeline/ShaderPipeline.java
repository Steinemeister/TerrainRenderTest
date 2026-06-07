package main.rendering.pipeline;

import org.lwjgl.opengl.*;

import java.util.List;

public class ShaderPipeline {
    private final ShaderProgram computeProgram;
    private final ShaderProgram mainProgram;
    private final List<PostProcessingStep> postSteps;

    private final int[] fbos = new int[2];
    private final int[] textures = new int[2];
    private int rboDepth;
    private int dummyVAO;

    private final int width, height;
    private final boolean shouldClear;

    protected ShaderPipeline(ShaderProgram computeProgram, ShaderProgram mainProgram,
                             List<PostProcessingStep> postSteps, int width, int height, boolean shouldClear) {
        this.computeProgram = computeProgram;
        this.mainProgram = mainProgram;
        this.postSteps = postSteps;
        this.width = width;
        this.height = height;
        this.shouldClear = shouldClear;
        this.dummyVAO = GL30.glGenVertexArrays();

        if (!postSteps.isEmpty()) {
            initFramebuffers();
        }
    }

    private void initFramebuffers() {
        for (int i = 0; i < 2; i++) {
            fbos[i] = GL30.glGenFramebuffers();
            textures[i] = GL11.glGenTextures();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbos[i]);

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[i]);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, width, height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textures[i], 0);
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbos[0]);
        rboDepth = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, rboDepth);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, rboDepth);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public void run(RenderContext context, Renderable scene) {
        // 1. AUTOMATISCHER COMPUTE PASS (Culling)
        boolean useIndirect = computeProgram != null
                              && context.getObjectDataBuffer() != 0
                              && context.getIndirectCommandBuffer() != 0;

        if (useIndirect) {
            computeProgram.bind(context);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, context.getObjectDataBuffer());
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, context.getIndirectCommandBuffer());

            int groupsX = (int) Math.ceil((double) scene.getIndirectDrawCount() / 64.0);
            GL43.glDispatchCompute(groupsX, 1, 1);
            GL42.glMemoryBarrier(GL43.GL_COMMAND_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            computeProgram.unbind();
        }

        // 2. AUTOMATISCHER MAIN GEOMETRIE PASS
        boolean hasPost = !postSteps.isEmpty();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, hasPost ? fbos[0] : 0);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        if (shouldClear) {
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } else {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT); // Schützt vorherige Layer (z.B. 3D-Szene vor der UI)
        }

        if (mainProgram != null) {
            mainProgram.bind(context);
            GL30.glBindVertexArray(scene.getVaoId());

            if (useIndirect) {
                GL30.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, context.getIndirectCommandBuffer());
                GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, 0, scene.getIndirectDrawCount(), 0);
                GL30.glBindBuffer(GL43.GL_DRAW_INDIRECT_BUFFER, 0);
            } else if (scene.getInstanceCount() > 1) {
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, scene.getElementCount(), GL11.GL_UNSIGNED_INT, 0, scene.getInstanceCount());
            } else {
                GL11.glDrawElements(GL11.GL_TRIANGLES, scene.getElementCount(), GL11.GL_UNSIGNED_INT, 0);
            }

            GL30.glBindVertexArray(0);
            mainProgram.unbind();
        }

        // 3. AUTOMATISCHER POST-PROCESSING PASS (Ping-Pong Kette)
        if (hasPost) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL30.glBindVertexArray(dummyVAO);

            int currentSourceTexture = textures[0];
            int currentTargetFBO = fbos[1];

            for (int i = 0; i < postSteps.size(); i++) {
                PostProcessingStep step = postSteps.get(i);
                boolean isLast = (i == postSteps.size() - 1);

                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, isLast ? 0 : currentTargetFBO);
                if (isLast && shouldClear) GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

                step.getProgram().bind(context);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentSourceTexture);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                step.getProgram().unbind();

                if (!isLast) {
                    currentSourceTexture = (currentTargetFBO == fbos[1]) ? textures[1] : textures[0];
                    currentTargetFBO = (currentTargetFBO == fbos[1]) ? fbos[0] : fbos[1];
                }
            }
            GL30.glBindVertexArray(0);
        }
    }

    public void cleanup() {
        if (computeProgram != null) computeProgram.cleanup();
        if (mainProgram != null) mainProgram.cleanup();
        for (PostProcessingStep step : postSteps) step.cleanup();
        if (!postSteps.isEmpty()) {
            for (int i = 0; i < 2; i++) {
                GL30.glDeleteFramebuffers(fbos[i]);
                GL30.glDeleteTextures(textures[i]);
            }
            GL30.glDeleteRenderbuffers(rboDepth);
        }
        GL30.glDeleteVertexArrays(dummyVAO);
    }
}
