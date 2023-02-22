public abstract class AbstractComputationContext {
    internal abstract fun commit()
    internal abstract val newStates: MutableMap<ComputableValue<*>, ComputableValueState<*>>
    internal abstract val currentNode: ComputableValue<*>?
    internal abstract fun <T> ComputableValue<*>.withinStackScope(f: () -> T): T
    internal abstract fun openComputation()
    internal abstract val precommitTasks: MutableSet<ComputableValue<*>>
    internal abstract fun closeComputation(successfully: Boolean)
    internal abstract fun setNodeState(value: ComputableValue<*>, newState: ComputableValueState<*>)
    internal abstract fun <T> getNodeState(value: ComputableValue<T>): ComputableValueState<T>?
    public abstract val recomputeEagerly: Boolean
    public abstract val isWatchingHistory: Boolean
    internal abstract var isCausedByUserAction: Boolean
}