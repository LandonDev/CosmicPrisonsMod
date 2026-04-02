package me.landon.client.screen;

import me.landon.client.runtime.CompanionClientRuntime;
import me.landon.companion.config.CompanionConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class PingVisualSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 28;
    private static final int LIST_TOP = 56;

    private final CompanionClientRuntime runtime;
    private final Screen parent;

    private ButtonWidget durationDecreaseButton;
    private ButtonWidget durationIncreaseButton;
    private ButtonWidget modeToggleButton;
    private ButtonWidget particlesToggleButton;

    public PingVisualSettingsScreen(CompanionClientRuntime runtime, Screen parent) {
        super(Text.translatable("text.cosmicprisonsmod.ping_visuals.title"));
        this.runtime = runtime;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int rowWidth = Math.min(430, width - 32);
        int rowX = (width - rowWidth) / 2;

        durationDecreaseButton =
                addDrawableChild(
                        ButtonWidget.builder(Text.literal("-"), button -> decreaseDuration())
                                .dimensions(rowX + rowWidth - 130, LIST_TOP + 4, 20, 20)
                                .build());
        durationIncreaseButton =
                addDrawableChild(
                        ButtonWidget.builder(Text.literal("+"), button -> increaseDuration())
                                .dimensions(rowX + rowWidth - 24, LIST_TOP + 4, 20, 20)
                                .build());

        particlesToggleButton =
                addDrawableChild(
                        ButtonWidget.builder(Text.empty(), button -> toggleParticles())
                                .dimensions(
                                        rowX + rowWidth - 130,
                                        LIST_TOP + (ROW_HEIGHT * 2) + 4,
                                        126,
                                        20)
                                .build());

        modeToggleButton =
                addDrawableChild(
                        ButtonWidget.builder(Text.empty(), button -> toggleMode())
                                .dimensions(
                                        rowX + rowWidth - 130, LIST_TOP + ROW_HEIGHT + 4, 126, 20)
                                .build());

        int footerY = LIST_TOP + (ROW_HEIGHT * 3) + 20;
        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable(
                                        "text.cosmicprisonsmod.ping_visuals.reset_button"),
                                button -> {
                                    runtime.resetPingVisualSettingsToDefault();
                                    refreshControls();
                                })
                        .dimensions((width / 2) - 124, footerY, 120, 20)
                        .build());
        addDrawableChild(
                ButtonWidget.builder(
                                Text.translatable("text.cosmicprisonsmod.settings.button.done"),
                                button -> close())
                        .dimensions((width / 2) + 4, footerY, 120, 20)
                        .build());

        refreshControls();
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(keyInput);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks) {
        drawContext.fill(0, 0, width, height, 0xBF0E131D);
        drawContext.fill(0, 0, width, 28, 0x6B273D5C);
        drawContext.drawCenteredTextWithShadow(textRenderer, title, width / 2, 9, 0xFFFFFFFF);
        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.ping_visuals.subtitle"),
                width / 2,
                33,
                0xFFB8C7DA);

        renderRows(drawContext);

        drawContext.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("text.cosmicprisonsmod.ping_visuals.duration_bounds"),
                width / 2,
                LIST_TOP + (ROW_HEIGHT * 3) + 4,
                0xFFC6D4E8);

        super.render(drawContext, mouseX, mouseY, deltaTicks);
    }

    private void renderRows(DrawContext drawContext) {
        int rowWidth = Math.min(430, width - 32);
        int rowX = (width - rowWidth) / 2;

        renderRow(
                drawContext,
                rowX,
                LIST_TOP,
                rowWidth,
                Text.translatable("text.cosmicprisonsmod.ping_visuals.duration_label"),
                0xFF406D97);
        renderRow(
                drawContext,
                rowX,
                LIST_TOP + ROW_HEIGHT,
                rowWidth,
                Text.translatable("text.cosmicprisonsmod.ping_visuals.mode_label"),
                0xFF4A7A5F);
        renderRow(
                drawContext,
                rowX,
                LIST_TOP + (ROW_HEIGHT * 2),
                rowWidth,
                Text.translatable("text.cosmicprisonsmod.ping_visuals.particles_label"),
                0xFF9A693A);

        int durationSeconds = runtime.pingVisualDurationSeconds();
        Text valueText =
                Text.translatable(
                        "text.cosmicprisonsmod.ping_visuals.duration_value", durationSeconds);
        int valueX = rowX + rowWidth - 92;
        drawContext.drawCenteredTextWithShadow(
                textRenderer, valueText, valueX, LIST_TOP + 10, 0xFFE6EEF8);
    }

    private void renderRow(
            DrawContext drawContext, int x, int y, int width, Text label, int accentColor) {
        int bottom = y + ROW_HEIGHT - 2;
        drawContext.fill(x, y, x + width, bottom, 0xB4131E30);
        drawContext.fill(x, y, x + width, y + 1, accentColor);
        drawContext.fill(x, bottom - 1, x + width, bottom, 0xFF25364D);
        drawContext.drawTextWithShadow(textRenderer, label, x + 9, y + 9, 0xFFE6EEF8);
    }

    private void decreaseDuration() {
        runtime.setPingVisualDurationSeconds(runtime.pingVisualDurationSeconds() - 1);
        refreshControls();
    }

    private void increaseDuration() {
        runtime.setPingVisualDurationSeconds(runtime.pingVisualDurationSeconds() + 1);
        refreshControls();
    }

    private void toggleParticles() {
        runtime.setPingParticlesEnabled(!runtime.pingParticlesEnabled());
        refreshControls();
    }

    private void toggleMode() {
        runtime.setPingVisualMode(
                runtime.pingVisualModeStatic()
                        ? CompanionConfig.PING_VISUAL_MODE_FOLLOW
                        : CompanionConfig.PING_VISUAL_MODE_STATIC);
        refreshControls();
    }

    private void refreshControls() {
        if (durationDecreaseButton == null
                || durationIncreaseButton == null
                || modeToggleButton == null
                || particlesToggleButton == null) {
            return;
        }

        int currentDuration = runtime.pingVisualDurationSeconds();
        durationDecreaseButton.active =
                currentDuration > CompanionConfig.PING_VISUAL_DURATION_SECONDS_MIN;
        durationIncreaseButton.active =
                currentDuration < CompanionConfig.PING_VISUAL_DURATION_SECONDS_MAX;
        modeToggleButton.setMessage(
                runtime.pingVisualModeStatic()
                        ? Text.translatable("text.cosmicprisonsmod.ping_visuals.mode_static")
                        : Text.translatable("text.cosmicprisonsmod.ping_visuals.mode_follow"));
        particlesToggleButton.setMessage(
                runtime.pingParticlesEnabled()
                        ? Text.translatable("text.cosmicprisonsmod.ping_visuals.particles_on")
                        : Text.translatable("text.cosmicprisonsmod.ping_visuals.particles_off"));
    }
}
