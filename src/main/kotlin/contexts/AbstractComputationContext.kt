package contexts

import values.ComputableValue
import states.ComputableValueState

/**
 * Abstract class that defines common context for the single group of possibly connected computations.
 * 
 * It stores internals of dependency graph, manipulates the state of [ComputableValue] and history of its change, handles recursive chains errors.
 */
public abstract class AbstractComputationContext internal constructor() {
    internal abstract fun commit()
    internal abstract val newStates: MutableMap<ComputableValue<*>, ComputableValueState<*>>
    internal abstract val currentNode: ComputableValue<*>?
    internal abstract fun <T> ComputableValue<*>.withinStackScope(f: () -> T): T
    internal abstract fun openComputation()
    internal abstract val precommitTasks: MutableSet<ComputableValue<*>>
    internal abstract fun closeComputation(successfully: Boolean)
    internal abstract fun setNodeState(value: ComputableValue<*>, newState: ComputableValueState<*>)
    internal abstract fun <T> getNodeState(value: ComputableValue<T>): ComputableValueState<T>?

    /**
     * Specifies strategy of recomputing values that depend on some value that has already changed.
     * 
     * * When is true, dependent values are computed within the same transaction with dependency.
     *   Its advantage is cutting off recursive dependencies together with their causes.
     * * When is false, dependent values have no computed value until it is requested and generate it only on purpose.
     *   Its advantage is deferred computation that may be eliminated if it is not called anymore.
     */
    public abstract val recomputeEagerly: Boolean

    /**
     * Specifies whether [AbstractComputationContext] is watching history if it is supported by context, false otherwise.
     * @see ComputationContext.WithHistory
     */
    public abstract val isWatchingHistory: Boolean
    internal abstract var isCausedByUserAction: Boolean
}
