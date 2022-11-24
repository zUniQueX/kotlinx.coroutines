package foo

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.jvm.internal.CoroutineStackFrame
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.intrinsics.*

// THIS IS JUST COPYPAST TO GET COMPILED WITH internal SuspendFunctionGun

internal typealias PipelineInterceptorFunction<TSubject, TContext> =
    (PipelineContext<TSubject, TContext>, TSubject, Continuation<Unit>) -> Any?

internal object StackWalkingFailedFrame : CoroutineStackFrame, Continuation<Nothing> {
    override val callerFrame: CoroutineStackFrame? get() = null

    override fun getStackTraceElement(): StackTraceElement? {
        return null
    }

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<Nothing>) {
        ""
    }
}

public abstract class PipelineContext<TSubject : Any, TContext : Any>(
    public val context: TContext
) : CoroutineScope {

    /**
     * Subject of this pipeline execution that goes along the pipeline
     */
    public abstract var subject: TSubject

    /**
     * Finishes current pipeline execution
     */
    public abstract fun finish()

    /**
     * Continues execution of the pipeline with the given subject
     */
    public abstract suspend fun proceedWith(subject: TSubject): TSubject

    /**
     * Continues execution of the pipeline with the same subject
     */
    public abstract suspend fun proceed(): TSubject

    internal abstract suspend fun execute(initial: TSubject): TSubject
}

class SuspendFunctionGun<TSubject : Any, TContext : Any>(
    initial: TSubject,
    context: TContext,
    private val blocks: List<PipelineInterceptorFunction<TSubject, TContext>>
) : PipelineContext<TSubject, TContext>(context) {

    override val coroutineContext: CoroutineContext get() = continuation.context

    // this is impossible to inline because of property name clash
    // between PipelineContext.context and Continuation.context
    private val continuation: Continuation<Unit> = object : Continuation<Unit>, CoroutineStackFrame {
        override val callerFrame: CoroutineStackFrame? get() = peekContinuation() as? CoroutineStackFrame

        var currentIndex: Int = Int.MIN_VALUE

        override fun getStackTraceElement(): StackTraceElement? = null

        private fun peekContinuation(): Continuation<*>? {
            if (currentIndex == Int.MIN_VALUE) currentIndex = lastSuspensionIndex
            if (currentIndex < 0) {
                currentIndex = Int.MIN_VALUE
                return null
            }
            // this is only invoked by debug agent during job state probes
            // lastPeekedIndex is non-volatile intentionally
            // and the list of continuations is not synchronized too
            // so this is not guaranteed to work properly (may produce incorrect trace),
            // but the only we care is to not crash here
            // and simply return StackWalkingFailedFrame on any unfortunate accident

            try {
                val result = suspensions[currentIndex] ?: return StackWalkingFailedFrame
                currentIndex -= 1
                return result
            } catch (_: Throwable) {
                return StackWalkingFailedFrame
            }
        }

        override val context: CoroutineContext
            get() = suspensions[lastSuspensionIndex]?.context ?: error("Not started")

        override fun resumeWith(result: Result<Unit>) {
            if (result.isFailure) {
                resumeRootWith(Result.failure(result.exceptionOrNull()!!))
                return
            }

            loop(false)
        }
    }

    override var subject: TSubject = initial

    private val suspensions: Array<Continuation<TSubject>?> = arrayOfNulls(blocks.size)
    private var lastSuspensionIndex: Int = -1
    private var index = 0

    override fun finish() {
        index = blocks.size
    }

    override suspend fun  proceed(): TSubject = suspendCoroutineUninterceptedOrReturn { continuation ->
        if (index == blocks.size) return@suspendCoroutineUninterceptedOrReturn subject

        // TODO ?
        addContinuation(continuation)

        if (loop(true)) {
            discardLastRootContinuation()
            return@suspendCoroutineUninterceptedOrReturn subject
        }

        COROUTINE_SUSPENDED
    }

    override suspend fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    override suspend fun execute(initial: TSubject): TSubject {
        index = 0
        if (index == blocks.size) return initial
        subject = initial

        if (lastSuspensionIndex >= 0) throw IllegalStateException("Already started")

        return proceed()
    }

    /**
     * @return `true` if it is possible to return result immediately
     */
    private fun loop(direct: Boolean): Boolean {
        do {
            val currentIndex = index // it is important to read index every time
            if (currentIndex == blocks.size) {
                if (!direct) {
                    resumeRootWith(Result.success(subject))
                    return false
                }

                return true
            }

            index = currentIndex + 1 // it is important to increase it before function invocation
            val next = blocks[currentIndex]

            try {
                val result = next(this, subject, continuation)
                if (result === COROUTINE_SUSPENDED) return false
            } catch (cause: Throwable) {
                resumeRootWith(Result.failure(cause))
                return false
            }
        } while (true)
    }

    private fun resumeRootWith(result: Result<TSubject>) {
        if (lastSuspensionIndex < 0) error("No more continuations to resume")
        val next = suspensions[lastSuspensionIndex]!!
        suspensions[lastSuspensionIndex--] = null

        if (!result.isFailure) {
            next.resumeWith(result)
        } else {
            // val exception = recoverStackTraceBridge(result.exceptionOrNull()!!, next)
            next.resumeWithException(result.exceptionOrNull()!!)
        }
    }

    private fun discardLastRootContinuation() {
        if (lastSuspensionIndex < 0) throw IllegalStateException("No more continuations to resume")
        suspensions[lastSuspensionIndex--] = null
    }

    private fun addContinuation(continuation: Continuation<TSubject>) {
        suspensions[++lastSuspensionIndex] = continuation
    }
}
