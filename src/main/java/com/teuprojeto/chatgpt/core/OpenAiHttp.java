package com.teuprojeto.chatgpt.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class OpenAiHttp {

    private static final String OPENAI_BASE = "https://api.openai.com/v1";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // readTimeout = 0 para suportar streaming
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.SECONDS)
            .build();

    private OpenAiHttp() {}

    // =================== Helpers ===================

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static JsonArray toJsonMessages(List<Map<String, Object>> messages) {
        JsonArray arr = new JsonArray();
        for (Map<String, Object> m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", String.valueOf(m.get("role")));
            o.addProperty("content", String.valueOf(m.get("content")));
            arr.add(o);
        }
        return arr;
    }

    // Constrói lista de mensagens com histórico (capado por tamanho),
    // + um system prompt fixo.
    private static List<Map<String, Object>> buildMessages(
            List<HistoryMsg> history,
            String userPrompt,
            String systemPrompt,
            int maxChars
    ) {
        final String SYSTEM = (systemPrompt == null || systemPrompt.isBlank())
                ? "Responde em português de Portugal e trata o utilizador por tu."
                : systemPrompt.trim();
        final int MAX_CHARS = Math.max(1000, maxChars);

        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("system", SYSTEM));

        int total = SYSTEM.length();
        if (history != null && !history.isEmpty()) {
            List<Map<String, Object>> rev = new ArrayList<>();
            ListIterator<HistoryMsg> it = history.listIterator(history.size());
            while (it.hasPrevious()) {
                HistoryMsg h = it.previous();
                String role = ("assistant".equalsIgnoreCase(h.role)) ? "assistant" : "user";
                String content = h.text != null ? h.text : "";
                if (total + content.length() > MAX_CHARS) break;
                rev.add(msg(role, content));
                total += content.length();
            }
            Collections.reverse(rev);
            msgs.addAll(rev);
        }

        msgs.add(msg("user", userPrompt));
        return msgs;
    }

    // =================== API: sem histórico (compat) ===================

    public static String chat(String apiKey, String model, String prompt, double temperature) throws IOException {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("system", "Responde em português de Portugal e trata o utilizador por tu."));
        msgs.add(msg("user", prompt));
        return callChatCompletions(apiKey, model, msgs, temperature);
    }

    public static void chatStream(
            String apiKey, String model, String prompt, double temperature,
            Consumer<String> onDelta, Runnable onDone
    ) throws IOException {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(msg("system", "Responde em português de Portugal e trata o utilizador por tu."));
        msgs.add(msg("user", prompt));
        streamChatCompletions(apiKey, model, msgs, temperature, onDelta, onDone);
    }

    // =================== API: com histórico ===================

    public static String chatWithHistory(
            String apiKey, String model,
            List<HistoryMsg> history, String userPrompt, double temperature,
            String systemPrompt, int maxContextChars
    ) throws IOException {
        List<Map<String, Object>> msgs = buildMessages(history, userPrompt, systemPrompt, maxContextChars);
        return callChatCompletions(apiKey, model, msgs, temperature);
    }

    public static void chatStreamWithHistory(
            String apiKey, String model,
            List<HistoryMsg> history, String userPrompt, double temperature,
            java.util.function.Consumer<String> onDelta, Runnable onDone,
            String systemPrompt, int maxContextChars
    ) throws IOException {
        List<Map<String, Object>> msgs = buildMessages(history, userPrompt, systemPrompt, maxContextChars);
        streamChatCompletions(apiKey, model, msgs, temperature, onDelta, onDone);
    }

    // =================== HTTP core ===================

    private static String callChatCompletions(String apiKey, String model,
                                              List<Map<String, Object>> messages,
                                              double temperature) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", toJsonMessages(messages));
        body.addProperty("temperature", temperature);

        Request req = new Request.Builder()
                .url(OPENAI_BASE + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(JSON, body.toString()))
                .build();

        try (Response resp = HTTP.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + ": " + (resp.body() != null ? resp.body().string() : "sem corpo"));
            }
            String s = resp.body().string();
            JsonObject o = JsonParser.parseString(s).getAsJsonObject();
            JsonArray choices = o.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) return "";
            JsonObject first = choices.get(0).getAsJsonObject();
            JsonObject msg = first.getAsJsonObject("message");
            return (msg != null && msg.has("content")) ? msg.get("content").getAsString() : "";
        }
    }

    private static void streamChatCompletions(String apiKey, String model,
                                              List<Map<String, Object>> messages,
                                              double temperature,
                                              Consumer<String> onDelta,
                                              Runnable onDone) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", toJsonMessages(messages));
        body.addProperty("temperature", temperature);
        body.addProperty("stream", true);

        Request req = new Request.Builder()
                .url(OPENAI_BASE + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(JSON, body.toString()))
                .build();

        Call call = HTTP.newCall(req);
        Response resp = call.execute();
        if (!resp.isSuccessful()) {
            throw new IOException("HTTP " + resp.code() + ": " + (resp.body() != null ? resp.body().string() : "sem corpo"));
        }

        try (Response r = resp) {
            BufferedSource src = r.body().source();
            while (!src.exhausted()) {
                String line;
                try {
                    line = src.readUtf8Line();
                } catch (Exception e) {
                    break; // EOF/erro de stream
                }
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (!line.startsWith("data:")) continue;

                String payload = line.substring(5).trim();
                if (payload.isEmpty()) continue;
                if ("[DONE]".equals(payload)) break;

                JsonObject chunk;
                try {
                    chunk = JsonParser.parseString(payload).getAsJsonObject();
                } catch (Exception ignore) {
                    continue;
                }

                // choices é um Array; extraímos o delta.content
                JsonArray choices = chunk.getAsJsonArray("choices");
                if (choices != null && choices.size() > 0) {
                    JsonObject choice0 = choices.get(0).getAsJsonObject();
                    JsonObject delta = choice0.getAsJsonObject("delta");
                    if (delta != null && delta.has("content")) {
                        String piece = delta.get("content").getAsString();
                        if (piece != null && !piece.isEmpty()) {
                            onDelta.accept(piece);
                        }
                    }
                }
            }
        } finally {
            if (onDone != null) onDone.run();
        }
    }

    // =================== DTO p/ histórico ===================

    public static class HistoryMsg {
        public final String role;
        public final String text;
        public HistoryMsg(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }
}
