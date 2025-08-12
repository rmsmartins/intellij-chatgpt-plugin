package com.teuprojeto.chatgpt.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class ChatGptSettingsConfigurable implements Configurable {

    private JPanel mainPanel;
    private JTextArea apiKeyField;
    private JCheckBox useCtx;
    private JSpinner maxChars;
    private JTextArea systemPromptArea;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() { return "ChatGPT"; }

    @Nullable
    @Override
    public JComponent createComponent() {
        // Painel raiz
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(JBUI.Borders.empty(10, 10, 10, 10));

        // ===== API KEY =====
        JLabel apiLbl = new JLabel("OpenAI API Key:");
        alignLeft(apiLbl);

        apiKeyField = new JTextArea(4, 50); // 4 linhas + scroll
        apiKeyField.setLineWrap(true);
        apiKeyField.setWrapStyleWord(true);
        Border apiBorder = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(4, 8, 4, 8)
        );
        apiKeyField.setBorder(apiBorder);

        JScrollPane apiScroll = new JScrollPane(apiKeyField,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        fillWidth(apiScroll, apiScroll.getPreferredSize().height);

        mainPanel.add(apiLbl);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(apiScroll);
        mainPanel.add(Box.createVerticalStrut(12));

        // ===== CONTEXTO =====
        useCtx = new JCheckBox("Usar contexto (histórico) nas perguntas");
        alignLeft(useCtx);

        JLabel maxCharsLbl = new JLabel("Limite de caracteres do contexto:");
        maxChars = new JSpinner(new SpinnerNumberModel(8000, 1000, 200000, 500));

        JPanel ctxRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        ctxRow.add(maxCharsLbl);
        ctxRow.add(maxChars);
        fillWidth(ctxRow, ctxRow.getPreferredSize().height);

        mainPanel.add(useCtx);
        mainPanel.add(Box.createVerticalStrut(6));
        mainPanel.add(ctxRow);
        mainPanel.add(Box.createVerticalStrut(12));

        // ===== SYSTEM PROMPT =====
        JLabel spLbl = new JLabel("System prompt:");
        alignLeft(spLbl);

        systemPromptArea = new JTextArea(3, 50);
        systemPromptArea.setLineWrap(true);
        systemPromptArea.setWrapStyleWord(true);
        systemPromptArea.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(4, 8, 4, 8)
        ));

        JScrollPane spScroll = new JScrollPane(systemPromptArea,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        fillWidth(spScroll, spScroll.getPreferredSize().height);

        mainPanel.add(spLbl);
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(spScroll);

        // Carregar estado
        ChatGptSettingsState s = ChatGptSettingsState.getInstance();
        apiKeyField.setText(s.getApiKey());
        useCtx.setSelected(s.isUseContext());
        maxChars.setValue(s.getMaxContextChars());
        systemPromptArea.setText(s.getSystemPrompt());

        return mainPanel;
    }

    // === Helpers de layout ===
    private static void alignLeft(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        // evitar “apertar”: deixa a largura crescer
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
    }

    private static void fillWidth(JComponent c, int fixedHeight) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, fixedHeight));
        c.setPreferredSize(new Dimension(c.getPreferredSize().width, fixedHeight));
    }

    // === Ciclo de vida ===
    @Override
    public boolean isModified() {
        ChatGptSettingsState s = ChatGptSettingsState.getInstance();
        if (!apiKeyField.getText().equals(s.getApiKey())) return true;
        if (useCtx.isSelected() != s.isUseContext()) return true;
        if (!maxChars.getValue().equals(s.getMaxContextChars())) return true;
        return !systemPromptArea.getText().equals(s.getSystemPrompt());
    }

    @Override
    public void apply() {
        ChatGptSettingsState s = ChatGptSettingsState.getInstance();
        s.setApiKey(apiKeyField.getText());
        s.setUseContext(useCtx.isSelected());
        s.setMaxContextChars((Integer) maxChars.getValue());
        s.setSystemPrompt(systemPromptArea.getText());
    }

    @Override
    public void reset() {
        ChatGptSettingsState s = ChatGptSettingsState.getInstance();
        apiKeyField.setText(s.getApiKey());
        useCtx.setSelected(s.isUseContext());
        maxChars.setValue(s.getMaxContextChars());
        systemPromptArea.setText(s.getSystemPrompt());
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        apiKeyField = null;
        useCtx = null;
        maxChars = null;
        systemPromptArea = null;
    }
}
