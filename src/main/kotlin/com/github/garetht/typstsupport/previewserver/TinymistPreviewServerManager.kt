package com.github.garetht.typstsupport.previewserver

import com.github.garetht.typstsupport.languageserver.TypstLanguageServerManager
import com.github.garetht.typstsupport.languageserver.TypstLspServerSupportProvider
import com.github.garetht.typstsupport.configuration.TypstProjectSettings
import com.google.gson.internal.LinkedTreeMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import kotlinx.coroutines.runBlocking
import org.eclipse.lsp4j.ExecuteCommandParams
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger


private val LOG = logger<TinymistPreviewServerManager>()

class TinymistPreviewServerManager : PreviewServerManager {

    private data class ServerInfo(
        val dataPlanePort: Int,
        val controlPlanePort: Int,
        val staticServerAddress: String,
        val taskId: UUID,
        val startTime: Long = System.currentTimeMillis(),
    )

    private val servers = ConcurrentHashMap<String, ServerInfo>()
    private val portCounter = AtomicInteger(STARTING_PORT)

    override fun createServer(filepath: String, project: Project, callback: (staticServerAddress: String?) -> Unit) {
        // Resolve to pinned main if set, otherwise use the provided file
        val resolvedMainPath = resolveMainPath(project, filepath)
        val existingServer = servers[resolvedMainPath]
        if (existingServer?.staticServerAddress != null) {
            callback(existingServer.staticServerAddress)
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                // If we're at max servers, remove the oldest one
                if (servers.size >= MAX_SERVERS) {
                    val oldestServer = servers.minByOrNull { it.value.startTime }
                    oldestServer?.let {
                        shutdownServer(
                            it.key, project
                        )
                    }
                }

                // Try to start the server with different ports if needed
                for (attempt in 0 until MAX_START_RETRIES) {
                    LOG.info("Starting server on attempt ${attempt + 1}")
                    val dataPlanePort = findAvailablePort()
                    val controlPlanePort = findAvailablePort()

                    try {
                        val taskId = UUID.randomUUID()
                        // Always start preview anchored at the resolved main path
                        val staticServerAddress = startServer(project, resolvedMainPath, taskId, dataPlanePort, controlPlanePort)

                        if (staticServerAddress != null) {
                            servers[resolvedMainPath] = ServerInfo(dataPlanePort, controlPlanePort, staticServerAddress, taskId)
                        }
                        callback(staticServerAddress)
                        return@runBlocking
                    } catch (e: Exception) {
                        LOG.error(
                            "Failed to start server for $resolvedMainPath on attempt ${attempt + 1}: ${e.message}"
                        )
                        if (attempt == MAX_START_RETRIES - 1) {
                            throw e
                        }
                    }
                }
            }
        }
    }

    override fun shutdownServer(filepath: String, project: Project) {
        // Ensure we target the server keyed by the main file, not the sub-file
        val resolvedMainPath = resolveMainPath(project, filepath)
        ApplicationManager.getApplication().executeOnPooledThread {
            runBlocking {
                val server = retrieveServer(project)
                servers[resolvedMainPath]?.let { serverInfo ->
                    server?.sendRequestSync {
                        it.workspaceService.executeCommand(
                            ExecuteCommandParams(
                                "tinymist.doKillPreview",
                                listOf(listOf(serverInfo.taskId.toString()))
                            )
                        )
                    }
                    servers.remove(resolvedMainPath)
                }
            }
        }
    }

    private fun resolveMainPath(project: Project, fallbackPath: String): String {
        val main = TypstProjectSettings.getInstance(project).state.mainFilePath
        return if (!main.isNullOrBlank()) main else fallbackPath
    }

    private fun findAvailablePort(): Int {
        var attempts = 0
        while (attempts < MAX_PORT_RETRIES) {
            val port = portCounter.getAndIncrement()
            try {
                ServerSocket(port).use {
                    return port
                }
            } catch (e: IOException) {
                attempts++
                continue
            }
        }
        return portCounter.get() - 1
    }

    private suspend fun startServer(
        project: Project,
        filename: String,
        taskId: UUID,
        dataPlanePort: Int,
        controlPlanePort: Int
    ): String? {
        // Provide the project root for includes/styles: use the main's parent directory
        val mainPath = Paths.get(filename)
        val options =
            TinymistPreviewOptions(
                dataPlaneHostPort = dataPlanePort,
                controlPlaneHostPort = controlPlanePort,
                partialRendering = true,
                taskId = taskId,
                root = mainPath.parent
            )

        LOG.info("Starting server with command: $options")

    return retrieveServer(project)?.sendRequestSync {
      it.workspaceService.executeCommand(
        ExecuteCommandParams(
          "tinymist.doStartPreview",
          options.toCommandParamsArguments(filename)
        )
      ).handle { result, throwable ->
        LOG.info("Retrieved server: result: $result, $throwable")
        if (throwable != null) {
          null
        } else {
          (result as? LinkedTreeMap<*, *>)?.get("staticServerAddr") as? String
        }
      }
    }
  }


  private suspend fun retrieveServer(project: Project): LspServer? = TypstLanguageServerManager.waitForServer(
    LspServerManager.getInstance(project), TypstLspServerSupportProvider::class.java
  )

  companion object {
    private const val STARTING_PORT = 23627 // Start from default tinymist port
    private const val MAX_SERVERS = 5
    private const val MAX_START_RETRIES = 10
    private const val MAX_PORT_RETRIES = 2

    private val instance = TinymistPreviewServerManager()
    fun getInstance(): PreviewServerManager = instance
  }
}
