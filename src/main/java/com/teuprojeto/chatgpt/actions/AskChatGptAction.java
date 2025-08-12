package com.teuprojeto.chatgpt.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.teuprojeto.chatgpt.settings.ChatGptSettingsState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class AskChatGptAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();

        // 1) Ler API Key
        String apiKey = ChatGptSettingsState.getInstance().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            notify(project, "Define a API key em Settings → Tools → ChatGPT.", NotificationType.WARNING);
            return;
        }

        // 2) Pedir prompt
        String prompt = Messages.showInputDialog(project, "Escreve o teu prompt:", "ChatGPT: Ask", Messages.getQuestionIcon());
        if (prompt == null || prompt.isBlank()) return;

        // 3) Chamada síncrona simples (request curto). Se quiseres, migramos para background after.
        try {
            String responseText = callChatCompletions(apiKey, prompt);
            notify(project, responseText, NotificationType.INFORMATION);
        } catch (Exception ex) {
            notify(project, "Erro na chamada à API: " + ex.getMessage(), NotificationType.ERROR);
        }
    }

    private static String callChatCompletions(String apiKey, String prompt) throws Exception {
        // Endpoint de Chat Completions (estável e simples)
        URI uri = URI.create("https://api.openai.com/v1/chat/completions");

        // Corpo JSON
        JsonObject body = new JsonObject();
        body.addProperty("model", "gpt-4o-mini");
        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);
        messages.add(userMsg);
        body.add("messages", messages);
        body.addProperty("temperature", 0.7);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }

        // Extrair choices[0].message.content
        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) return "(sem resposta)";
        JsonObject first = choices.get(0).getAsJsonObject();
        JsonObject msg = first.getAsJsonObject("message");
        if (msg == null || msg.get("content") == null) return "(sem conteúdo)";
        return msg.get("content").getAsString();
    }

    private static void notify(Project project, String msg, NotificationType type) {
        Notifications.Bus.notify(new Notification("ChatGPT", "ChatGPT IntelliJ Helper", msg, type), project);
    }
}
