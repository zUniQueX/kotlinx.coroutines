/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package foo.benchmarks

import foo.*
import kotlinx.coroutines.*
import org.openjdk.jmh.annotations.*
import java.util.concurrent.*
import kotlin.test.*

public typealias PipelineInterceptor<TSubject, TContext> =
    suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(1)
open class KtorLikeBench {

    private val requestIdThreadLocal = ThreadLocal.withInitial { 72 }


    @Benchmark
    fun test() = runBlocking {
        requestIdThreadLocal.set(72)
        val interceptors = listOf<PipelineInterceptor<Unit, Unit>>(
            {
                // 1
                withContext(requestIdThreadLocal.asContextElement(123)) {
                    proceed()
                }
                requestIdThreadLocal.get()
            },

            {
                withContext(Dispatchers.IO) {}
            }
        )

        SuspendFunctionGun(Unit, Unit, interceptors as List<PipelineInterceptorFunction<Unit, Unit>>).execute(
            Unit
        )
    }
}
