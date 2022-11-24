package foo

import kotlinx.coroutines.*
import kotlin.test.*

private val requestIdThreadLocal = ThreadLocal.withInitial { 72 }
public typealias PipelineInterceptor<TSubject, TContext> =
        suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit

class SuspendFunctionGunTest {

    @Test
    fun test() = runBlocking {
        repeat(1000) {
            requestIdThreadLocal.set(72)
            val interceptors = listOf<PipelineInterceptor<Unit, Unit>>(
                {
                    // 1
                    withContext(requestIdThreadLocal.asContextElement(123)) {
                        val supplementary = 2
                        proceed()
                        val supplementary2 = 2
                    }
                    println(requestIdThreadLocal.get())
                    if (requestIdThreadLocal.get() == 123) {
                        val putBreakpointHereToDebug = 2
                    }
                    assertEquals(72, requestIdThreadLocal.get(), "Thread local's context should be restored")
                },

                {
                    // file has more than 4088 bytes
                    val channel = withContext(Dispatchers.IO) {}
                    println("? " + requestIdThreadLocal.get())
                }
            )

            try {
                SuspendFunctionGun(Unit, Unit, interceptors as List<PipelineInterceptorFunction<Unit, Unit>>).execute(
                    Unit
                )
            } finally {
                println(requestIdThreadLocal.get())
            }
        }
    }
}
