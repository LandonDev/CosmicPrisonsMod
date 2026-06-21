package me.landon.cosmicapi.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CosmicApiRuntimePayloads {
    public record HudWidget(String widgetId, List<String> lines, int ttlSeconds) {
        public HudWidget {
            widgetId = Objects.requireNonNull(widgetId, "widgetId");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }
    }

    public record InventoryItemOverlay(int slot, int overlayType, String displayText) {
        public InventoryItemOverlay {
            displayText = Objects.requireNonNull(displayText, "displayText");
        }
    }

    public record EntityMarkerDelta(
            int markerType, List<Integer> addEntityIds, List<Integer> removeEntityIds) {
        public EntityMarkerDelta {
            addEntityIds = List.copyOf(Objects.requireNonNull(addEntityIds, "addEntityIds"));
            removeEntityIds =
                    List.copyOf(Objects.requireNonNull(removeEntityIds, "removeEntityIds"));
        }
    }

    public static List<HudWidget> mutableHudWidgetListWithCapacity(int capacity) {
        return new ArrayList<>(Math.max(0, capacity));
    }

    public static List<InventoryItemOverlay> mutableInventoryItemOverlayListWithCapacity(
            int capacity) {
        return new ArrayList<>(Math.max(0, capacity));
    }

    private CosmicApiRuntimePayloads() {}
}
