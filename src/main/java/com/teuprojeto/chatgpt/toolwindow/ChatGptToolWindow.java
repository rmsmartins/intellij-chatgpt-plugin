package com.teuprojeto.chatgpt.toolwindow;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import com.teuprojeto.chatgpt.core.OpenAiHttp;
import com.teuprojeto.chatgpt.settings.ChatGptSettingsState;
import com.teuprojeto.chatgpt.settings.ChatGptSettingsState.Message;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class ChatGptToolWindow extends SimpleToolWindowPanel {

    private final Project project;

    private final JBTextArea conversationArea = new JBTextArea();
    private final JBTextArea promptField = new JBTextArea(); // input multi-linha
    private final JComboBox<String> modelBox = new JComboBox<>(new String[]{"gpt-4o-mini", "gpt-4o"});
    private final JBCheckBox streamCheck = new JBCheckBox("Streaming", true);

    // holders para auto-resize
    private JBScrollPane inputScroll;
    private JBPanel<?> inputWrapper;

    // limites (px) — ajustáveis
    private final int minInputHeight = JBUI.scale(70);   // ~3 linhas confortáveis
    private final int maxInputHeight = JBUI.scale(200);  // ~10–12 linhas

    public ChatGptToolWindow(Project project) {
        super(true, true);
        this.project = project;

        // Área de conversa
        conversationArea.setEditable(false);
        conversationArea.setLineWrap(true);
        conversationArea.setWrapStyleWord(true);

        // INPUT multi-linha com wrap
        promptField.setLineWrap(true);
        promptField.setWrapStyleWord(true);
        promptField.setRows(3);
        promptField.getEmptyText().setText("Escreve a tua pergunta...");
        promptField.setFont(conversationArea.getFont());
        // margem interna no textarea
        promptField.setBorder(JBUI.Borders.empty(6, 8, 8, 8));

        // Scroll do input (sem borda; borda vai no wrapper)
        inputScroll = new JBScrollPane(promptField);
        inputScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        inputScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER); // ligamos só se passar do máximo
        inputScroll.setBorder(JBUI.Borders.empty());

        // Wrapper com contorno + padding (garante borda visível)
        inputWrapper = new JBPanel<>(new BorderLayout());
        inputWrapper.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.blue, 1), // contorno azul
                JBUI.Borders.empty(6)                     // padding interno
        ));
        inputWrapper.add(inputScroll, BorderLayout.CENTER);

        // altura inicial e máximos
        inputWrapper.setPreferredSize(new Dimension(Integer.MAX_VALUE, minInputHeight));
        inputWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxInputHeight));

        JButton sendBtn = new JButton("Send");
        JButton clearBtn = new JButton("Limpar");
        clearBtn.addActionListener(e -> {
            conversationArea.setText("");
            ChatGptSettingsState.getInstance().clearHistory(); // limpa persistido
        });

        // linha 2: model + streaming + botões
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(new JBLabel("Model:"));
        controls.add(modelBox);
        controls.add(streamCheck);
        controls.add(sendBtn);
        controls.add(clearBtn);
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);

        // topo em 2 linhas (input em cima, controlos em baixo)
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(inputWrapper);
        top.add(Box.createVerticalStrut(6));
        top.add(controls);

        JBScrollPane scroll = new JBScrollPane(conversationArea);

        JPanel content = new JBPanel<>(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(top, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);

        setContent(content);

        // Enviar + manter foco
        sendBtn.addActionListener(e -> {
            send();
            ApplicationManager.getApplication().invokeLater(() -> promptField.requestFocusInWindow());
        });

        // Ctrl+Enter => enviar (Enter normal quebra linha no JTextArea)
        promptField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK), "send");
        promptField.getActionMap().put("send", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                send();
                ApplicationManager.getApplication().invokeLater(() -> promptField.requestFocusInWindow());
            }
        });

        // Auto-resize quando o utilizador escreve
        promptField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { autoResizeInput(); }
            @Override public void removeUpdate(DocumentEvent e) { autoResizeInput(); }
            @Override public void changedUpdate(DocumentEvent e) { autoResizeInput(); }
        });

        // Auto-resize quando a largura muda (ex.: resize da tool window)
        inputWrapper.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { autoResizeInput(); }
            @Override public void componentShown(ComponentEvent e)    { autoResizeInput(); }
        });

        // Carregar histórico persistente e focar input
        ApplicationManager.getApplication().invokeLater(() -> {
            renderPersistedHistory();
            autoResizeInput();
            promptField.requestFocusInWindow();
        });
    }

    private void renderPersistedHistory() {
        List<Message> history = ChatGptSettingsState.getInstance().getHistory();
        if (history.isEmpty()) return;

        DateTimeFormatter fmtTime = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
        conversationArea.setText("");
        for (Message m : history) {
            String ts = fmtTime.format(Instant.ofEpochMilli(m.timestamp));
            if ("user".equalsIgnoreCase(m.role)) {
                conversationArea.append("You (" + ts + "): " + m.text + "\n");
                conversationArea.append("Assistant: ");
            } else {
                conversationArea.append(m.text + "\n\n");
            }
        }
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
    }

    private void send() {
        String apiKey = ChatGptSettingsState.getInstance().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            notifyUi("Define a API key em Settings → Tools → ChatGPT.", NotificationType.WARNING);
            return;
        }
        String prompt = promptField.getText().trim();
        if (prompt.isEmpty()) return;

        // Respostas locais: data/hora (evita respostas desatualizadas da API)
        String p = prompt.toLowerCase().trim();
        if (p.matches(".*\\b(que dia é hoje|que dia e hoje|data de hoje|hoje que dia|data atual)\\b.*")) {
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            java.time.LocalDate d = java.time.LocalDate.now(zone);
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' uuuu", new java.util.Locale("pt","PT"));
            appendUser(prompt);
            appendAssistant(d.format(fmt));
            promptField.setText("");
            autoResizeInput();
            return;
        }
        if (p.matches(".*\\b(que horas são|que horas sao|hora atual|agora que horas)\\b.*")) {
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            java.time.LocalTime t = java.time.LocalTime.now(zone).withSecond(0).withNano(0);
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm", new java.util.Locale("pt","PT"));
            appendUser(prompt);
            appendAssistant(t.format(fmt));
            promptField.setText("");
            autoResizeInput();
            return;
        }

        String model = (String) modelBox.getSelectedItem();
        boolean streaming = streamCheck.isSelected();

        appendUser(prompt);
        promptField.setText("");
        autoResizeInput();

        List<ChatGptSettingsState.Message> persisted = ChatGptSettingsState.getInstance().getHistory();
        List<OpenAiHttp.HistoryMsg> ctx = persisted.stream()
                .map(m -> new OpenAiHttp.HistoryMsg(
                        "assistant".equalsIgnoreCase(m.role) ? "assistant" : "user",
                        m.text == null ? "" : m.text))
                .collect(Collectors.toList());


        if (streaming) {
            StringBuilder acc = new StringBuilder();
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    OpenAiHttp.chatStreamWithHistory(
                            apiKey, model, ctx, prompt, 0.7,
                            (java.util.function.Consumer<String>) (delta ->
                                    ApplicationManager.getApplication().invokeLater(() -> appendAssistantDelta(acc, delta))
                            ),
                            (Runnable) () ->
                                    ApplicationManager.getApplication().invokeLater(() -> appendAssistantDone(acc))
                    );
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            notifyUi("Erro: " + ex.getMessage(), NotificationType.ERROR));
                }
            });
        } else {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    String answer = OpenAiHttp.chatWithHistory(apiKey, model, ctx, prompt, 0.7);
                    ApplicationManager.getApplication().invokeLater(() -> appendAssistant(answer));
                } catch (Exception ex) {
                    ApplicationManager.getApplication().invokeLater(() ->
                            notifyUi("Erro: " + ex.getMessage(), NotificationType.ERROR));
                }
            });
        }
    }

    private void appendUser(String text) {
        // Persistir + render
        ChatGptSettingsState.getInstance().addUser(text);
        conversationArea.append("You: " + text + "\n");
        conversationArea.append("Assistant: ");
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
    }

    private void appendAssistant(String text) {
        ChatGptSettingsState.getInstance().addAssistant(text);
        conversationArea.append(text + "\n\n");
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
    }

    private void appendAssistantDelta(StringBuilder acc, String delta) {
        acc.append(delta);
        conversationArea.append(delta);
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
    }

    private void appendAssistantDone(StringBuilder acc) {
        // fecha a mensagem stream: persistir inteira
        ChatGptSettingsState.getInstance().addAssistant(acc.toString());
        conversationArea.append("\n\n");
        conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
    }

    private void autoResizeInput() {
        int availWidth = inputWrapper.getWidth() > 0
                ? inputWrapper.getWidth() - JBUI.scale(12) // empty(6) de cada lado
                : inputWrapper.getPreferredSize().width;
        if (availWidth <= 0) return;

        // guarda posição atual do cursor para não o mexer
        final int caretPos = promptField.getCaretPosition();

        // mede altura preferida com a largura disponível
        Dimension oldSize = promptField.getSize();
        promptField.setSize(new Dimension(availWidth, Short.MAX_VALUE));
        Dimension pref = promptField.getPreferredSize();
        promptField.setSize(oldSize);

        Insets s = inputScroll.getInsets();
        Insets v = inputScroll.getViewport().getInsets();
        Insets t = promptField.getInsets();
        int extra = (s != null ? s.top + s.bottom : 0)
                + (v != null ? v.top + v.bottom : 0)
                + (t != null ? t.top + t.bottom : 0);

        int fudge = JBUI.scale(8);
        int desired = pref.height + extra + fudge;

        int target = Math.max(minInputHeight, Math.min(desired, maxInputHeight));
        boolean needsScroll = desired > maxInputHeight;

        inputWrapper.setPreferredSize(new Dimension(Integer.MAX_VALUE, target));
        inputWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxInputHeight));
        inputWrapper.revalidate();
        inputWrapper.repaint();

        inputScroll.setVerticalScrollBarPolicy(
                needsScroll ? ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                        : ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        );

        // mantém o cursor visível SEM o mover para o fim
        SwingUtilities.invokeLater(() -> {
            try {
                Rectangle r = promptField.modelToView2D(Math.min(caretPos, promptField.getDocument().getLength())).getBounds();
                r.y += JBUI.scale(3);
                promptField.scrollRectToVisible(r);
            } catch (Exception ignore) { }
        });
    }


    private void notifyUi(String msg, NotificationType type) {
        Notifications.Bus.notify(new Notification("ChatGPT", "ChatGPT IntelliJ Helper", msg, type), project);
    }
}
