package me.landon.client.screen;

import java.util.ArrayList;
import java.util.List;
import me.landon.client.feature.ClientFeatureDefinition;
import me.landon.client.feature.ClientFeatures;
import me.landon.client.runtime.CompanionClientRuntime;
import me.landon.companion.protocol.ProtocolConstants;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class FeatureSettingsScreen extends Screen {
    private static final int GRID_TOP = 58;
    private static final int CARD_WIDTH = 176;
    private static final int CARD_HEIGHT = 92;
    private static final int CARD_GAP = 10;
    private static final int POPUP_BASE_WIDTH = 320;
    private static final int POPUP_BASE_HEIGHT = 232;
    private static final int FEATURE_SUMMARY_MAX_LINES = 2;
    private static final int ICON_FRAME_SIZE = 28;

    private final CompanionClientRuntime runtime;
    private final List<ButtonWidget> mainButtons = new ArrayList<>();
    private final List<PopupActionButton> popupActionButtons = new ArrayList<>();
    private List<ClientFeatureDefinition> features = List.of();

    private String popupFeatureId;
    private boolean popupOpening;
    private float popupProgress;

    public FeatureSettingsScreen(CompanionClientRuntime runtime) {
        super(Text.translatable("text.cosmicprisonsmod.settings.title"));
        this.runtime = runtime;
    }

    @Override
    protected void init() {
        super.init();
        features = runtime.getAvailableFeatures();
        mainButtons.clear();

        int footerButtonWidth = Math.min(144, (width - 42) / 2);
        int footerGap = 10;
        int footerRowWidth = (footerButtonWidth * 2) + footerGap;
        int footerStartX = (width - footerRowWidth) / 2;
        int footerButtonY = height - 28;

        for (int index = 0; index < features.size(); index++) {
            ClientFeatureDefinition feature = features.get(index);
            CardBounds cardBounds = cardBounds(index);
            int toggleWidth = 56;
            int seeMoreWidth = 84;
            int buttonY = cardBounds.y + cardBounds.height - 28;
            int toggleX = cardBounds.right() - toggleWidth - 10;
            int seeMoreX = toggleX - seeMoreWidth - 6;

            ButtonWidget seeMoreButton =
                    addDrawableChild(
                            ButtonWidget.builder(
                                            Text.translatable(
                                                    "text.cosmicprisonsmod.settings.see_more"),
                                            button -> openPopup(feature.id()))
                                    .dimensions(seeMoreX, buttonY, seeMoreWidth, 18)
                                    .build());
            mainButtons.add(seeMoreButton);

            ButtonWidget toggleButton =
                    addDrawableChild(
                            ButtonWidget.builder(
                                            toggleButtonText(feature.id()),
                                            button -> {
                                                runtime.setFeatureEnabled(
                                                        feature.id(),
                                                        !runtime.isFeatureEnabled(feature.id()));
                                                button.setMessage(toggleButtonText(feature.id()));
                                            })
                                    .dimensions(toggleX, buttonY, toggleWidth, 18)
                                    .build());
            mainButtons.add(toggleButton);
        }

        ButtonWidget hudLayoutButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.settings.button.hud_layout"),
                                        button -> openHudLayoutEditor())
                                .dimensions(footerStartX, footerButtonY, footerButtonWidth, 20)
                                .build());
        mainButtons.add(hudLayoutButton);

        ButtonWidget doneButton =
                addDrawableChild(
                        ButtonWidget.builder(
                                        Text.translatable(
                                                "text.cosmicprisonsmod.settings.button.done"),
                                        button -> close())
                                .dimensions(
                                        footerStartX + footerButtonWidth + footerGap,
                                        footerButtonY,
                                        footerButtonWidth,
                                        20)
                                .build());
        mainButtons.add(doneButton);

        updateMainButtonsState();
    }

    @Override
    public void tick() {
        super.tick();

        if (popupFeatureId == null) {
            popupProgress = 0.0F;
            popupActionButtons.clear();
            return;
        }

        float speed = 0.16F;

        if (popupOpening) {
            popupProgress = Math.min(1.0F, popupProgress + speed);
        } else {
            popupProgress = Math.max(0.0F, popupProgress - speed);

            if (popupProgress <= 0.0F) {
                popupFeatureId = null;
            }
        }

        updateMainButtonsState();
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.getKeycode() == GLFW.GLFW_KEY_ESCAPE && popupFeatureId != null) {
            closePopup();
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (popupFeatureId != null && popupProgress > 0.05F) {
            for (PopupActionButton actionButton : popupActionButtons) {
                if (!isPointWithin(
                        click.x(),
                        click.y(),
                        actionButton.x(),
                        actionButton.y(),
                        actionButton.width(),
                        actionButton.height())) {
                    continue;
                }

                actionButton.action().run();
                return true;
            }

            PopupLayout popupLayout = popupLayout(popupProgress);
            ClientFeatureDefinition feature = featureById(popupFeatureId);

            boolean clickedClose =
                    isPointWithin(
                            click.x(),
                            click.y(),
                            popupLayout.closeX,
                            popupLayout.closeY,
                            popupLayout.closeWidth,
                            popupLayout.closeHeight);
            boolean clickedInsidePopup =
                    isPointWithin(
                            click.x(),
                            click.y(),
                            popupLayout.x,
                            popupLayout.y,
                            popupLayout.width,
                            popupLayout.height);

            if (clickedClose || !clickedInsidePopup) {
                closePopup();
            }

            return true;
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(new GameMenuScreen(true));
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks) {
        drawContext.fill(0, 0, width, height, 0xC00C121B);
        drawContext.fill(0, 0, width, 36, 0x5A182437);

        renderHeader(drawContext);
        renderFeatureDeck(drawContext);
        renderFooterBand(drawContext);

        renderFeatureCards(drawContext);
        super.render(drawContext, mouseX, mouseY, deltaTicks);
        renderPopup(drawContext);
    }

    private void renderHeader(DrawContext drawContext) {
        int panelWidth = Math.min(372, width - 24);
        int panelHeight = 44;
        int x = (width - panelWidth) / 2;
        int y = 8;
        int right = x + panelWidth;
        int bottom = y + panelHeight;

        drawContext.fill(x - 2, y - 2, right + 2, bottom + 2, withAlpha(0x030609, 156));
        drawContext.fill(x, y, right, bottom, withAlpha(0x121C2A, 242));
        drawContext.fill(x, y, right, y + 2, withAlpha(0x5D93D5, 255));
        drawContext.fill(x + 18, bottom - 5, right - 18, bottom - 4, withAlpha(0x344A69, 255));

        drawScaledCenteredText(drawContext, title, width / 2, y + 8, 0xFFFFFFFF, 1.15F);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.settings.subtitle"),
                width / 2,
                y + 28,
                0xFFB9C9DD);
    }

    private void renderFeatureDeck(DrawContext drawContext) {
        int columns = gridColumns();
        int rows = Math.max(1, (features.size() + columns - 1) / columns);
        int gridWidth = (columns * CARD_WIDTH) + ((columns - 1) * CARD_GAP);
        int gridHeight = (rows * CARD_HEIGHT) + ((rows - 1) * CARD_GAP);
        int x = ((width - gridWidth) / 2) - 12;
        int y = GRID_TOP - 8;
        int right = x + gridWidth + 24;
        int bottom = y + gridHeight + 16;

        drawContext.fill(x - 2, y - 2, right + 2, bottom + 2, withAlpha(0x04070D, 112));
        drawContext.fill(x, y, right, bottom, withAlpha(0x0E1622, 196));
        drawContext.fill(x, y, right, y + 1, withAlpha(0x2F415B, 255));
        drawContext.fill(x, bottom - 1, right, bottom, withAlpha(0x2F415B, 255));
        drawContext.fill(x, y, x + 1, bottom, withAlpha(0x223146, 255));
        drawContext.fill(right - 1, y, right, bottom, withAlpha(0x223146, 255));
    }

    private void renderFooterBand(DrawContext drawContext) {
        int top = height - 40;
        drawContext.fill(0, top - 6, width, height, withAlpha(0x0D1520, 224));
        drawContext.fill(0, top - 6, width, top - 5, withAlpha(0x2B3D57, 255));
    }

    private void renderFeatureCards(DrawContext drawContext) {
        if (features.isEmpty()) {
            drawContext.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("text.cosmicprisonsmod.settings.empty"),
                    width / 2,
                    GRID_TOP + 16,
                    0xFFBFC6D2);
            return;
        }

        for (int index = 0; index < features.size(); index++) {
            ClientFeatureDefinition feature = features.get(index);
            CardBounds cardBounds = cardBounds(index);
            int accentColor = featureAccentColor(feature.id());

            drawContext.fill(
                    cardBounds.x,
                    cardBounds.y,
                    cardBounds.right(),
                    cardBounds.bottom(),
                    withAlpha(0x141B29, 232));
            drawContext.fill(
                    cardBounds.x,
                    cardBounds.y,
                    cardBounds.right(),
                    cardBounds.y + 2,
                    withAlpha(accentColor, 255));
            drawContext.fill(
                    cardBounds.x,
                    cardBounds.bottom() - 1,
                    cardBounds.right(),
                    cardBounds.bottom(),
                    withAlpha(0x2A3752, 255));
            drawContext.fill(
                    cardBounds.x,
                    cardBounds.y,
                    cardBounds.x + 1,
                    cardBounds.bottom(),
                    withAlpha(0x2A3752, 255));
            drawContext.fill(
                    cardBounds.right() - 1,
                    cardBounds.y,
                    cardBounds.right(),
                    cardBounds.bottom(),
                    withAlpha(0x2A3752, 255));
            drawContext.fill(
                    cardBounds.x + 1,
                    cardBounds.y + 3,
                    cardBounds.right() - 1,
                    cardBounds.y + 21,
                    withAlpha(accentColor, 22));

            drawFeatureIcon(drawContext, feature, cardBounds.x + 8, cardBounds.y + 7, accentColor);

            drawContext.drawTextWithShadow(
                    textRenderer,
                    Text.translatable(feature.nameTranslationKey()),
                    cardBounds.x + 42,
                    cardBounds.y + 10,
                    0xFFFFFFFF);

            int summaryY = cardBounds.y + 38;
            int lineHeight = textRenderer.fontHeight + 2;
            List<String> summaryLines =
                    summaryLines(feature, cardBounds.width - 20, FEATURE_SUMMARY_MAX_LINES);
            for (int lineIndex = 0; lineIndex < summaryLines.size(); lineIndex++) {
                drawContext.drawTextWithShadow(
                        textRenderer,
                        summaryLines.get(lineIndex),
                        cardBounds.x + 10,
                        summaryY + (lineIndex * lineHeight),
                        0xFFB9C5D8);
            }
        }
    }

    private void renderPopup(DrawContext drawContext) {
        if (popupFeatureId == null || popupProgress <= 0.0F) {
            return;
        }

        ClientFeatureDefinition feature = featureById(popupFeatureId);

        if (feature == null) {
            return;
        }

        float eased = easeOutBack(popupProgress);
        int overlayAlpha = (int) (170.0F * Math.min(1.0F, popupProgress * 1.2F));
        drawContext.fill(0, 0, width, height, overlayAlpha << 24);

        PopupLayout popupLayout = popupLayout(popupProgress);
        int x = popupLayout.x;
        int y = popupLayout.y;
        int right = x + popupLayout.width;
        int bottom = y + popupLayout.height;
        int accentColor = featureAccentColor(feature.id());

        drawContext.fill(x - 2, y - 2, right + 2, bottom + 2, withAlpha(0x02050A, 150));
        drawContext.fill(x, y, right, bottom, withAlpha(0x111929, 246));
        drawContext.fill(x, y, right, y + 2, withAlpha(accentColor, 255));
        drawContext.fill(x, y, x + 1, bottom, withAlpha(0x3A4C70, 255));
        drawContext.fill(right - 1, y, right, bottom, withAlpha(0x3A4C70, 255));
        drawContext.fill(x, bottom - 1, right, bottom, withAlpha(0x3A4C70, 255));

        drawFeatureIcon(drawContext, feature, x + 14, y + 14, accentColor);

        drawContext.drawTextWithShadow(
                textRenderer,
                Text.translatable(feature.nameTranslationKey()),
                x + 50,
                y + 19,
                0xFFFFFFFF);

        drawContext.drawWrappedTextWithShadow(
                textRenderer,
                Text.translatable(feature.descriptionTranslationKey()),
                x + 14,
                y + 52,
                popupLayout.width - 28,
                0xFFE5EDF8);

        drawContext.drawTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.settings.popup.examples"),
                x + 14,
                y + 104,
                0xFFE6EEFB);

        int examplesY = y + 118;
        int exampleHeight = popupExampleHeight(feature);
        int examplesGap = 8;
        int exampleWidth = (popupLayout.width - 28 - examplesGap) / 2;
        int leftExampleX = x + 14;
        int rightExampleX = leftExampleX + exampleWidth + examplesGap;
        renderPopupExamples(
                drawContext,
                feature,
                leftExampleX,
                rightExampleX,
                examplesY,
                exampleWidth,
                exampleHeight);
        int actionsY = examplesY + exampleHeight + 10;
        renderPopupActions(drawContext, feature, popupLayout, accentColor, actionsY);

        drawContext.fill(
                popupLayout.closeX,
                popupLayout.closeY,
                popupLayout.closeX + popupLayout.closeWidth,
                popupLayout.closeY + popupLayout.closeHeight,
                withAlpha(0x324B73, 255));
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.settings.popup.close"),
                popupLayout.closeX + (popupLayout.closeWidth / 2),
                popupLayout.closeY + 5,
                0xFFFFFFFF);

        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.settings.popup.hint"),
                x + (popupLayout.width / 2),
                bottom - 12,
                withAlpha(0xC0D0E5, (int) (215.0F * eased)));
    }

    private void drawPopupExample(
            DrawContext drawContext,
            int x,
            int y,
            int width,
            int height,
            Item item,
            int overlayType,
            String overlayText,
            Text label) {
        drawContext.fill(x, y, x + width, y + height, withAlpha(0x1A2436, 220));
        drawContext.fill(x, y, x + width, y + 1, withAlpha(0x354B70, 255));
        int itemX = x + 6;
        int itemY = y + 7;
        drawContext.drawItem(item.getDefaultStack(), itemX, itemY);
        drawOverlayTextOnItem(drawContext, itemX, itemY, overlayType, overlayText, 0.56F);
        drawContext.drawTextWithShadow(textRenderer, label, x + 27, y + 11, 0xFFE7EEF9);
    }

    private void drawPopupTextExample(
            DrawContext drawContext, int x, int y, int width, int height, Text text) {
        drawContext.fill(x, y, x + width, y + height, withAlpha(0x1A2436, 220));
        drawContext.fill(x, y, x + width, y + 1, withAlpha(0x354B70, 255));
        drawContext.drawWrappedTextWithShadow(
                textRenderer, text, x + 7, y + 9, width - 14, 0xFFE7EEF9);
    }

    private void renderPopupExamples(
            DrawContext drawContext,
            ClientFeatureDefinition feature,
            int leftExampleX,
            int rightExampleX,
            int examplesY,
            int exampleWidth,
            int exampleHeight) {
        if (ClientFeatures.PEACEFUL_MINING_ID.equals(feature.id())) {
            drawPopupTextExample(
                    drawContext,
                    leftExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.peaceful_mining.example.player_blocking"));
            drawPopupTextExample(
                    drawContext,
                    rightExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.peaceful_mining.example.mine_through"));
            return;
        }

        if (ClientFeatures.HUD_COOLDOWNS_ID.equals(feature.id())) {
            drawPopupTextExample(
                    drawContext,
                    leftExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.hud_cooldowns.example.active"));
            drawPopupTextExample(
                    drawContext,
                    rightExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.hud_cooldowns.example.ordering"));
            return;
        }

        if (ClientFeatures.HUD_PETS_ID.equals(feature.id())) {
            drawPopupTextExample(
                    drawContext,
                    leftExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable("text.cosmicprisonsmod.feature.hud_pets.example.active"));
            drawPopupTextExample(
                    drawContext,
                    rightExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable("text.cosmicprisonsmod.feature.hud_pets.example.ready"));
            return;
        }

        if (ClientFeatures.HUD_EVENTS_ID.equals(feature.id())) {
            drawPopupTextExample(
                    drawContext,
                    leftExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable("text.cosmicprisonsmod.feature.hud_events.example.reboot"));
            drawPopupTextExample(
                    drawContext,
                    rightExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.hud_events.example.level_cap"));
            return;
        }

        if (ClientFeatures.HUD_SATCHEL_DISPLAY_ID.equals(feature.id())) {
            drawPopupTextExample(
                    drawContext,
                    leftExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.hud_satchel_display.example.ore_line"));
            drawPopupTextExample(
                    drawContext,
                    rightExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.hud_satchel_display.example.count"));
            return;
        }

        if (ClientFeatures.HUD_GANG_ID.equals(feature.id())) {
            drawPopupTextExample(
                    drawContext,
                    leftExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable("text.cosmicprisonsmod.feature.hud_gang.example.summary"));
            drawPopupTextExample(
                    drawContext,
                    rightExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable("text.cosmicprisonsmod.feature.hud_gang.example.members"));
            return;
        }

        if (ClientFeatures.HUD_LEADERBOARDS_ID.equals(feature.id())) {
            drawPopupTextExample(
                    drawContext,
                    leftExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.hud_leaderboards.example.line"));
            drawPopupTextExample(
                    drawContext,
                    rightExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.hud_leaderboards.example.modes"));
            return;
        }

        if (ClientFeatures.PINGS_ID.equals(feature.id())) {
            drawPopupTextExample(
                    drawContext,
                    leftExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.pings.example.gang",
                            runtime.gangPingKeybindLabel()));
            drawPopupTextExample(
                    drawContext,
                    rightExampleX,
                    examplesY,
                    exampleWidth,
                    exampleHeight,
                    Text.translatable(
                            "text.cosmicprisonsmod.feature.pings.example.truce",
                            runtime.trucePingKeybindLabel()));
            return;
        }

        drawPopupExample(
                drawContext,
                leftExampleX,
                examplesY,
                exampleWidth,
                exampleHeight,
                Items.LIGHT_BLUE_DYE,
                ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY,
                Text.translatable(
                                "text.cosmicprisonsmod.feature.inventory_item_overlays.example.cosmic_value")
                        .getString(),
                Text.translatable(
                        "text.cosmicprisonsmod.feature.inventory_item_overlays.example.cosmic_label"));
        drawPopupExample(
                drawContext,
                rightExampleX,
                examplesY,
                exampleWidth,
                exampleHeight,
                Items.PAPER,
                ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE,
                Text.translatable(
                                "text.cosmicprisonsmod.feature.inventory_item_overlays.example.money_value")
                        .getString(),
                Text.translatable(
                        "text.cosmicprisonsmod.feature.inventory_item_overlays.example.money_label"));
    }

    private void openPopup(String featureId) {
        popupFeatureId = featureId;
        popupProgress = 0.0F;
        popupOpening = true;
        popupActionButtons.clear();
        updateMainButtonsState();
    }

    private void closePopup() {
        popupOpening = false;
        popupActionButtons.clear();
    }

    private void updateMainButtonsState() {
        boolean popupActive = popupFeatureId != null;

        for (ButtonWidget button : mainButtons) {
            button.active = !popupActive;
        }
    }

    private ClientFeatureDefinition featureById(String featureId) {
        for (ClientFeatureDefinition feature : features) {
            if (feature.id().equals(featureId)) {
                return feature;
            }
        }

        return null;
    }

    private List<String> summaryLines(ClientFeatureDefinition feature, int maxWidth, int maxLines) {
        String full = Text.translatable(feature.descriptionTranslationKey()).getString();
        String remaining = full == null ? "" : full.trim();
        List<String> lines = new ArrayList<>();

        while (!remaining.isEmpty() && lines.size() < maxLines) {
            String line = textRenderer.trimToWidth(remaining, maxWidth);
            if (line.isEmpty()) {
                break;
            }

            int consumed = line.length();
            if (consumed < remaining.length()) {
                int lastSpace = line.lastIndexOf(' ');
                if (lastSpace > 0) {
                    line = line.substring(0, lastSpace);
                    consumed = lastSpace;
                }
            }

            line = line.stripTrailing();
            if (line.isEmpty()) {
                line = textRenderer.trimToWidth(remaining, maxWidth);
                consumed = Math.max(1, line.length());
            }

            lines.add(line);
            remaining = remaining.substring(Math.min(consumed, remaining.length())).stripLeading();
        }

        if (!remaining.isEmpty() && !lines.isEmpty()) {
            int lastLineIndex = lines.size() - 1;
            int ellipsisWidth = textRenderer.getWidth("...");
            String clamped =
                    textRenderer.trimToWidth(
                            lines.get(lastLineIndex), Math.max(8, maxWidth - ellipsisWidth));
            lines.set(lastLineIndex, clamped.isEmpty() ? "..." : clamped.stripTrailing() + "...");
        }

        return lines;
    }

    private void drawFeatureIcon(
            DrawContext drawContext,
            ClientFeatureDefinition feature,
            int x,
            int y,
            int accentColor) {
        int size = ICON_FRAME_SIZE;
        drawContext.fill(x, y, x + size, y + size, withAlpha(0x11131A, 230));
        drawContext.fill(x, y, x + size, y + 1, withAlpha(accentColor, 255));
        drawContext.fill(x, y, x + 1, y + size, withAlpha(0x3A4B6A, 255));
        drawContext.fill(x + size - 1, y, x + size, y + size, withAlpha(0x3A4B6A, 255));
        drawContext.fill(x, y + size - 1, x + size, y + size, withAlpha(0x3A4B6A, 255));

        int itemX = x + ((size - 16) / 2);
        int itemY = y + ((size - 16) / 2);

        if (ClientFeatures.PEACEFUL_MINING_ID.equals(feature.id())) {
            drawContext.drawItem(Items.IRON_PICKAXE.getDefaultStack(), itemX, itemY);
            return;
        }

        if (ClientFeatures.INVENTORY_ITEM_OVERLAYS_ID.equals(feature.id())) {
            drawContext.drawItem(Items.PAPER.getDefaultStack(), itemX, itemY);
            drawOverlayTextOnItem(
                    drawContext,
                    itemX,
                    itemY,
                    ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY,
                    Text.translatable(
                                    "text.cosmicprisonsmod.feature.inventory_item_overlays.example.cosmic_value")
                            .getString(),
                    0.54F);
            return;
        }

        if (ClientFeatures.HUD_COOLDOWNS_ID.equals(feature.id())) {
            drawContext.drawItem(Items.CLOCK.getDefaultStack(), itemX, itemY);
            return;
        }

        if (ClientFeatures.HUD_PETS_ID.equals(feature.id())) {
            drawContext.drawItem(Items.BONE.getDefaultStack(), itemX, itemY);
            return;
        }

        if (ClientFeatures.HUD_EVENTS_ID.equals(feature.id())) {
            drawContext.drawItem(Items.RECOVERY_COMPASS.getDefaultStack(), itemX, itemY);
            return;
        }

        if (ClientFeatures.HUD_SATCHEL_DISPLAY_ID.equals(feature.id())) {
            drawContext.drawItem(Items.BUNDLE.getDefaultStack(), itemX, itemY);
            return;
        }

        if (ClientFeatures.HUD_GANG_ID.equals(feature.id())) {
            drawContext.drawItem(Items.RED_BANNER.getDefaultStack(), itemX, itemY);
            return;
        }

        if (ClientFeatures.HUD_LEADERBOARDS_ID.equals(feature.id())) {
            drawContext.drawItem(Items.NETHER_STAR.getDefaultStack(), itemX, itemY);
            return;
        }

        if (ClientFeatures.PINGS_ID.equals(feature.id())) {
            drawContext.drawItem(Items.BEACON.getDefaultStack(), itemX, itemY);
            return;
        }

        Text icon = Text.translatable(feature.iconTranslationKey());
        int iconWidth = textRenderer.getWidth(icon);
        drawContext.drawTextWithShadow(
                textRenderer,
                icon,
                x + ((size - iconWidth) / 2),
                y + ((size - textRenderer.fontHeight) / 2),
                0xFFFFFFFF);
    }

    private void drawScaledCenteredText(
            DrawContext drawContext, Text text, int centerX, int y, int color, float scale) {
        int scaledTextX = Math.round(centerX - ((textRenderer.getWidth(text) * scale) / 2.0F));
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(scaledTextX, y);
        drawContext.getMatrices().scale(scale, scale);
        drawContext.drawTextWithShadow(textRenderer, text, 0, 0, color);
        drawContext.getMatrices().popMatrix();
    }

    private void drawOverlayTextOnItem(
            DrawContext drawContext,
            int slotX,
            int slotY,
            int overlayType,
            String displayText,
            float baseScale) {
        if (textRenderer == null || displayText == null || displayText.isEmpty()) {
            return;
        }

        int textWidth = textRenderer.getWidth(displayText);

        if (textWidth <= 0) {
            return;
        }

        float fittedScale = Math.min(baseScale, 15.0F / textWidth);
        float textScale = Math.max(0.34F, fittedScale);
        int scaledTextWidth = Math.max(1, Math.round(textWidth * textScale));
        int scaledTextHeight = Math.max(1, Math.round(textRenderer.fontHeight * textScale));
        int textX = Math.max(slotX + 1, slotX + 16 - scaledTextWidth - 1);
        int textY = Math.max(slotY + 1, slotY + 16 - scaledTextHeight - 1);
        int textColor = overlayTextColor(overlayType);
        int backgroundColor = overlayBackgroundColor(overlayType);
        int backgroundLeft = Math.max(slotX, textX - 1);
        int backgroundTop = Math.max(slotY, textY - 1);
        int backgroundRight = Math.min(slotX + 16, textX + scaledTextWidth + 1);
        int backgroundBottom = Math.min(slotY + 16, textY + scaledTextHeight + 1);

        drawContext.enableScissor(slotX, slotY, slotX + 16, slotY + 16);
        drawContext.fill(
                backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, backgroundColor);
        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate(textX, textY);
        drawContext.getMatrices().scale(textScale, textScale);
        drawContext.drawTextWithShadow(textRenderer, displayText, 0, 0, textColor);
        drawContext.getMatrices().popMatrix();
        drawContext.disableScissor();
    }

    private int featureAccentColor(String featureId) {
        if (ClientFeatures.PEACEFUL_MINING_ID.equals(featureId)) {
            return 0x61C08D;
        }

        if (ClientFeatures.INVENTORY_ITEM_OVERLAYS_ID.equals(featureId)) {
            return 0x46A9FF;
        }

        if (ClientFeatures.HUD_COOLDOWNS_ID.equals(featureId)) {
            return 0x4EA7FF;
        }

        if (ClientFeatures.HUD_PETS_ID.equals(featureId)) {
            return 0x89D56B;
        }

        if (ClientFeatures.HUD_EVENTS_ID.equals(featureId)) {
            return 0xF1B95E;
        }

        if (ClientFeatures.HUD_SATCHEL_DISPLAY_ID.equals(featureId)) {
            return 0x6CE39B;
        }

        if (ClientFeatures.HUD_GANG_ID.equals(featureId)) {
            return 0xFF8E63;
        }

        if (ClientFeatures.HUD_LEADERBOARDS_ID.equals(featureId)) {
            return 0x7BA5FF;
        }

        if (ClientFeatures.PINGS_ID.equals(featureId)) {
            return 0xE5914A;
        }

        return 0x6C7FA0;
    }

    private int gridColumns() {
        int availableWidth = Math.max(CARD_WIDTH, width - 36);
        int columns = (availableWidth + CARD_GAP) / (CARD_WIDTH + CARD_GAP);
        return Math.max(1, Math.min(3, columns));
    }

    private CardBounds cardBounds(int index) {
        int columns = gridColumns();
        int row = index / columns;
        int column = index % columns;
        int gridWidth = (columns * CARD_WIDTH) + ((columns - 1) * CARD_GAP);
        int left = (width - gridWidth) / 2;
        int x = left + (column * (CARD_WIDTH + CARD_GAP));
        int y = GRID_TOP + (row * (CARD_HEIGHT + CARD_GAP));
        return new CardBounds(x, y, CARD_WIDTH, CARD_HEIGHT);
    }

    private PopupLayout popupLayout(float progress) {
        float eased = easeOutBack(progress);
        int targetWidth = Math.min(POPUP_BASE_WIDTH, width - 26);
        int targetHeight = Math.min(POPUP_BASE_HEIGHT, height - 28);
        float scale = 0.86F + (0.14F * eased);
        int popupWidth = Math.max(240, Math.round(targetWidth * scale));
        int popupHeight = Math.max(160, Math.round(targetHeight * scale));
        int x = (width - popupWidth) / 2;
        int y = ((height - popupHeight) / 2) + Math.round((1.0F - eased) * 12.0F);
        int closeWidth = 64;
        int closeHeight = 18;
        int closeX = x + popupWidth - closeWidth - 14;
        int closeY = y + popupHeight - closeHeight - 24;
        return new PopupLayout(
                x, y, popupWidth, popupHeight, closeX, closeY, closeWidth, closeHeight);
    }

    private static int overlayTextColor(int overlayType) {
        return switch (overlayType) {
            case ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY -> 0xFF5DE6FF;
            case ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE -> 0xFF84F08F;
            case ProtocolConstants.OVERLAY_TYPE_GANG_POINT_NOTE -> 0xFFFFC659;
            case ProtocolConstants.OVERLAY_TYPE_SATCHEL_PERCENT -> 0xFFB5E86C;
            case ProtocolConstants.OVERLAY_TYPE_PET_ACTIVE -> 0xFF8BF5C2;
            case ProtocolConstants.OVERLAY_TYPE_PET_COOLDOWN -> 0xFFFFB27D;
            default -> 0xFFE6E6E6;
        };
    }

    private static int overlayBackgroundColor(int overlayType) {
        return switch (overlayType) {
            case ProtocolConstants.OVERLAY_TYPE_COSMIC_ENERGY -> 0xA01E3F52;
            case ProtocolConstants.OVERLAY_TYPE_MONEY_NOTE -> 0xA01B3C25;
            case ProtocolConstants.OVERLAY_TYPE_GANG_POINT_NOTE -> 0xA04A361A;
            case ProtocolConstants.OVERLAY_TYPE_SATCHEL_PERCENT -> 0xA0284A1E;
            case ProtocolConstants.OVERLAY_TYPE_PET_ACTIVE -> 0xA01D4733;
            case ProtocolConstants.OVERLAY_TYPE_PET_COOLDOWN -> 0xA04E2A1B;
            default -> 0xA0333333;
        };
    }

    private void renderPopupActions(
            DrawContext drawContext,
            ClientFeatureDefinition feature,
            PopupLayout popupLayout,
            int accentColor,
            int buttonY) {
        popupActionButtons.clear();
        List<PopupActionDefinition> actions = popupActionsForFeature(feature.id());

        if (actions.isEmpty()) {
            return;
        }

        int gap = 6;
        int buttonHeight = 18;
        int availableWidth = popupLayout.width - 28;
        int buttonWidth = (availableWidth - (gap * (actions.size() - 1))) / actions.size();
        int startX = popupLayout.x + 14;
        int maxButtonY = popupLayout.closeY - 24;
        buttonY = Math.min(buttonY, maxButtonY);

        for (int index = 0; index < actions.size(); index++) {
            PopupActionDefinition actionDefinition = actions.get(index);
            int buttonX = startX + (index * (buttonWidth + gap));
            int bottom = buttonY + buttonHeight;
            drawContext.fill(
                    buttonX, buttonY, buttonX + buttonWidth, bottom, withAlpha(0x1A273C, 236));
            drawContext.fill(
                    buttonX,
                    buttonY,
                    buttonX + buttonWidth,
                    buttonY + 1,
                    withAlpha(accentColor, 255));
            drawContext.fill(
                    buttonX, bottom - 1, buttonX + buttonWidth, bottom, withAlpha(0x39516F, 255));
            drawContext.fill(buttonX, buttonY, buttonX + 1, bottom, withAlpha(0x39516F, 255));
            drawContext.fill(
                    buttonX + buttonWidth - 1,
                    buttonY,
                    buttonX + buttonWidth,
                    bottom,
                    withAlpha(0x39516F, 255));
            drawContext.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable(actionDefinition.labelTranslationKey()),
                    buttonX + (buttonWidth / 2),
                    buttonY + 5,
                    0xFFFFFFFF);

            popupActionButtons.add(
                    new PopupActionButton(
                            buttonX,
                            buttonY,
                            buttonWidth,
                            buttonHeight,
                            actionDefinition.action()));
        }
    }

    private List<PopupActionDefinition> popupActionsForFeature(String featureId) {
        if (ClientFeatures.HUD_EVENTS_ID.equals(featureId)) {
            return List.of(
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.display_modes",
                            () -> {
                                if (client != null) {
                                    client.setScreen(new HudEventVisibilityScreen(runtime, this));
                                }
                            }),
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.hud_layout",
                            this::openHudLayoutEditor));
        }

        if (ClientFeatures.HUD_COOLDOWNS_ID.equals(featureId)) {
            return List.of(
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.hud_layout",
                            this::openHudLayoutEditor));
        }

        if (ClientFeatures.HUD_PETS_ID.equals(featureId)) {
            return List.of(
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.display_modes",
                            () -> {
                                if (client != null) {
                                    client.setScreen(new HudPetsSettingsScreen(runtime, this));
                                }
                            }),
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.hud_layout",
                            this::openHudLayoutEditor));
        }

        if (ClientFeatures.HUD_SATCHEL_DISPLAY_ID.equals(featureId)) {
            return List.of(
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.display_modes",
                            () -> {
                                if (client != null) {
                                    client.setScreen(new HudEventVisibilityScreen(runtime, this));
                                }
                            }),
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.hud_layout",
                            this::openHudLayoutEditor));
        }

        if (ClientFeatures.HUD_GANG_ID.equals(featureId)) {
            return List.of(
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.hud_layout",
                            this::openHudLayoutEditor));
        }

        if (ClientFeatures.HUD_LEADERBOARDS_ID.equals(featureId)) {
            return List.of(
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.display_modes",
                            () -> {
                                if (client != null) {
                                    client.setScreen(
                                            new HudLeaderboardSettingsScreen(runtime, this));
                                }
                            }),
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.hud_layout",
                            this::openHudLayoutEditor));
        }

        if (ClientFeatures.PINGS_ID.equals(featureId)) {
            return List.of(
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.ping_keys",
                            () -> {
                                if (client != null) {
                                    client.setScreen(new PingKeybindSettingsScreen(runtime, this));
                                }
                            }),
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.ping_visuals",
                            () -> {
                                if (client != null) {
                                    client.setScreen(new PingVisualSettingsScreen(runtime, this));
                                }
                            }),
                    new PopupActionDefinition(
                            "text.cosmicprisonsmod.settings.popup.action.ping_reset",
                            runtime::resetPingKeybindsToDefault));
        }

        return List.of();
    }

    private void openHudLayoutEditor() {
        if (client != null) {
            client.setScreen(new HudLayoutEditorScreen(runtime, this));
        }
    }

    private static float easeOutBack(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float shifted = clamped - 1.0F;
        return 1.0F + (c3 * shifted * shifted * shifted) + (c1 * shifted * shifted);
    }

    private static int withAlpha(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    private static int popupExampleHeight(ClientFeatureDefinition feature) {
        if (ClientFeatures.PINGS_ID.equals(feature.id())
                || ClientFeatures.HUD_PETS_ID.equals(feature.id())) {
            return 44;
        }

        return 31;
    }

    private static boolean isPointWithin(
            double x, double y, int areaX, int areaY, int areaWidth, int areaHeight) {
        return x >= areaX && y >= areaY && x < (areaX + areaWidth) && y < (areaY + areaHeight);
    }

    private Text toggleButtonText(String featureId) {
        return runtime.isFeatureEnabled(featureId)
                ? Text.translatable("text.cosmicprisonsmod.settings.toggle.on")
                : Text.translatable("text.cosmicprisonsmod.settings.toggle.off");
    }

    private record CardBounds(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }
    }

    private record PopupLayout(
            int x,
            int y,
            int width,
            int height,
            int closeX,
            int closeY,
            int closeWidth,
            int closeHeight) {}

    private record PopupActionDefinition(String labelTranslationKey, Runnable action) {}

    private record PopupActionButton(int x, int y, int width, int height, Runnable action) {}
}
