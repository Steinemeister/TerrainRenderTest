package main;

import main.rendering.Window;

public class Launcher {
    public static Window window;
    public static void main(String[] args) {
        window = new Window();
        window.run();
    }
}
