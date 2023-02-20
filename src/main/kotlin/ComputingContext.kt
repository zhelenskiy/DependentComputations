public class ComputingContext public constructor(public val isEager: Boolean) {
    private val newStates = mutableMapOf<DependentComputation<*>, DependentComputationState<*>>()
    private var openedComputations: Long = 0L
    private val isInsideTransaction: Boolean
        get() = openedComputations > 0L
    
    private class Stack {
        private val stack = mutableListOf<DependentComputation<*>>()
        private val stackContent = mutableListOf<DependentComputation<*>>()

        @Throws(RecursiveDependencyException::class)
        fun push(element: DependentComputation<*>) {
            if (stackContent.contains(element)) {
                val chain = buildList {
                    add(element)
                    for (frame in stack.asReversed()) {
                        add(frame)
                        if (frame == element) break
                    }
                }.asReversed()
                throw RecursiveDependencyException(chain)
            }
            stack.add(element)
            stackContent.add(element)
        }

        fun pop() {
            stackContent.remove(stack.removeLast())
        }
        
        fun peek(): DependentComputation<*>? = stack.lastOrNull()
        
        fun clear() {
            stack.clear()
            stackContent.clear()
        }
        
        val size: Int
            get() = stack.size
    }
    private val stack = Stack()
    
    internal val currentNode: DependentComputation<*>?
        get() = stack.peek()
    
    internal fun <T> DependentComputation<*>.withinStackScope(f: () -> T): T {
        stack.push(this)
        return try {
            f()
        } finally {
            if (stack.size > 0) {
                stack.pop()
            }
        }
    }
    
    internal fun openComputation() {
        openedComputations++
    }
    
    internal val precommitTasks: MutableSet<() -> Unit> = mutableSetOf()
    
    internal fun closeComputation(successfully: Boolean) {
        if (!isInsideTransaction) return
        if (successfully) {
            if (openedComputations == 1L) {
                try {
                    precommitTasks.forEach { it() }
                } catch (e: NotCaughtException) {
                    closeComputation(successfully = false)
                    throw e
                }
                commit()
            }
            openedComputations--
        } else {
            openedComputations = 0L
            revert()
        }
    }

    private fun commit() {
        for ((value, newState) in newStates) {
            @Suppress("UNCHECKED_CAST")
            value.storedState = newState as DependentComputationState<Nothing>
        }
        clear()
    }

    private fun revert() {
        clear()
    }

    private fun clear() {
        precommitTasks.clear()
        newStates.clear()
        stack.clear()
    }

    internal fun setNodeState(value: DependentComputation<*>, newState: DependentComputationState<*>) {
        if (!isInsideTransaction) throw IllegalComputationStateException("Cannot change beyond transaction")
        newStates[value] = newState
    }
    
    @Suppress("UNCHECKED_CAST")
    internal fun <T> getNodeState(value: DependentComputation<T>): DependentComputationState<T>? = newStates[value] as DependentComputationState<T>?
}
