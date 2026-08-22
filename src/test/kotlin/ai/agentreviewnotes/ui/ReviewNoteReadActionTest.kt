package ai.agentreviewnotes.ui

import com.intellij.openapi.application.Application
import com.intellij.openapi.util.Computable
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewNoteReadActionTest {
    @Test
    fun `navigation model read выполняется через application read action`() {
        var readAccess = false
        var calls = 0
        val application = Proxy.newProxyInstance(
            Application::class.java.classLoader,
            arrayOf(Application::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "isReadAccessAllowed" -> readAccess
                "runReadAction" -> {
                    calls++
                    readAccess = true
                    try {
                        @Suppress("UNCHECKED_CAST")
                        (arguments.single() as Computable<Boolean>).compute()
                    } finally {
                        readAccess = false
                    }
                }
                "toString" -> "TestApplication"
                "hashCode" -> 1
                "equals" -> false
                else -> error("Неожиданный вызов Application.${method.name}")
            }
        } as Application

        assertFalse(application.isReadAccessAllowed)
        val result = ReviewNoteReadAction.compute(application) { application.isReadAccessAllowed }
        assertTrue(result)
        assertEquals(1, calls)
        assertFalse(application.isReadAccessAllowed)
    }
}
