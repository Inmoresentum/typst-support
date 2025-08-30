package com.github.garetht.typstsupport.editor

import com.github.garetht.typstsupport.configuration.TypstProjectSettings
import com.github.garetht.typstsupport.language.filetype.TypstFileType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class PinTypstMainFileAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (project == null || virtualFile == null || virtualFile.fileType !is TypstFileType) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isEnabledAndVisible = true
        val settings = TypstProjectSettings.getInstance(project)
        if (settings.state.mainFilePath == virtualFile.path) {
            e.presentation.text = "Unpin as Typst Main File"
        } else {
            e.presentation.text = "Pin as Typst Main File"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!!
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)!!
        val settings = TypstProjectSettings.getInstance(project)

        if (settings.state.mainFilePath == virtualFile.path) {
            settings.state.mainFilePath = null
        } else {
            settings.state.mainFilePath = virtualFile.path
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
