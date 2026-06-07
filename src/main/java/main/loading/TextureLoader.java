package main.loading;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;

public class TextureLoader {
    public static int loadTextureFromClasspath(String resourcePath) {
        int[] width = new int[1];
        int[] height = new int[1];
        int[] channels = new int[1];

        // Datei über den Classpath einlesen
        java.io.InputStream is = ClassLoader.getSystemResourceAsStream(resourcePath);
        if (is == null) throw new RuntimeException("Textur nicht gefunden: " + resourcePath);

        try {
            byte[] bytes = is.readAllBytes();
            ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();

            // STBImage lädt die Bilddaten direkt aus dem Speicher-Buffer
            ByteBuffer image = STBImage.stbi_load_from_memory(buffer, width, height, channels, 1); // 1 = Erzwinge Graustufen (Luminanz)
            if (image == null) {
                throw new RuntimeException("Fehler beim Dekodieren des Bildes: " + STBImage.stbi_failure_reason());
            }

            int textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

            // Wichtig für Heightmaps: Lineare Filterung für sanfte Übergänge zwischen den Pixeln
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

            // Verhindert Kantenartefakte an den Grenzen des 1000m Feldes
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);

            // Texturdaten an die GPU senden (als R8-Format, da Graustufen)
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, width[0], height[0], 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, image);

            STBImage.stbi_image_free(image);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            return textureId;
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Laden der Heightmap-Textur", e);
        }
    }
}
