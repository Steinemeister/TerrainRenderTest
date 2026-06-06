#version 430 core

struct FoliageInstance {
    vec4 positionAndScale;
    vec4 rotationAndType;
};

// Wir binden das Ausgabeband des Compute Shaders als Eingabeband des Grafik Shaders!
layout(std430, binding = 1) readonly buffer VisibleBuffer { FoliageInstance visibleInstances[]; };

uniform mat4 viewProjection;

out vec3 outColor;

// Lokale Geometrie eines simplen Grashalms (Vorderseite eines Dreiecks)
vec3 grassVertices[3] = vec3[](
vec3(-0.1, 0.0, 0.0), // Unten Links
vec3( 0.1, 0.0, 0.0), // Unten Rechts
vec3( 0.0, 1.5, 0.0)  // Spitze
);

void main() {
    // Hole die Daten der sichtbaren Instanz für diesen Draw-Aufruf
    FoliageInstance plant = visibleInstances[gl_InstanceID];
    vec3 worldPos = plant.positionAndScale.xyz;
    float scale = plant.positionAndScale.w;
    float rotY = plant.rotationAndType.y;

    // Lokalen Vertex holen
    vec3 localVertex = grassVertices[gl_VertexID];

    // Simple Rotation um die Y-Achse auf der GPU berechnen
    float cosY = cos(rotY);
    float sinY = sin(rotY);
    vec3 rotatedVertex;
    rotatedVertex.x = localVertex.x * cosY - localVertex.z * sinY;
    rotatedVertex.y = localVertex.y;
    rotatedVertex.z = localVertex.x * sinY + localVertex.z * cosY;

    // Skalieren und Weltposition bestimmen
    vec3 finalWorldPos = worldPos + (rotatedVertex * scale);

    // Farbe generieren (wird nach oben hin heller grün)
    outColor = mix(vec3(0.05, 0.2, 0.05), vec3(0.2, 0.6, 0.1), localVertex.y / 1.5);

    gl_Position = viewProjection * vec4(finalWorldPos, 1.0);
}

