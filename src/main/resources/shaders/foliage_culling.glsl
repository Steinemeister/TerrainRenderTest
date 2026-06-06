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

// Die 6 Frustum Ebenen von der Java-Kamera
uniform vec4 frustumPlanes[6];

bool isVisible(vec3 pos, float radius) {
    for (int i = 0; i < 6; i++) {
        // Wenn die Distanz zur Ebene kleiner als minus Radius ist, liegt das Objekt außerhalb
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
    vec3 pos = plant.positionAndScale.xyz;
    float radius = plant.positionAndScale.w * 1.5; // Kugelradius basierend auf Skalierung

    if (isVisible(pos, radius)) {
        uint uniqueOutputIndex = atomicAdd(drawCommand.instanceCount, 1u);
        visibleInstances[uniqueOutputIndex] = plant;
    }
}