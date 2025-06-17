package com.github.garetht.typstsupport.editor

import com.github.garetht.typstsupport.previewserver.PreviewServerManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.InputEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

private val LOG = logger<TypstPreviewEditor>()

class TypstPreviewEditor(private val file: VirtualFile, previewServerManager: PreviewServerManager) :
  UserDataHolderBase(), FileEditor {

  private val panel = JPanel(BorderLayout())
  private val browser =
    JBCefBrowser.createBuilder()
      .setMouseWheelEventEnable(false)
      .build()
  private val cardLayout = CardLayout()
  private val containerPanel = JPanel(cardLayout)
  private val loadingPanel = JPanel(BorderLayout()).apply {
    add(JLabel("Loading preview...", SwingConstants.CENTER), BorderLayout.CENTER)
  }

  init {
    containerPanel.add(browser.component, "browser")
    containerPanel.add(loadingPanel, "loading")
    panel.add(containerPanel, BorderLayout.CENTER)

    cardLayout.show(containerPanel, "loading")

    previewServerManager.createServer(file.path) {
      ApplicationManager.getApplication().invokeLater {
        LOG.warn("Attempting to start the preview loader on port $it")
        browser.loadURL("http://127.0.0.1:$it")
        LOG.warn("Did to start the preview loader on port $it")
      }
    }

    browser.jbCefClient.addLoadHandler(object : CefLoadHandler {
      override fun onLoadingStateChange(
        p0: CefBrowser?,
        p1: Boolean,
        p2: Boolean,
        p3: Boolean
      ) = Unit

      override fun onLoadStart(
        p0: CefBrowser?,
        p1: CefFrame?,
        p2: CefRequest.TransitionType?
      ) = Unit

      override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
        LOG.warn("Load ended with status: $httpStatusCode for: ${frame?.url}")
        if (frame?.isMain == true) {
          ApplicationManager.getApplication().invokeLater {
            cardLayout.show(containerPanel, "browser")
          }
        }
      }

      override fun onLoadError(
        browser: CefBrowser?,
        frame: CefFrame?,
        errorCode: CefLoadHandler.ErrorCode?,
        errorText: String?,
        failedUrl: String?
      ) = Unit
    }, browser.cefBrowser)

    browser.component.addMouseWheelListener { e ->
      // Check if Shift key is pressed
      val isShiftPressed = (e.modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0

      // Calculate the scroll delta
      val scrollDelta = e.wheelRotation * e.scrollAmount * 3.5

      // Convert to horizontal scroll if Shift is pressed, otherwise vertical
      val deltaX =
        if (isShiftPressed) {
          scrollDelta
        } else {
          0.0
        }
      val deltaY =
        if (isShiftPressed) {
          0.0
        } else {
          scrollDelta
        }

      transmitScrollToPage(deltaX, deltaY)
    }
  }

  private fun transmitScrollToPage(deltaX: Double, deltaY: Double) {
    val scrollScript =
      """
    (function() {
      // Create and dispatch a wheel event
      var wheelEvent = new WheelEvent('wheel', {
        deltaX: $deltaX,
        deltaY: $deltaY,
        deltaZ: 0,
        deltaMode: WheelEvent.DOM_DELTA_PIXEL,
        bubbles: true,
        cancelable: false,
        view: window
      });
      
      // Dispatch to the document
      document.dispatchEvent(wheelEvent);
      
      // Manually scroll the page
      window.scrollBy($deltaX, $deltaY);
    })();
  """
        .trimIndent()

    browser.cefBrowser.executeJavaScript(scrollScript, "", 0)
  }

  override fun getComponent(): JComponent = panel

  override fun getPreferredFocusedComponent(): JComponent? = browser.component

  override fun getName(): String = "Preview"

  override fun setState(state: FileEditorState) = Unit

  override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(p0: PropertyChangeListener) {}

  override fun removePropertyChangeListener(p0: PropertyChangeListener) {}

  override fun dispose() {
    browser.dispose()
  }

  override fun getFile(): VirtualFile = file
}
