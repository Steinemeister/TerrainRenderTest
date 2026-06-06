package main.plants;

public class FoliageInstance {
    public float x, y, z, scale;
    public float rotX, rotY, rotZ, typeId;

    public FoliageInstance(float x, float y, float z, float scale, float rotY, float typeId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.scale = scale;
        this.rotX = 0.0f; // Für flache Pflanzen meist 0
        this.rotY = rotY; // Drehung um die eigene Achse
        this.rotZ = 0.0f;
        this.typeId = typeId;
    }
}
