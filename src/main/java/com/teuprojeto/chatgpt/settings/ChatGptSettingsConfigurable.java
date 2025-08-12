package com.teuprojeto.chatgpt.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class ChatGptSettingsConfigurable implements Configurable {

    private JPanel mainPanel;

    // API Key (fixo em 4 linhas)
    private JTextArea apiKeyArea;
    private JScrollPane apiScroll;
    private static final int API_FIXED_ROWS = 4;

    // Contexto
    private JCheckBox useCtx;
    private JSpinner maxChars;

    // System prompt (auto-resize)
    private JTextArea systemPromptArea;
    private JScrollPane systemPromptScroll;
    private static final int SP_MIN_ROWS = 3;
    private static final int SP_MAX_ROWS = 12;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "ChatGPT";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        // raiz
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // --- API Key (4 linhas fixas) ---
        JLabel apiLbl = new JLabel("OpenAI API Key:");
        apiLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        apiKeyArea = new JTextArea(API_FIXED_ROWS, 40);
        apiKeyArea.setLineWrap(true);
        apiKeyArea.setWrapStyleWord(true);

        apiScroll = new JScrollPane(apiKeyArea);
        apiScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        apiScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        apiScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        apiScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // fixa a altura do SCROLL para 4 linhas
        SwingUtilities.invokeLater(() -> fixScrollHeightToRows(apiKeyArea, apiScroll, API_FIXED_ROWS));

        // auto-scroll para o fim apenas se o caret já estava no fim
        apiKeyArea.getDocument().addDocumentListener(new DocumentListener() {
            private boolean atEndBefore;

            @Override public void insertUpdate(DocumentEvent e) { maybeScrollToEnd(); }
            @Override public void removeUpdate(DocumentEvent e) { /* não auto-scroll em remoção */ }
            @Override public void changedUpdate(DocumentEvent e) { }

            private void maybeScrollToEnd() {
                int len = apiKeyArea.getDocument().getLength();
                // se o caret estava no fim antes da edição, mantemos no fim e forçamos scroll
                if (atEndBefore) {
                    apiKeyArea.setCaretPosition(len);
                    SwingUtilities.invokeLater(() -> {
                        JScrollBar bar = apiScroll.getVerticalScrollBar();
                        if (bar != null) bar.setValue(bar.getMaximum());
                    });
                }
            }

            // Captura o estado "estava no fim?" antes de cada input do utilizador
            {
                apiKeyArea.addCaretListener(ev -> {
                    int len = apiKeyArea.getDocument().getLength();
                    atEndBefore = (ev.getDot() == len);
                });
            }
        });

        // --- Contexto ---
        useCtx = new JCheckBox("Usar contexto (histórico) nas perguntas");
        useCtx.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel maxCharsLbl = new JLabel("Limite de caracteres do contexto:");
        maxCharsLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        maxChars = new JSpinner(new SpinnerNumberModel(8000, 1000, 200000, 500));
        maxChars.setAlignmentX(Component.LEFT_ALIGNMENT);

        // --- System prompt (auto-resize) ---
        JLabel spLbl = new JLabel("System prompt:");
        spLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        systemPromptArea = aTextArea(SP_MIN_ROWS, 50);
        systemPromptScroll = new JScrollPane(systemPromptArea);
        systemPromptScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        systemPromptScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        systemPromptScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        systemPromptScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // carregar valores atuais
        ChatGptSettingsState s = ChatGptSettingsState.getInstance();
        apiKeyArea.setText(s.getApiKey());
        useCtx.setSelected(s.isUseContext());
        maxChars.setValue(s.getMaxContextChars());
        systemPromptArea.setText(s.getSystemPrompt());

        // auto-resize do System prompt (API Key fica fixa)
        autoResize(systemPromptArea, systemPromptScroll, SP_MIN_ROWS, SP_MAX_ROWS);
        systemPromptArea.getDocument().addDocumentListener(new SimpleDocListener(() ->
                autoResize(systemPromptArea, systemPromptScroll, SP_MIN_ROWS, SP_MAX_ROWS)));

        // layout final
        mainPanel.add(apiLbl);
        mainPanel.add(apiScroll);
        mainPanel.add(Box.createVerticalStrut(10));

        mainPanel.add(useCtx);
        mainPanel.add(Box.createVerticalStrut(6));

        mainPanel.add(maxCharsLbl);
        mainPanel.add(maxChars);
        mainPanel.add(Box.createVerticalStrut(6));

        mainPanel.add(spLbl);
        mainPanel.add(systemPromptScroll);

        return mainPanel;
    }

    // fixa a altura do JScrollPane para N linhas da JTextArea
    private static void fixScrollHeightToRows(JTextArea ta, JScrollPane sp, int rows) {
        FontMetrics fm = ta.getFontMetrics(ta.getFont());
        int lineH = fm != null ? fm.getHeight() : ta.getFont().getSize() + 6;
        Insets in = ta.getInsets();
        int innerPad = (in != null ? in.top + in.bottom : 0);
        int targetH = rows * lineH + innerPad + 8; // +8 para a borda/padding do scroll
        Dimension pref = sp.getPreferredSize();
        sp.setPreferredSize(new Dimension(pref.width, targetH));
        sp.setMinimumSize(new Dimension(0, targetH));
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, targetH));
        sp.revalidate();
        sp.repaint();
    }

    // helper para criar JTextArea com wrap
    private static JTextArea aTextArea(int rows, int cols) {
        JTextArea ta = new JTextArea(rows, cols);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        return ta;
    }

    // auto-resize genérico (System prompt)
    private static void autoResize(JTextArea area, JScrollPane scroll, int minRows, int maxRows) {
        int lines;
        try {
            lines = area.getLineOfOffset(area.getDocument().getLength()) + 1;
        } catch (Exception ignore) {
            lines = minRows;
        }
        lines = Math.max(minRows, Math.min(maxRows, lines));

        FontMetrics fm = area.getFontMetrics(area.getFont());
        int lineH = fm != null ? fm.getHeight() : area.getFont().getSize() + 6;
        Insets in = area.getInsets();
        int innerPad = (in != null ? in.top + in.bottom : 0);
        int targetH = lines * lineH + innerPad;

        Dimension pref = area.getPreferredSize();
        area.setPreferredSize(new Dimension(pref.width, targetH));
        area.revalidate();
        scroll.revalidate();
        scroll.repaint();

        SwingUtilities.invokeLater(() -> {
            int caret = area.getCaretPosition();
            try {
                Rectangle r = area.modelToView2D(caret).getBounds();
                area.scrollRectToVisible(r);
            } catch (Exception ignore) { }
        });
    }

    // doc listener simples
    private static class SimpleDocListener implements DocumentListener {
        private final Runnable r;
        private SimpleDocListener(Runnable r) { this.r = r; }
        @Override public void insertUpdate(DocumentEvent e) { r.run(); }
        @Override public void removeUpdate(DocumentEvent e) { r.run(); }
        @Override public void changedUpdate(DocumentEvent e) { r.run(); }
    }

    @Override
    public boolean isModified() {
        ChatGptSettingsState s = ChatGptSettingsState.getInstance();
        if (!apiKeyArea.getText().equals(s.getApiKey())) return true;
        if (useCtx.isSelected() != s.isUseContext()) return true;
        if (!maxChars.getValue().equals(s.getMaxContextChars())) return true;
        return !systemPromptArea.getText().equals(s.getSystemPrompt());
    }

    @Override
    public void apply() {
        ChatGptSettingsState s = ChatGptSettingsState.getInstance();
        s.setApiKey(apiKeyArea.getText().trim());
        s.setUseContext(useCtx.isSelected());
        s.setMaxContextChars((Integer) maxChars.getValue());
        s.setSystemPrompt(systemPromptArea.getText());
    }

    @Override
    public void reset() {
        ChatGptSettingsState s = ChatGptSettingsState.getInstance();
        apiKeyArea.setText(s.getApiKey());
        useCtx.setSelected(s.isUseContext());
        maxChars.setValue(s.getMaxContextChars());
        systemPromptArea.setText(s.getSystemPrompt());

        SwingUtilities.invokeLater(() -> {
            fixScrollHeightToRows(apiKeyArea, apiScroll, API_FIXED_ROWS);
            autoResize(systemPromptArea, systemPromptScroll, SP_MIN_ROWS, SP_MAX_ROWS);
        });
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        apiKeyArea = null;
        apiScroll = null;
        useCtx = null;
        maxChars = null;
        systemPromptArea = null;
        systemPromptScroll = null;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return apiKeyArea;
    }
}
