package com.teuprojeto.chatgpt.actions;

import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.teuprojeto.chatgpt.core.OpenAiHttp;
import com.teuprojeto.chatgpt.settings.ChatGptSettingsState;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class AskFromSelectionInsertAction extends AnAction {

    private static final Set<String> SENSITIVE_NAMES = Set.of("pom.xml","build.gradle","settings.gradle","gradle.properties");
    private static final Set<String> SENSITIVE_EXTS  = Set.of("xml","yml","yaml","json","toml","properties");
    private static final Set<String> SAFE_EXTS       = Set.of("java","kt","kts","txt","md","markdown","groovy");

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);

        String apiKey = ChatGptSettingsState.getInstance().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            Messages.showWarningDialog(project, "Define a API key em Settings → Tools → ChatGPT.", "API key em falta");
            return;
        }

        // leitura segura da seleção
        String selected = ApplicationManager.getApplication().runReadAction(
                (com.intellij.openapi.util.Computable<String>) () -> {
                    if (editor == null) return null;
                    var sel = editor.getSelectionModel();
                    return sel.hasSelection() ? sel.getSelectedText() : null;
                }
        );

        String prompt = (selected != null && !selected.isBlank())
                ? selected
                : Messages.showInputDialog(project, "Escreve o teu prompt:", "ChatGPT", Messages.getQuestionIcon());

        if (prompt == null || prompt.isBlank()) return;

        boolean insertDirect = shouldInsertDirectly(vf);

        new com.intellij.openapi.progress.Task.Backgroundable(project, "ChatGPT a processar…", false) {
            @Override
            public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    String answer = OpenAiHttp.chat(apiKey, "gpt-4o-mini", prompt, 0.7);

                    if (insertDirect && editor != null) {
                        // inserir após a seleção (ou no cursor se não houver)
                        ApplicationManager.getApplication().invokeLater(() ->
                                WriteCommandAction.runWriteCommandAction(project, () -> {
                                    Document doc = editor.getDocument();
                                    var sel = editor.getSelectionModel();
                                    int insertionOffset = sel.hasSelection() ? sel.getSelectionEnd() : editor.getCaretModel().getOffset();
                                    doc.insertString(insertionOffset, "\n/* ChatGPT */\n" + answer + "\n");
                                })
                        );
                        showNotification(project, "ChatGPT OK (inserido no editor)", NotificationType.INFORMATION);
                    } else {
                        // abrir em Scratch
                        ApplicationManager.getApplication().invokeLater(() -> {
                            String base = (vf != null) ? vf.getName() : "untitled";
                            int dot = base.lastIndexOf('.');
                            if (dot > 0) base = base.substring(0, dot);
                            String name = "ChatGPT-" + base + "-" +
                                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) +
                                    ".txt";

                            Language lang = PlainTextLanguage.INSTANCE;
                            var scratch = ScratchRootType.getInstance().createScratchFile(
                                    project, name, lang,
                                    "/* Prompt */\n" + prompt + "\n\n/* Resposta */\n" + answer + "\n"
                            );
                            if (scratch != null) {
                                FileEditorManager.getInstance(project).openFile(scratch, true);
                                showNotification(project, "ChatGPT OK (aberto em Scratch)", NotificationType.INFORMATION);
                            } else {
                                showNotification(project, "Não foi possível criar Scratch file.", NotificationType.WARNING);
                            }
                        });
                    }
                } catch (Exception ex) {
                    showNotification(project, "Erro: " + ex.getMessage(), NotificationType.ERROR);
                }
            }
        }.queue();
    }

    private static boolean shouldInsertDirectly(VirtualFile vf) {
        if (vf == null) return false;
        String name = vf.getName();
        String ext = vf.getExtension() != null ? vf.getExtension().toLowerCase() : "";
        if (SENSITIVE_NAMES.contains(name)) return false;
        if (SENSITIVE_EXTS.contains(ext)) return false;
        if (SAFE_EXTS.contains(ext)) return true;
        return false;
    }

    private static void showNotification(Project project, String msg, NotificationType type) {
        Notifications.Bus.notify(new Notification("ChatGPT", "ChatGPT IntelliJ Helper", msg, type), project);
    }
}
