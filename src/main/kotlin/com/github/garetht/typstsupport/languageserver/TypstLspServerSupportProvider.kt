package com.github.garetht.typstsupport.languageserver

import com.github.garetht.typstsupport.TypstIcons
import com.github.garetht.typstsupport.configuration.SettingsConfigurable
import com.github.garetht.typstsupport.configuration.TypstProjectSettings
import com.github.garetht.typstsupport.languageserver.downloader.Filesystem
import com.github.garetht.typstsupport.languageserver.downloader.TinymistDownloadScheduler
import com.github.garetht.typstsupport.languageserver.downloader.TinymistDownloader
import com.github.garetht.typstsupport.languageserver.locations.TinymistLocationResolver
import com.github.garetht.typstsupport.languageserver.locations.isSupportedTypstFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem
import org.eclipse.lsp4j.ExecuteCommandParams

private val LISTENER_INSTALLED = Key.create<Boolean>("typst.pin.listener.installed")

class TypstLspServerSupportProvider : LspServerSupportProvider {
    private val downloadScheduler by lazy {
        TinymistDownloadScheduler(
            TinymistLocationResolver(),
            TinymistDownloader(),
            Filesystem(),
            TypstLanguageServerManager()
        )
    }

    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter
    ) {
        if (!file.isSupportedTypstFileType()) {
            return
        }

        TypstManager(downloadScheduler, project, serverStarter).startIfRequired()

        // Re-assert pin for newly opened files (if a main is set)
        val settings = TypstProjectSettings.getInstance(project)
        val pinnedMain = settings.state.mainFilePath
        if (!pinnedMain.isNullOrBlank()) {
            val lspServer = LspServerManager.getInstance(project)
                .getServersForProvider(TypstLspServerSupportProvider::class.java)
                .firstOrNull()

            lspServer?.sendRequestSync {
                it.workspaceService.executeCommand(ExecuteCommandParams("tinymist.pinMain", listOf(pinnedMain)))
            }
        }

        // Install a one-time listener to re-pin when the editor selection changes
        if (project.getUserData(LISTENER_INSTALLED) != true) {
            val connection = project.messageBus.connect(project)
            connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val selected = event.newFile ?: return
                    if (!selected.isSupportedTypstFileType()) return

                    val currentMain = TypstProjectSettings.getInstance(project).state.mainFilePath
                    if (currentMain.isNullOrBlank()) return

                    val server = LspServerManager.getInstance(project)
                        .getServersForProvider(TypstLspServerSupportProvider::class.java)
                        .firstOrNull() ?: return

                    // Re-send pin so tinymist applies multi-file context for the newly focused Typst file
                    server.sendRequestSync {
                        it.workspaceService.executeCommand(ExecuteCommandParams("tinymist.pinMain", listOf(currentMain)))
                    }
                }
            })
            project.putUserData(LISTENER_INSTALLED, true)
        }
    }

    override fun createLspServerWidgetItem(
        lspServer: LspServer,
        currentFile: VirtualFile?
    ): LspServerWidgetItem? {
        return object : LspServerWidgetItem(
            lspServer,
            currentFile,
            TypstIcons.WIDGET_ICON,
            SettingsConfigurable::class.java
        ) {
            override val widgetActionText: @NlsActions.ActionText String
                get() = "Typst (Tinymist)"
        }
    }
}
