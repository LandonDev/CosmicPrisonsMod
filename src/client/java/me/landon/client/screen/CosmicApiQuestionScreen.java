package me.landon.client.screen;

import java.util.ArrayList;
import java.util.List;
import me.landon.client.runtime.CompanionClientRuntime;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class CosmicApiQuestionScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_MIN_HEIGHT = 178;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 116;
    private static final int BUTTON_GAP = 10;
    private static final int MAX_ANSWER_CHARS_FALLBACK = 2048;

    private final CompanionClientRuntime runtime;
    private final Screen parent;
    private final int questionId;
    private final String questionText;
    private final int maxAnswerChars;
    private final int timeoutSeconds;

    private TextFieldWidget answerField;
    private boolean submitted;

    public CosmicApiQuestionScreen(
            CompanionClientRuntime runtime,
            Screen parent,
            int questionId,
            String questionText,
            int maxAnswerChars,
            int timeoutSeconds) {
        super(Text.literal("Server Prompt"));
        this.runtime = runtime;
        this.parent = parent;
        this.questionId = questionId;
        this.questionText =
                questionText == null || questionText.isBlank() ? "Enter a response." : questionText;
        this.maxAnswerChars = maxAnswerChars <= 0 ? MAX_ANSWER_CHARS_FALLBACK : maxAnswerChars;
        this.timeoutSeconds = Math.max(0, timeoutSeconds);
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = panelWidth();
        int panelX = (width - panelWidth) / 2;
        int panelY = panelY();
        int fieldY = panelY + panelHeight() - 70;
        int fieldX = panelX + 18;
        int fieldWidth = panelWidth - 36;

        answerField =
                addDrawableChild(
                        new TextFieldWidget(
                                textRenderer,
                                fieldX,
                                fieldY,
                                fieldWidth,
                                FIELD_HEIGHT,
                                Text.empty()));
        answerField.setMaxLength(maxAnswerChars);
        answerField.setFocused(true);

        int buttonsY = fieldY + FIELD_HEIGHT + 16;
        int buttonStartX = panelX + ((panelWidth - ((BUTTON_WIDTH * 2) + BUTTON_GAP)) / 2);
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Submit"), button -> submit(false))
                        .dimensions(buttonStartX, buttonsY, BUTTON_WIDTH, 20)
                        .build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Cancel"), button -> submit(true))
                        .dimensions(
                                buttonStartX + BUTTON_WIDTH + BUTTON_GAP,
                                buttonsY,
                                BUTTON_WIDTH,
                                20)
                        .build());
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.getKeycode() == GLFW.GLFW_KEY_ENTER) {
            submit(false);
            return true;
        }
        if (keyInput.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            submit(true);
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public void close() {
        submit(true);
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks) {
        drawContext.fill(0, 0, width, height, 0xC40B111B);

        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = panelY();
        int panelBottom = panelY + panelHeight;

        drawContext.fill(panelX, panelY, panelX + panelWidth, panelBottom, 0xEA111D2D);
        drawContext.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFFDAA53A);
        drawContext.fill(panelX, panelBottom - 1, panelX + panelWidth, panelBottom, 0xFF314760);
        drawContext.fill(panelX, panelY, panelX + 1, panelBottom, 0xFF314760);
        drawContext.fill(
                panelX + panelWidth - 1, panelY, panelX + panelWidth, panelBottom, 0xFF314760);

        drawContext.drawCenteredTextWithShadow(
                textRenderer, title, width / 2, panelY + 12, 0xFFFFD77B);
        drawContext.drawTextWithShadow(textRenderer, "* ", panelX + 18, panelY + 34, 0xFFFFC857);
        drawContext.drawTextWithShadow(
                textRenderer,
                "The server is asking for a response.",
                panelX + 30,
                panelY + 34,
                0xFFE8F0FA);

        int textY = panelY + 55;
        for (String line : wrappedQuestionLines(panelWidth - 36)) {
            drawContext.drawTextWithShadow(textRenderer, line, panelX + 18, textY, 0xFFD7E3F3);
            textY += 11;
        }

        int metaY = panelY + panelHeight - 90;
        String limitLine = "Maximum characters: " + maxAnswerChars;
        if (timeoutSeconds > 0) {
            limitLine += "  *  Timeout: " + timeoutSeconds + "s";
        }
        drawContext.drawTextWithShadow(textRenderer, limitLine, panelX + 18, metaY, 0xFF9FB4CC);

        super.render(drawContext, mouseX, mouseY, deltaTicks);
    }

    private void submit(boolean cancelled) {
        if (submitted) {
            return;
        }
        submitted = true;
        String answer = cancelled || answerField == null ? "" : answerField.getText();
        runtime.submitCosmicApiQuestionAnswer(questionId, answer, cancelled);
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private List<String> wrappedQuestionLines(int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = questionText.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textRenderer.getWidth(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (textRenderer.getWidth(word) <= maxWidth) {
                current.append(word);
            } else {
                lines.add(word);
            }
            if (lines.size() >= 5) {
                break;
            }
        }
        if (!current.isEmpty() && lines.size() < 5) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add("Enter a response.");
        }
        return lines;
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, width - 28);
    }

    private int panelHeight() {
        return Math.max(PANEL_MIN_HEIGHT, Math.min(230, height - 36));
    }

    private int panelY() {
        return Math.max(18, (height - panelHeight()) / 2);
    }
}
