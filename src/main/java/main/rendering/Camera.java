package main.rendering;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class Camera {
    public Vector3f position = new Vector3f(0, 5, 10);
    public float yaw = -90.0f;
    public float pitch = 0.0f;

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f viewProjection = new Matrix4f();

    // Array für die 6 Frustum-Ebenen (jeweils vec4: x, y, z = Normale, w = Distanz)
    private final Vector4f[] frustumPlanes = new Vector4f[6];

    public Camera() {
        for (int i = 0; i < 6; i++) {
            frustumPlanes[i] = new Vector4f();
        }
    }

    public void update(int width, int height) {
        // View Matrix berechnen basierend auf Rotation
        Vector3f front = new Vector3f();
        front.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.y = (float) Math.sin(Math.toRadians(pitch));
        front.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.normalize();

        viewMatrix.setLookAt(position, new Vector3f(position).add(front), new Vector3f(0, 1, 0));
        projectionMatrix.setPerspective((float) Math.toRadians(60.0f), (float) width / height, 0.5f, 1000.0f);

        // View-Projection Matrix für das Culling kombinieren
        projectionMatrix.mul(viewMatrix, viewProjection);

        // Frustum Ebenen direkt aus der VP-Matrix extrahieren
        extractPlanes();
    }

    private void extractPlanes() {
        // Zwischenspeicher-Vektoren, um Garbage-Collection-Overhead in der Schleife zu verhindern
        Vector4f row0 = new Vector4f();
        Vector4f row1 = new Vector4f();
        Vector4f row2 = new Vector4f();
        Vector4f row3 = new Vector4f();

        viewProjection.getRow(0, row0);
        viewProjection.getRow(1, row1);
        viewProjection.getRow(2, row2);
        viewProjection.getRow(3, row3);

        // Links
        row3.add(row0, frustumPlanes[0]);
        // Rechts
        row3.sub(row0, frustumPlanes[1]);
        // Unten
        row3.add(row1, frustumPlanes[2]);
        // Oben
        row3.sub(row1, frustumPlanes[3]);
        // Nah
        row3.add(row2, frustumPlanes[4]);
        // Fern
        row3.sub(row2, frustumPlanes[5]);

        for (int i = 0; i < 6; i++) {
            Vector4f plane = frustumPlanes[i];
            // Länge des Normalenvektors (x, y, z) berechnen
            float length = (float) Math.sqrt(plane.x * plane.x + plane.y * plane.y + plane.z * plane.z);

            // Alle 4 Komponenten (inklusive Distanz w!) durch diese 3D-Länge teilen
            if (length > 0.0f) {
                plane.div(length);
            }
        }
    }

    // --- Bestehende und neue Getter für das automatisierte Pipelinesystem ---
    public Matrix4f getProjectionMatrix() { return projectionMatrix; }
    public Matrix4f getViewMatrix() { return viewMatrix; }
    public Matrix4f getViewProjection() { return viewProjection; }
    public Vector4f[] getFrustumPlanes() { return frustumPlanes; }
}
