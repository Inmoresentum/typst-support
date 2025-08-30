package com.github.garetht.typstsupport.configuration

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@State(
    name = "com.github.garetht.typstsupport.configuration.TypstProjectSettings",
    storages = [Storage("typst-support.xml")]
)
@Service(Service.Level.PROJECT)
class TypstProjectSettings : PersistentStateComponent<TypstProjectSettings.State> {

    class State {
        var mainFilePath: String? = null
    }

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): TypstProjectSettings = project.service()
    }
}