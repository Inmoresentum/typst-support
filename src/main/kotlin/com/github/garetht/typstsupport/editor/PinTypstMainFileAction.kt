package com.github.garetht.typstsupport.editor

import com.github.garetht.typstsupport.configuration.TypstProjectSettings
import com.github.garetht.typstsupport.language.filetype.TypstFileType
import com.github.garetht.typstsupport.languageserver.TypstLspServerSupportProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.lsp.api.LspServerManager
import org.eclipse.lsp4j.ExecuteCommandParams

private val LOG = logger<PinTypstMainFileAction>()

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
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        LOG.info("Pinning/Unpinning Typst main file: ${virtualFile.name}")

        val settings = TypstProjectSettings.getInstance(project)
        val mainFilePath = virtualFile.path

        // Toggle pin state
        settings.state.mainFilePath = if (settings.state.mainFilePath == mainFilePath) null else mainFilePath

        val lspServer = LspServerManager.getInstance(project)
            .getServersForProvider(TypstLspServerSupportProvider::class.java)
            .firstOrNull() ?: return

        val command = "tinymist.pinMain"
        val arguments = if (settings.state.mainFilePath != null) listOf(mainFilePath) else null

        lspServer.sendRequestSync {
            it.workspaceService.executeCommand(ExecuteCommandParams(command, arguments))
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
