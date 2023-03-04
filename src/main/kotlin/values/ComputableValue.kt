package values

import contexts.AbstractComputationContext
import contexts.ComputationContext
import exceptions.NotCaughtException
import exceptions.NotInitializedException
import exceptions.RecursiveComputationException
import kotlin.reflect.KProperty

/**
 * Marker annotation for [ComputableValue] DSL.
 */
@DslMarker
public annotation class ComputationDsl

/**
 * Interface that encapsulates delegate for computable value that may have dependents and dependencies.
 * It provides several public ways to access value.
 */
@ComputationDsl
public interface ComputableValue<out T> {
    /**
     * Collected property names for [ComputableValue.toString].
     */
    public val names: Set<String>
    
    /**
     *  Joins names of remembered delegated properties with "/" if they are provided else calls `super.toString()`.
     */
    override fun toString(): String
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
    
    /**
     * Getter access for [ComputableValue.value].
     *
     * It also remembers the name of the [property] to prettify [ComputableValue.toString].
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T

    /**
     * More convenient access to [ComputableValue.result] assuming [Result.success] is inside.
     *
     * @throws NotInitializedException If [ComputableValue.result] cannot be obtained and thus is null.
     * @throws Throwable If [ComputableValue.result] was failure, the exception is rethrown.
     * @return Stored value of [ComputableValue.result].
     * @see ComputableValue.result
     */
    public val value: T
    
    /**
     * Refreshes the current value and all transitive dependents.
     *
     * Parameter setter call is a checkpoint for [ComputationContext.WithHistory.undo] and [ComputationContext.WithHistory.redo].
     *
     * Eagerness of the recomputing of the dependent values is defined by [ComputationContext.computeEagerlyByDefault].
     *
     * This operation drops the following history if [ComputationContext.isWatchingHistory] is true.
     *
     * @see ComputationContext.WithHistory
     */
    public fun refresh()
}

/**
 * Mutable version of interface [ComputableValue].
 */
public interface MutableComputableValue<T> : ComputableValue<T> {
    /**
     * Setter access for [MutableComputableValue.value].
     * 
     * It also remembers the name of the [property] to prettify [ComputableValue.toString].
     */
    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}
