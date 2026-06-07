package main.rendering.pipeline;

public class ShaderConstants {
    public static final String DEFAULT_POST_VERTEX_SHADER = """
        #version 430 core
        out vec2 TexCoords;
        void main() {
            float x = -1.0 + float((gl_VertexID & 1) << 2);
            float y = -1.0 + float((gl_VertexID & 2) << 1);
            TexCoords = vec2(x * 0.5 + 0.5, y * 0.5 + 0.5);
            gl_Position = vec4(x, y, 0.0, 1.0);
        }
        """;
}
