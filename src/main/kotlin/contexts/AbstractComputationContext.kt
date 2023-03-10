package contexts

import values.ComputableValue
import states.ComputableValueState
import values.PrimitiveComputableValue
import values.Computation

/**
 * Abstract class that defines common context for the single group of possibly connected computations.
 * 
 * It stores internals of dependency graph, manipulates the state of [ComputableValue] and history of its change, handles recursive chains errors.
 */
public abstract class AbstractComputationContext internal constructor() {
    internal abstract fun commit()
    internal abstract val newStates: MutableMap<PrimitiveComputableValue<*>, ComputableValueState<*>>
    internal abstract val currentNode: PrimitiveComputableValue<*>?
    internal abstract fun <T> PrimitiveComputableValue<*>.withinStackScope(f: () -> T): T
    internal abstract fun openComputation()
    internal abstract val precommitTasks: MutableSet<PrimitiveComputableValue<*>>
    internal abstract fun closeComputation(successfully: Boolean)
    internal abstract fun setNodeState(value: PrimitiveComputableValue<*>, newState: ComputableValueState<*>)
    internal abstract fun <T> getNodeState(value: PrimitiveComputableValue<T>): ComputableValueState<T>?

    /**
     * Specifies default strategy of computing values and recomputing them when some dependency changes.
     * This behaviour may be overridden by [Computation.computeEagerly].
     * 
     * * When is true, dependent values are computed within the same transaction with dependency.
     *   Its advantages are cutting off recursive dependencies together with their causes and workaround of the delegate/object initialization order issue.
     * * When is false, dependent values have no computed value until it is requested and generate it only on purpose.
     *   Its advantage is deferred computation that may be eliminated if it is not called anymore.
     */
    public abstract val computeEagerlyByDefault: Boolean

    /**
     * Specifies whether [AbstractComputationContext] is watching history if it is supported by context, false otherwise.
     * @see ComputationContext.WithHistory
     */
    public abstract val isWatchingHistory: Boolean
    internal abstract var isCausedByUserAction: Boolean
}
