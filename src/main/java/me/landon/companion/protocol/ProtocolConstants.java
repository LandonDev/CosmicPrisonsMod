package me.landon.companion.protocol;

import net.minecraft.util.Identifier;

public final class ProtocolConstants {
    public static final Identifier CHANNEL_ID = Identifier.of("servercompanion", "main");
    public static final int PROTOCOL_VERSION = 1;

    public static final int MAX_PACKET_BYTES = 16 * 1024;
    public static final int MAX_STRING_BYTES = 2048;
    public static final int MAX_WIDGET_COUNT = 64;
    public static final int MAX_WIDGET_LINES = 16;
    public static final int MAX_LIST_LEN = 256;
    public static final int MAX_ENTITY_DELTA = 2048;
    public static final int MAX_ITEM_OVERLAY_ENTRIES = 64;
    public static final int MAX_ITEM_OVERLAY_COUNT = MAX_ITEM_OVERLAY_ENTRIES;
    public static final int MAX_ITEM_OVERLAY_TEXT_BYTES = 24;
    public static final int MAX_INVENTORY_SLOT_INDEX = 53;
    public static final int MAX_SIGNATURE_BYTES = 255;
    public static final int MESSAGE_TYPE_PING_INTENT_C2S = 11;
    public static final int PING_TYPE_GANG = 1;
    public static final int PING_TYPE_TRUCE = 2;

    public static final int SERVER_FEATURE_HUD_WIDGETS = 1 << 0;
    public static final int SERVER_FEATURE_ENTITY_MARKERS = 1 << 1;
    public static final int FEATURE_GANG_TRUCE_PINGS = 1 << 6;
    public static final int SERVER_FEATURE_GANG_TRUCE_PINGS = FEATURE_GANG_TRUCE_PINGS;
    public static final int SERVER_FEATURE_INVENTORY_ITEM_OVERLAYS = 1 << 5;
    public static final int MARKER_TYPE_SAME_GANG = 1;
    public static final int MARKER_TYPE_PEACEFUL_MINING_PASS_THROUGH = 2;
    public static final int MARKER_TYPE_GANG_PING_BEACON = 3;
    public static final int MARKER_TYPE_TRUCE_PING_BEACON = 4;
    public static final int OVERLAY_TYPE_COSMIC_ENERGY = 1;
    public static final int OVERLAY_TYPE_MONEY_NOTE = 2;
    public static final int OVERLAY_TYPE_GANG_POINT_NOTE = 3;
    public static final int OVERLAY_TYPE_SATCHEL_PERCENT = 4;
    public static final int OVERLAY_TYPE_PET_ACTIVE = 5;
    public static final int OVERLAY_TYPE_PET_COOLDOWN = 6;
    public static final int CLIENT_HELLO_RETRY_TICKS = 60;

    private ProtocolConstants() {}
}
