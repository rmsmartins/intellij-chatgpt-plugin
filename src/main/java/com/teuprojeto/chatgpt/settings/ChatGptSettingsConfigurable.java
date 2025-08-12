package com.teuprojeto.chatgpt.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ChatGptSettingsConfigurable implements Configurable {

    private JPanel mainPanel;
    private JTextField apiKeyField;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "ChatGPT";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        // Criar UI simples
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JLabel label = new JLabel("OpenAI API Key:");
        apiKeyField = new JTextField();
        apiKeyField.setMaximumSize(apiKeyField.getPreferredSize());

        mainPanel.add(label);
        mainPanel.add(apiKeyField);

        // Carregar valor guardado
        ChatGptSettingsState settings = ChatGptSettingsState.getInstance();
        apiKeyField.setText(settings.getApiKey());

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        ChatGptSettingsState settings = ChatGptSettingsState.getInstance();
        return !apiKeyField.getText().equals(settings.getApiKey());
    }

    @Override
    public void apply() {
        ChatGptSettingsState settings = ChatGptSettingsState.getInstance();
        settings.setApiKey(apiKeyField.getText());
    }

    @Override
    public void reset() {
        ChatGptSettingsState settings = ChatGptSettingsState.getInstance();
        apiKeyField.setText(settings.getApiKey());
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
        apiKeyField = null;
    }
}
