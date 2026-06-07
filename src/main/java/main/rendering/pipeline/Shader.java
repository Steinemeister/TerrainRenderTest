package main.rendering.pipeline;

import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class Shader {
    private final int id;
    private final int type;

    // Konstruktor für Classpath-Dateien (z. B. "shaders/scene.vert")
    public Shader(int type, String classpathResourcePath) {
        this(type, loadSourceFromClasspath(classpathResourcePath), true);
    }

    // Konstruktor für direkten Quellcode (wird für den internen Post-Vertex-Shader genutzt)
    public Shader(int type, String sourceCode, boolean isRawSource) {
        this.type = type;
        this.id = GL20.glCreateShader(type);

        GL20.glShaderSource(id, sourceCode);
        GL20.glCompileShader(id);

        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            throw new RuntimeException("GLSL-Kompilierungsfehler (" + type + "): " + GL20.glGetShaderInfoLog(id));
        }
    }

    /**
     * Lädt den Quellcode einer Datei sicher über den Java Classpath,
     * egal ob in der IDE oder exportiert in einer JAR-Datei.
     */
    private static String loadSourceFromClasspath(String resourcePath) {
        // Sicherstellen, dass der Pfad nicht mit einem Slash beginnt, wenn der System-ClassLoader genutzt wird
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }

        try (InputStream is = ClassLoader.getSystemResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Shader-Ressource nicht im Classpath gefunden: " + resourcePath);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Lesen des Shaders aus dem Classpath: " + resourcePath, e);
        }
    }

    public int getId() { return id; }
    public int getType() { return type; }
    public void cleanup() { GL20.glDeleteShader(id); }
}
