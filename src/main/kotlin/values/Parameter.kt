package values

import contexts.AbstractComputationContext
import contexts.ComputationContext
import exceptions.IllegalComputationStateException
import states.WithValue
import kotlin.reflect.KProperty

/**
 * Read-write inheritor of [ComputableValue] whose value is mutable. Its initial value is [value].
 *
 * Example:
 * ```
 * var x by Parameter(42)
 * println(x) // prints 42
 * x++
 * println(x) // prints 43
 * ```
 *
 * @param value Initial value of the [Parameter].
 * @param names Already known initial names (see [ComputableValue.toString]) that may help to identify the instance.
 */
context (AbstractComputationContext)
public class Parameter<T> public constructor(value: T, vararg names: String) : ComputableValue<T>(*names) {
    init {
        updateValue(value, force = true)
    }

    override fun computeResult(): Result<T> = (state as WithValue<T>).cachedValue

    private fun updateValue(value: T, force: Boolean) {
        openComputation()
        isCausedByUserAction = true
        if (force || value != this.value) {
            state = WithValue(
                dependencies = state.dependencies,
                dependents = state.dependents,
                cachedValue = Result.success(value),
            )
            refresh()
        }
        closeComputation(successfully = true)
    }

    /**
     * Setter access for [Parameter.value].
     *
     * It also remembers the name of the [property] to prettify [ComputableValue.toString].
     *
     * Parameter setter call is a checkpoint for [ComputationContext.WithHistory.undo] and [ComputationContext.WithHistory.redo].
     *
     * If new value is equal to the old one, dependents are not recomputed.
     *
     * Eagerness of the recomputing of the dependent values is defined by [ComputationContext.computeEagerly].
     *
     * This operation drops the following history if [ComputationContext.isWatchingHistory] is true.
     *
     * @see ComputationContext.WithHistory
     */
    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        namesImpl.add(property.name)
        updateValue(value, force = false)
    }

    override fun refresh() {
        openComputation()
        isCausedByUserAction = true
        val existingState = state
        withinStackScope {
            invalidateAllFromThis()
        }
        state = existingState
        if (computeEagerly) {
            for (dependent in state.dependents) {
                dependent.result ?: throw IllegalComputationStateException("Refresh is caused by user")
            }
        }
        closeComputation(successfully = true)
    }
}
