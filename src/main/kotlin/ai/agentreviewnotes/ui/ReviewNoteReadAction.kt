package ai.agentreviewnotes.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Application
import com.intellij.openapi.util.Computable

internal object ReviewNoteReadAction {
    fun <T> compute(action: () -> T): T =
        compute(ApplicationManager.getApplication(), action)

    fun <T> compute(application: Application, action: () -> T): T =
        application.runReadAction(Computable(action))
}
