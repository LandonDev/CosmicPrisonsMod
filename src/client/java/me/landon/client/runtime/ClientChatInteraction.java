package me.landon.client.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.landon.CosmicPrisonsMod;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

public class ClientChatInteraction {
    private static final Pattern COORDS =
            Pattern.compile(
                    "(?<!\\d)(-?\\d+)(?:x)?(?:, |\\s+)(-?\\d+)(?:y)?(?:, |\\s+)(-?\\d+)(?:z)?(?!\\d)");
    public static final String COORDS_CLICK_PREFIX = CosmicPrisonsMod.MOD_ID + ": ";
    private static final String HOVER_TRANSLATABLE_KEY =
            "text.cosmicprisonsmod.coords_ping.hover_coordinates";

    /**
     * Called by the ChatHud#addMessage mixin to modify the text before it is displayed to the user.
     *
     * @param text The original text that was going to be shown to the user
     * @return The modified text that will actually be shown to the user
     */
    public static Text modifyChatMessage(Text text) {
        Text out = text;

        out = makeCoordinatesClickable(out);

        return out;
    }

    private record Span(int start, int end, Style style, String text) {}

    /**
     * @param message The message to edit
     * @return Edited message with clickable coordinates that define a new location for a ping
     *     beacon when clicked.
     */
    public static Text makeCoordinatesClickable(Text message) {
        // Flatten the message
        List<Span> spans = new ArrayList<>();
        StringBuilder full = new StringBuilder();

        message.visit(
                (style, string) -> {
                    if (!string.isEmpty()) {
                        int start = full.length();
                        full.append(string);
                        int end = full.length();
                        spans.add(new Span(start, end, style, string));
                    }
                    return Optional.empty();
                },
                Style.EMPTY);

        String fullText = full.toString();
        Matcher matcher = COORDS.matcher(fullText);

        // If no matches in the raw string, there are no coordinates so just return the unedited
        // message
        if (!matcher.find()) return message;
        matcher.reset();

        MutableText out = Text.empty();
        int cursor = 0;

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            if (start > cursor) {
                appendRange(out, spans, cursor, start);
            }

            String coordsText = fullText.substring(start, end);
            Style coordsStyle =
                    Style.EMPTY
                            .withUnderline(true)
                            .withBold(true)
                            .withColor(Formatting.DARK_GREEN)
                            .withHoverEvent(
                                    new HoverEvent.ShowText(
                                            Text.translatable(HOVER_TRANSLATABLE_KEY)))
                            .withClickEvent(
                                    new ClickEvent.RunCommand(
                                            COORDS_CLICK_PREFIX
                                                    + matcher.group(1)
                                                    + " "
                                                    + matcher.group(2)
                                                    + " "
                                                    + matcher.group(3)));

            out.append(Text.literal(coordsText).setStyle(coordsStyle));
            cursor = end;
        }

        if (cursor < fullText.length()) {
            appendRange(out, spans, cursor, fullText.length());
        }

        return out;
    }

    private static void appendRange(MutableText out, List<Span> spans, int from, int to) {
        for (Span span : spans) {
            if (span.end <= from) continue;
            if (span.start >= to) break;

            int start = Math.max(from, span.start);
            int end = Math.min(to, span.end);

            if (start < end) {
                int localStart = start - span.start;
                int localEnd = end - span.start;
                String piece = span.text.substring(localStart, localEnd);
                out.append(Text.literal(piece).setStyle(span.style));
            }
        }
    }
}
