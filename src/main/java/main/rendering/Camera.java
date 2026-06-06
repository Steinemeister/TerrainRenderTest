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
        for (int i = 0; i < 6; i++) frustumPlanes[i] = new Vector4f();
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
        // Links
        viewProjection.getRow(3, frustumPlanes[0]).add(viewProjection.getRow(0, new Vector4f()));
        // Rechts
        viewProjection.getRow(3, frustumPlanes[1]).sub(viewProjection.getRow(0, new Vector4f()));
        // Unten
        viewProjection.getRow(3, frustumPlanes[2]).add(viewProjection.getRow(1, new Vector4f()));
        // Oben
        viewProjection.getRow(3, frustumPlanes[3]).sub(viewProjection.getRow(1, new Vector4f()));
        // Nah
        viewProjection.getRow(3, frustumPlanes[4]).add(viewProjection.getRow(2, new Vector4f()));
        // Fern
        viewProjection.getRow(3, frustumPlanes[5]).sub(viewProjection.getRow(2, new Vector4f()));

        for (int i = 0; i < 6; i++) {
            Vector4f plane = frustumPlanes[i];
            // Länge des Normalenvektors (x, y, z) berechnen
            float length = (float) Math.sqrt(plane.x * plane.x + plane.y * plane.y + plane.z * plane.z);

            // Alle 4 Komponenten (inklusive Distanz w!) durch diese 3D-Länge teilen
            if (length > 0.0f) {
                plane.x /= length;
                plane.y /= length;
                plane.z /= length;
                plane.w /= length;
            }
        }
    }

    public Matrix4f getViewProjection() { return viewProjection; }
    public Vector4f[] getFrustumPlanes() { return frustumPlanes; }
}
