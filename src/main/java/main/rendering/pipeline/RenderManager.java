package main.rendering.pipeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RenderManager {
    private final List<RenderCommand> commandQueue = new ArrayList<>();

    public void submit(RenderCommand command) {
        commandQueue.add(command);
    }

    public void render(RenderContext context) {
        // Sortiert die Ebenen aufsteigend nach Priorität (niedrige Werte zuerst)
        commandQueue.sort(Comparator.comparingInt(RenderCommand::getLayerPriority));

        // Alle angemeldeten Pipelines nacheinander ausführen
        for (RenderCommand command : commandQueue) {
            command.execute(context);
        }

        // Warteschlange für den nächsten Frame leeren
        commandQueue.clear();
    }
}
