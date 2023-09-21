/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.test

import kotlinx.coroutines.*
import kotlin.coroutines.*

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual typealias TestResult = Unit

internal actual fun systemPropertyImpl(name: String): String? = null

internal actual fun createTestResult(testProcedure: suspend CoroutineScope.() -> Unit) {
    val newContext = GlobalScope.newCoroutineContext(EmptyCoroutineContext)
    val coroutine = object: AbstractCoroutine<Unit>(newContext, true, true) {}
    runEventLoop {
        coroutine.start(CoroutineStart.DEFAULT, coroutine, testProcedure)
    }

    if (coroutine.isCancelled) throw coroutine.getCancellationException().let { it.cause ?: it }
}

internal actual fun dumpCoroutines() { }