package main.rendering.pipeline;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class ShaderProgram {
    private final int programId;
    private final List<UniformBinder> uniformBinders = new ArrayList<>();

    public ShaderProgram(List<Shader> shaders) {
        this.programId = GL20.glCreateProgram();
        for (Shader shader : shaders) {
            GL20.glAttachShader(programId, shader.getId());
        }
        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
            throw new RuntimeException("GLSL-Linkerfehler: " + GL20.glGetProgramInfoLog(programId));
        }
        for (Shader shader : shaders) {
            GL20.glDetachShader(programId, shader.getId());
        }
    }

    public void addUniformBinder(UniformBinder binder) {
        if (binder != null) this.uniformBinders.add(binder);
    }

    public void bind(RenderContext context) {
        GL20.glUseProgram(programId);
        for (UniformBinder binder : uniformBinders) {
            binder.bind(context, programId);
        }
    }

    public void unbind() { GL20.glUseProgram(0); }
    public int getProgramId() { return programId; }
    public void cleanup() { GL20.glDeleteProgram(programId); }

    // --- Uniform Helfer-Methoden ---
    public static void setUniform(int programId, String name, float value) {
        int loc = GL20.glGetUniformLocation(programId, name);
        if (loc != -1) GL20.glUniform1f(loc, value);
    }

    public static void setUniform(int programId, String name, int value) {
        int loc = GL20.glGetUniformLocation(programId, name);
        if (loc != -1) GL20.glUniform1i(loc, value);
    }

    public static void setUniform(int programId, String name, org.joml.Vector2f vec) {
        int loc = GL20.glGetUniformLocation(programId, name);
        if (loc != -1) GL20.glUniform2f(loc, vec.x, vec.y);
    }

    public static void setUniform(int programId, String name, Matrix4f matrix) {
        int loc = GL20.glGetUniformLocation(programId, name);
        if (loc == -1) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            matrix.get(fb);
            GL20.glUniformMatrix4fv(loc, false, fb);
        }
    }
}
