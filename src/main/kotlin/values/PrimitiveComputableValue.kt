package values

import contexts.AbstractComputationContext
import exceptions.IllegalComputationStateException
import exceptions.NotCaughtException
import exceptions.NotInitializedException
import kotlinx.collections.immutable.persistentSetOf
import states.ComputableValueState
import states.NotInitialized
import states.WithValue
import states.casted
import java.util.*
import kotlin.reflect.KProperty

/**
 * Abstract internal implementation of [ComputableValue] interface.
 * Any user implementation of the interface must combine existing primitives.
 */
context(AbstractComputationContext)
public abstract class PrimitiveComputableValue<T> internal constructor(vararg names: String): ComputableValue<T> {
    protected abstract val computeEagerly: Boolean

    override val names: Set<String> get() = namesImpl
    internal val namesImpl: SortedSet<String> = sortedSetOf(*names)

    override fun toString(): String {
        return if (names.isNotEmpty()) names.joinToString("/") else super.toString()
    }

    internal var storedState: ComputableValueState<T> = NotInitialized(persistentSetOf(), persistentSetOf())

    internal var state: ComputableValueState<T>
        get() = getNodeState(this) ?: storedState
        set(value) = setNodeState(this, value)

    // todo multithreaded when delegating context receivers will be call-site-wise
    protected abstract fun computeResult(): Result<T>

    override val result: Result<T>?
        get() = when (val oldState = state) {
            is NotInitialized -> if (mayRecompute) computeResultWithinStateMachine() else null
            is WithValue -> {
                if (mayRecompute) {
                    openComputation()
                    currentNode?.let { this dependsOn it }
                    closeComputation(successfully = true)
                }
                oldState.cachedValue
            }
        }
    
    internal fun computeIfNotLazy() {
        if (!computeEagerly) return
        if (state !is NotInitialized) return
        if (!mayRecompute) throw IllegalComputationStateException("Cannot compute value implicitly during watching history")
        computeResultWithinStateMachine()
    }

    private val mayRecompute get() = !isWatchingHistory || isCausedByUserAction

    private fun computeResultWithinStateMachine(): Result<T> {
        openComputation()
        currentNode?.let { this dependsOn it }
        freeDependencies()
        val result = computeResult()
        val notCaughtException = result.exceptionOrNull() as? NotCaughtException
        if (notCaughtException != null) {
            closeComputation(successfully = false)
            throw notCaughtException
        }
        state = WithValue(state.dependents, state.dependencies, result)
        if (computeEagerly) {
            this.state.dependents.forEach {
                it.state = it.state.invalidated().casted
            }
            precommitTasks.addAll(this.state.dependents)
        }
        closeComputation(successfully = true)
        return result
    }

    internal fun invalidateAllFromThis() {
        fun <T> PrimitiveComputableValue<T>.invalidateCurrent() {
            state = state.invalidated()
        }

        fun invalidateAllFromThisImpl(current: PrimitiveComputableValue<*>): Unit = when (current.state) {
            is NotInitialized -> {}
            is WithValue -> {
                current.invalidateCurrent()
                for (dependent in current.state.dependents) {
                    invalidateAllFromThisImpl(dependent)
                }
            }
        }
        invalidateAllFromThisImpl(this)
    }

    private infix fun dependsOn(dependent: PrimitiveComputableValue<*>) {
        this.state = this.state.withNewDependent(dependent)
        dependent.state = dependent.state.withNewDependency(dependency = this).casted
    }

    private fun freeDependencies() {
        for (dependency in state.dependencies) {
            dependency.state = dependency.state.withoutDependent(this).casted
        }
        state = state.withoutAllDependencies()
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        namesImpl.add(property.name)
        return value
    }

    override val value: T
        get() = (result ?: throw NotInitializedException(this)).getOrThrow()

    
    abstract override fun refresh()
}
