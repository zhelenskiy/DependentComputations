package values

import states.ComputableValueState
import states.casted
import contexts.AbstractComputationContext
import exceptions.NotCaughtException
import exceptions.NotInitializedException
import exceptions.RecursiveComputationException
import states.NotInitialized
import states.WithValue
import java.util.*
import kotlin.reflect.KProperty
import contexts.ComputationContext
import kotlinx.collections.immutable.persistentSetOf

/**
 * Marker annotation for [ComputableValue] DSL.
 */
@DslMarker
public annotation class ComputationDsl

/**
 * Abstract class that encapsulates delegate for computable value that may have dependents and dependencies.
 * It provides several public ways to access value.
 */
context(AbstractComputationContext)
@ComputationDsl
public abstract class ComputableValue<T> internal constructor(vararg names: String) {
    /**
     * Collected property names for [ComputableValue.toString].
     */
    public val names: Set<String> get() = namesImpl
    internal val namesImpl: SortedSet<String> = sortedSetOf(*names)

    /**
     *  Joins names of remembered delegated properties with "/" if they are provided else calls `super.toString()`.
     */
    override fun toString(): String {
        return if (names.isNotEmpty()) names.joinToString("/") else super.toString()
    }
    internal var storedState: ComputableValueState<T> = NotInitialized(persistentSetOf(), persistentSetOf())
    internal var state: ComputableValueState<T>
        get() = getNodeState(this) ?: storedState
        set(value) = setNodeState(this, value)

    // todo multithreaded when delegating context receivers will be call-site-wise
    protected abstract fun computeResult(): Result<T>

    /**
     * Computes value wrapped by [Result].
     * Each invocation after computation returns the cached value until dependencies change or an explicit [refresh] call happens.
     * 
     * @throws RecursiveComputationException If the computing value depends on itself.
     * @return
     * 1. [Result.success] of the cached value if it was already computed.
     * 2. [Result.failure] of the cached exception (but [NotCaughtException] and its inheritors) if it was already caught.
     * 3. `null` if nothing is cached, [AbstractComputationContext.isWatchingHistory] is true and a history drop was not caused by user actions.
     * 4. [Result.success] or [Result.failure] of the new value according to 1, 2 if nothing is cached yet and 3 is not applicable.
     *   The new [Result] is cached.
     */
    public val result: Result<T>?
        get() = when (val oldState = state) {
            is WithValue -> {
                if (!isWatchingHistory || isCausedByUserAction) {
                    openComputation()
                    currentNode?.let { this dependsOn it }
                    closeComputation(successfully = true)
                }
                oldState.cachedValue
            }

            is NotInitialized -> {
                if (!isWatchingHistory || isCausedByUserAction) {
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
                    result
                } else {
                    null
                }
            }
        }

    internal fun invalidateAllFromThis() {
        fun <T> ComputableValue<T>.invalidateCurrent() {
            state = state.invalidated()
        }

        val visited = mutableSetOf<ComputableValue<*>>()
        fun invalidateAllFromThisImpl(current: ComputableValue<*>) {
            if (current in visited) return
            visited.add(current)
            current.invalidateCurrent()
            for (dependent in current.state.dependents) {
                invalidateAllFromThisImpl(dependent)
            }
        }
        invalidateAllFromThisImpl(this)
    }

    private infix fun dependsOn(dependent: ComputableValue<*>) {
        this.state = this.state.withNewDependent(dependent)
        dependent.state = dependent.state.withNewDependency(dependency = this).casted
    }

    private fun freeDependencies() {
        for (dependency in state.dependencies) {
            dependency.state = dependency.state.withoutDependent(this).casted
        }
        state = state.withoutAllDependencies()
    }

    /**
     * Getter access for [ComputableValue.value].
     * 
     * It also remembers the name of the [property] to prettify [ComputableValue.toString].
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        namesImpl.add(property.name)
        return value
    }

    /**
     * More convenient access to [ComputableValue.result] assuming [Result.success] is inside.
     * 
     * @throws NotInitializedException If [ComputableValue.result] cannot be obtained and thus is null.
     * @throws Throwable If [ComputableValue.result] was failure, the exception is rethrown.
     * @return Stored value of [ComputableValue.result].
     * @see ComputableValue.result
     */
    public val value: T
        get() = (result ?: throw NotInitializedException(this)).getOrThrow()

    /**
     * Refreshes the current value and all transitive dependents.
     *
     * Parameter setter call is a checkpoint for [ComputationContext.WithHistory.undo] and [ComputationContext.WithHistory.redo].
     *
     * Eagerness of the recomputing of the dependent values is defined by [ComputationContext.computeEagerly].
     *
     * This operation drops the following history if [ComputationContext.isWatchingHistory] is true.
     * 
     * @see ComputationContext.WithHistory
     */
    public abstract fun refresh()
}
