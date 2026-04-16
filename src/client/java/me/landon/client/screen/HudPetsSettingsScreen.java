package me.landon.client.screen;

import me.landon.client.runtime.CompanionClientRuntime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class HudPetsSettingsScreen extends Screen {
    private static final int PANEL_TOP = 50;
    private static final int PANEL_BOTTOM_MARGIN = 24;
    private static final int PANEL_HORIZONTAL_MARGIN = 22;

    private final CompanionClientRuntime runtime;
    private final Screen parent;

    private boolean itemTimerEnabled;

    public HudPetsSettingsScreen(CompanionClientRuntime runtime, Screen parent) {
        super(Text.translatable("text.cosmicprisonsmod.pets_filter.title"));
        this.runtime = runtime;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        itemTimerEnabled = runtime.isPetItemTimerEnabled();

        int panelX = panelX();
        int panelWidth = panelWidth();
        int buttonWidth = 200;
        int buttonX = panelX + ((panelWidth - buttonWidth) / 2);

        addDrawableChild(
                ButtonWidget.builder(timerButtonText(), button -> toggleTimer(button))
                        .dimensions(buttonX, PANEL_TOP + 40, buttonWidth, 20)
                        .build());

        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.settings.button.done"),
                                button -> close())
                        .dimensions((width / 2) - 74, doneButtonY(), 148, 20)
                        .build());
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks) {
        drawContext.fill(0, 0, width, height, 0xC40C1421);
        drawContext.fill(0, 0, width, 32, 0x7F26405E);

        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, 11, 0xFFFFFFFF);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.pets_filter.subtitle"),
                width / 2,
                36,
                0xFFD1DEEF);

        renderPanel(drawContext);
        super.render(drawContext, mouseX, mouseY, deltaTicks);
    }

    private void renderPanel(DrawContext drawContext) {
        int panelX = panelX();
        int panelWidth = panelWidth();
        int panelBottom = panelBottom();

        drawContext.fill(panelX, PANEL_TOP, panelX + panelWidth, panelBottom, 0xC1101A2B);
        drawContext.fill(panelX, PANEL_TOP, panelX + panelWidth, PANEL_TOP + 1, 0xFF385478);
        drawContext.fill(panelX, panelBottom - 1, panelX + panelWidth, panelBottom, 0xFF2A3E5A);
        drawContext.fill(panelX, PANEL_TOP, panelX + 1, panelBottom, 0xFF2A3E5A);
        drawContext.fill(
                panelX + panelWidth - 1, PANEL_TOP, panelX + panelWidth, panelBottom, 0xFF2A3E5A);

        drawContext.drawTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.pets_filter.timer.label"),
                panelX + 16,
                PANEL_TOP + 20,
                0xFFD6E6F8);
    }

    private void toggleTimer(ButtonWidget button) {
        itemTimerEnabled = !itemTimerEnabled;
        runtime.setPetItemTimerEnabled(itemTimerEnabled);
        button.setMessage(timerButtonText());
    }

    private Text timerButtonText() {
        return Text.translatable(
                "text.cosmicprisonsmod.pets_filter.timer.button",
                itemTimerEnabled
                        ? Text.translatable("text.cosmicprisonsmod.settings.toggle.on")
                        : Text.translatable("text.cosmicprisonsmod.settings.toggle.off"));
    }

    private int panelWidth() {
        return Math.min(720, width - (PANEL_HORIZONTAL_MARGIN * 2));
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelBottom() {
        return height - PANEL_BOTTOM_MARGIN;
    }

    private int doneButtonY() {
        return panelBottom() - 30;
    }
}
