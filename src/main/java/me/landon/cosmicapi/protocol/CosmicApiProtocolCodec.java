package me.landon.cosmicapi.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CosmicApiProtocolCodec {
    public byte[] encodeClientHello(CosmicApiClientHello hello) {
        StringBuilder builder = new StringBuilder(512);
        builder.append('{');
        appendStringField(builder, "type", CosmicApiProtocolConstants.MESSAGE_TYPE_CLIENT_HELLO);
        builder.append(',');
        appendNumberField(builder, "protocolVersion", CosmicApiProtocolConstants.PROTOCOL_VERSION);
        builder.append(',');
        appendStringField(builder, "clientId", hello.clientId());
        builder.append(',');
        appendStringField(builder, "modId", hello.modId());
        builder.append(',');
        appendStringField(builder, "installId", hello.installId());
        builder.append(',');
        appendStringField(builder, "modLoader", hello.modLoader());
        builder.append(',');
        appendStringField(builder, "minecraftVersion", hello.minecraftVersion());
        builder.append(',');
        appendStringField(builder, "modVersion", hello.modVersion());
        builder.append(',');
        appendStringArrayField(builder, "requestedScopes", hello.requestedScopes());
        builder.append(',');
        appendStringArrayField(builder, "requestedHooks", hello.requestedHooks());
        builder.append('}');

        byte[] bytes = builder.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > CosmicApiProtocolConstants.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Cosmic API payload exceeds maximum allowed bytes");
        }
        return bytes;
    }

    public CosmicApiServerMessage decodeServerMessage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Cosmic API payload is empty");
        }
        if (bytes.length > CosmicApiProtocolConstants.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Cosmic API payload exceeds maximum allowed bytes");
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        return new CosmicApiServerMessage(
                readStringField(json, "type"),
                readStringField(json, "event"),
                readBooleanField(json, "allowed"),
                readStringField(json, "sessionId"),
                readStringField(json, "requestId"),
                readStringField(json, "reason"),
                readStringField(json, "appName"),
                readStringArrayField(json, "allowedScopes"),
                firstNonEmpty(
                        readStringArrayField(json, "allowedHooks"),
                        readStringArrayField(json, "allowedHookEvents")));
    }

    private static void appendStringField(StringBuilder builder, String name, String value) {
        appendQuoted(builder, name);
        builder.append(':');
        appendQuoted(builder, value);
    }

    private static void appendNumberField(StringBuilder builder, String name, int value) {
        appendQuoted(builder, name);
        builder.append(':').append(value);
    }

    private static void appendStringArrayField(
            StringBuilder builder, String name, List<String> values) {
        appendQuoted(builder, name);
        builder.append(':').append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            appendQuoted(builder, values.get(index));
        }
        builder.append(']');
    }

    private static void appendQuoted(StringBuilder builder, String value) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static String readStringField(String json, String fieldName) {
        int valueStart = findFieldValueStart(json, fieldName);
        if (valueStart < 0 || valueStart >= json.length() || json.charAt(valueStart) != '"') {
            return "";
        }
        return readQuoted(json, valueStart).value();
    }

    private static boolean readBooleanField(String json, String fieldName) {
        int valueStart = findFieldValueStart(json, fieldName);
        if (valueStart < 0) {
            return false;
        }
        return json.startsWith("true", valueStart);
    }

    private static List<String> readStringArrayField(String json, String fieldName) {
        int valueStart = findFieldValueStart(json, fieldName);
        if (valueStart < 0 || valueStart >= json.length() || json.charAt(valueStart) != '[') {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        int index = valueStart + 1;
        while (index < json.length()) {
            char character = json.charAt(index);
            if (character == ']') {
                return List.copyOf(values);
            }
            if (Character.isWhitespace(character) || character == ',') {
                index++;
                continue;
            }
            if (character != '"') {
                return List.copyOf(values);
            }
            QuotedValue quoted = readQuoted(json, index);
            values.add(quoted.value());
            index = quoted.nextIndex();
        }
        return List.copyOf(values);
    }

    private static int findFieldValueStart(String json, String fieldName) {
        String needle = "\"" + fieldName + "\"";
        int fieldIndex = json.indexOf(needle);
        if (fieldIndex < 0) {
            return -1;
        }
        int colonIndex = json.indexOf(':', fieldIndex + needle.length());
        if (colonIndex < 0) {
            return -1;
        }
        int index = colonIndex + 1;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        return index;
    }

    private static QuotedValue readQuoted(String json, int quoteIndex) {
        StringBuilder builder = new StringBuilder();
        int index = quoteIndex + 1;
        while (index < json.length()) {
            char character = json.charAt(index++);
            if (character == '"') {
                return new QuotedValue(builder.toString(), index);
            }
            if (character != '\\' || index >= json.length()) {
                builder.append(character);
                continue;
            }
            char escaped = json.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/' -> builder.append(escaped);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    if (index + 4 <= json.length()) {
                        builder.append((char) Integer.parseInt(json.substring(index, index + 4), 16));
                        index += 4;
                    }
                }
                default -> builder.append(escaped);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private static List<String> firstNonEmpty(List<String> first, List<String> second) {
        return !first.isEmpty() ? first : second;
    }

    private record QuotedValue(String value, int nextIndex) {}
}
