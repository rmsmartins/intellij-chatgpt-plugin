package com.teuprojeto.chatgpt.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Service(Service.Level.APP)
@State(name = "ChatGptSettingsState", storages = @Storage("ChatGptSettings.xml"))
public final class ChatGptSettingsState implements PersistentStateComponent<ChatGptSettingsState> {

    public String apiKey = "";

    // Histórico persistente (capado a MAX_HISTORY mensagens)
    private static final int MAX_HISTORY = 200;

    @XCollection(propertyElementName = "history", elementName = "msg")
    public List<Message> history = new ArrayList<>();

    @Tag("v")
    public int schemaVersion = 1;

    // ===== API =====

    public static ChatGptSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(ChatGptSettingsState.class);
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey != null ? apiKey.trim() : ""; }

    public List<Message> getHistory() { return history; }

    public void clearHistory() {
        history.clear();
    }

    public void addUser(String text) {
        add("user", text);
    }

    public void addAssistant(String text) {
        add("assistant", text);
    }

    private void add(String role, String text) {
        if (text == null) text = "";
        history.add(new Message(role, text, System.currentTimeMillis()));
        // Capar tamanho
        if (history.size() > MAX_HISTORY) {
            int drop = history.size() - MAX_HISTORY;
            history.subList(0, drop).clear();
        }
    }

    // ===== PersistentStateComponent =====

    @Override
    public @NotNull ChatGptSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ChatGptSettingsState state) {
        this.apiKey = state.apiKey;
        this.schemaVersion = state.schemaVersion;
        this.history = (state.history != null) ? new ArrayList<>(state.history) : new ArrayList<>();
    }

    // ===== DTO serializável =====

    @Tag("msg")
    public static class Message {
        @Tag("role")
        public String role;

        @Tag("text")
        public String text;

        @Tag("ts")
        public long timestamp;

        public Message() { } // necessário para xmlb

        public Message(String role, String text, long timestamp) {
            this.role = role;
            this.text = text;
            this.timestamp = timestamp;
        }
    }

    public boolean useContext = true;          // usar histórico nas chamadas
    public int maxContextChars = 8000;         // limite de chars do histórico enviado
    public String systemPrompt = "Responde em português de Portugal e trata o utilizador por tu.";

    // getters/setters simples (opcional)
    public boolean isUseContext() { return useContext; }
    public void setUseContext(boolean useContext) { this.useContext = useContext; }

    public int getMaxContextChars() { return maxContextChars; }
    public void setMaxContextChars(int v) { this.maxContextChars = Math.max(1000, v); }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String sp) { this.systemPrompt = (sp == null || sp.isBlank())
            ? "Responde em português de Portugal e trata o utilizador por tu."
            : sp.trim(); }
}
