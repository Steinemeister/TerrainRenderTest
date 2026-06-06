package main.rendering;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class Shader {
    private final int programId;

    public Shader(String computePath) {
        this.programId = GL20.glCreateProgram();
        int cShader = compileShader(computePath, GL43.GL_COMPUTE_SHADER);
        GL20.glAttachShader(programId, cShader);
        GL20.glLinkProgram(programId);
        checkLinkStatus(computePath);
        GL20.glDeleteShader(cShader);
    }

    public Shader(String vertexPath, String fragmentPath) {
        this.programId = GL20.glCreateProgram();

        int vShader = compileShader(vertexPath, GL20.GL_VERTEX_SHADER);
        int fShader = compileShader(fragmentPath, GL20.GL_FRAGMENT_SHADER);

        GL20.glAttachShader(programId, vShader);
        GL20.glAttachShader(programId, fShader);

        GL20.glLinkProgram(programId);
        checkLinkStatus(vertexPath + " / " + fragmentPath);

        GL20.glDeleteShader(vShader);
        GL20.glDeleteShader(fShader);
    }

    private int compileShader(String resourcePath, int type) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Shader-Datei nicht im Resources-Ordner gefunden: " + resourcePath);
            }

            // Datei in einen zusammenhängenden String einlesen
            String source = new BufferedReader(new InputStreamReader(in))
                    .lines()
                    .collect(Collectors.joining("\n"));

            int shaderId = GL20.glCreateShader(type);
            GL20.glShaderSource(shaderId, source);
            GL20.glCompileShader(shaderId);

            // Fehlerprüfung für diese Shader-Stufe
            if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                throw new RuntimeException("Kompilierungsfehler in " + resourcePath + ":\n" + GL20.glGetShaderInfoLog(shaderId));
            }
            return shaderId;
        } catch (Exception e) {
            throw new RuntimeException("Konnte Shader nicht laden: " + resourcePath, e);
        }
    }

    /**
     * Prüft, ob das Shader-Programm fehlerfrei gelinkt wurde
     */
    private void checkLinkStatus(String shaderNames) {
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Program Link-Fehler bei [" + shaderNames + "]:\n" + GL20.glGetProgramInfoLog(programId));
        }
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public int getId() {
        return programId;
    }
}
