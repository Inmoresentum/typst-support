package com.github.garetht.typstsupport.configuration

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput

interface ProcessExecutor {
  fun executeProcess(commandLine: GeneralCommandLine): ProcessOutput
}
