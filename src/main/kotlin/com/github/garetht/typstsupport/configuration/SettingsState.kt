package com.github.garetht.typstsupport.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(
  name = "SettingsState",
  storages = [Storage("typst.xml")]
)
class SettingsState : SimplePersistentStateComponent<SettingsState.State>(State()) {

  class State : BaseState() {
    var binarySource by enum(BinarySource.USE_AUTOMATIC_DOWNLOAD)
    var customBinaryPath by property("") { it.isEmpty() || it.isBlank() }

  }

  companion object {
    fun getInstance(): SettingsState {
      return ApplicationManager.getApplication().getService(SettingsState::class.java)
    }
  }
}
