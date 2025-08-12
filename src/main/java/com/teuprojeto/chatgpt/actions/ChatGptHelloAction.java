package com.teuprojeto.chatgpt.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class ChatGptHelloAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Messages.showInfoMessage("Base do plugin pronta. 🎯", "ChatGPT IntelliJ Helper");
    }
}
