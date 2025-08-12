package com.teuprojeto.chatgpt.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class OpenChatToolWindowAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow("ChatGPT");
        if (tw != null) tw.activate(null, true);
    }
}
