#version 430 core
layout(local_size_x = 256, local_size_y = 1, local_size_z = 1) in;

struct FoliageInstance {
    vec4 positionAndScale;
    vec4 rotationAndType;
};

struct DrawElementsIndirectCommand {
    uint count;
    uint instanceCount;
    uint firstIndex;
    uint baseVertex;
    uint baseInstance;
};

layout(std430, binding = 0) readonly buffer InputBuffer { FoliageInstance allInstances[]; };
layout(std430, binding = 1) writeonly buffer OutputBuffer { FoliageInstance visibleInstances[]; };
layout(std430, binding = 2) buffer CommandBuffer { DrawElementsIndirectCommand drawCommand; };

layout(binding = 3) uniform sampler2D u_heightmap;

uniform float u_terrainSize = 1000.0;
uniform float u_maxHeight = 50.0;
uniform vec4 frustumPlanes[6];

uniform vec3 u_cameraPosition;
uniform float u_lodDistNear = 40.0;
uniform float u_lodDistMed = 120.0;
uniform float u_maxVisibilityDist = 250.0; // --- NEU: Absolute maximale Sichtweite für Gras ---

bool isVisible(vec3 pos, float radius) {
    for (int i = 0; i < 6; i++) {
        if (dot(frustumPlanes[i].xyz, pos) + frustumPlanes[i].w < -radius) {
            return false;
        }
    }
    return true;
}

void main() {
    uint gIdx = gl_GlobalInvocationID.x;
    if (gIdx >= 100000u) return;

    FoliageInstance plant = allInstances[gIdx];

    // Höhenberechnung via Heightmap
    vec2 uv = (plant.positionAndScale.xz + (u_terrainSize * 0.5)) / u_terrainSize;
    float height = texture(u_heightmap, uv).r * u_maxHeight;
    plant.positionAndScale.y = height;

    vec3 pos = plant.positionAndScale.xyz;
    float originalScale = plant.positionAndScale.w; // Ursprüngliche Skalierung merken
    float radius = originalScale * 1.5;

    float dist = distance(u_cameraPosition, pos);

    // Ganz weit entferntes Gras direkt verwerfen
    if (dist > u_maxVisibilityDist) return;

    bool passLOD = true;
    float fadeFactor = 1.0;

    // --- DYNAMISCHE LOD FILTERUNG & FADING ---
    if (dist > u_lodDistMed) {
        // Fernbereich: Nur jede 8. Pflanze rendern
        if ((gIdx % 8u) != 0u) return;

        // Fading zum Horizont hin (skaliert von u_lodDistMed bis u_maxVisibilityDist sanft auf 0)
        fadeFactor = 1.0 - ((dist - u_lodDistMed) / (u_maxVisibilityDist - u_lodDistMed));
    } else if (dist > u_lodDistNear) {
        // Mittlerer Bereich: Nur jede 3. Pflanze rendern
        if ((gIdx % 3u) != 0u) return;

        // Optional: Kleines Zwischenfading beim Übergang von Nah zu Mittel
        // Verhindert auffälliges Ploppen der ausgedünnten Halme
        float transitionZone = (u_lodDistMed - u_lodDistNear) * 0.2; // Die letzten 20% der Zone faden
        if (dist > (u_lodDistMed - transitionZone)) {
            fadeFactor = 0.5 + 0.5 * ((u_lodDistMed - dist) / transitionZone);
        }
    }

    // Skalierung mit dem Berechneten Fade-Faktor multiplizieren
    plant.positionAndScale.w = originalScale * clamp(fadeFactor, 0.0, 1.0);

    // Nur einreichen, wenn im Frustum
    if (isVisible(pos, radius)) {
        uint uniqueOutputIndex = atomicAdd(drawCommand.instanceCount, 1u);
        visibleInstances[uniqueOutputIndex] = plant;
    }
}