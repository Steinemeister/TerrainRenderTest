plugins {
    java // Aktiviert das Java-Plugin
}

repositories {
    mavenCentral() // Lädt die Bibliotheken aus dem zentralen Maven-Repository
}

// Erkennt automatisch das Betriebssystem für die nativen LWJGL-Bibliotheken
val osName = System.getProperty("os.name").lowercase()
val lwjglNatives = when {
    osName.contains("win") -> "natives-windows"
    osName.contains("mac") -> "natives-macos"
    osName.contains("nix") || osName.contains("nux") -> "natives-linux"
    else -> throw GradleException("Unsupported operating system: $osName")
}

dependencies {
    // 1. JOML (Mathematik-Bibliothek für 3D-Grafik)
    implementation("org.joml:joml:1.10.8")

    // 2. LWJGL Core
    implementation("org.lwjgl:lwjgl:3.3.4")
    runtimeOnly("org.lwjgl:lwjgl:3.3.4:$lwjglNatives")

    // 3. LWJGL GLFW (Fenster- und Input-Management)
    implementation("org.lwjgl:lwjgl-glfw:3.3.4")
    runtimeOnly("org.lwjgl:lwjgl-glfw:3.3.4:$lwjglNatives")

    // 4. LWJGL OpenGL (Grafik-Rendering)
    implementation("org.lwjgl:lwjgl-opengl:3.3.4")
    runtimeOnly("org.lwjgl:lwjgl-opengl:3.3.4:$lwjglNatives")

    // LWJGL Bill of Materials (BOM) definiert die passenden Versionen aller Module
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.4"))

    // STB-Modul (für STBImage) & OS-Natives
    implementation("org.lwjgl:lwjgl-stb")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
}