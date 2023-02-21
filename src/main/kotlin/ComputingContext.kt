public abstract class AbstractComputingContext {
    internal abstract fun commit()
    internal abstract val newStates: MutableMap<ComputableValue<*>, ComputableValueState<*>>
    internal abstract val currentNode: ComputableValue<*>?
    internal abstract fun <T> ComputableValue<*>.withinStackScope(f: () -> T): T
    internal abstract fun openComputation()
    internal abstract val precommitTasks: MutableSet<ComputableValue<*>>
    internal abstract fun closeComputation(successfully: Boolean)
    internal abstract fun setNodeState(value: ComputableValue<*>, newState: ComputableValueState<*>)
    internal abstract fun <T> getNodeState(value: ComputableValue<T>): ComputableValueState<T>?
    public abstract val isEager: Boolean
}

public open class ComputingContext public constructor(public override val isEager: Boolean) : AbstractComputingContext() {
    override val newStates = mutableMapOf<ComputableValue<*>, ComputableValueState<*>>()
    private var openedComputations: Long = 0L
    private val isInsideTransaction: Boolean
        get() = openedComputations > 0L
    
    private class Stack {
        private val stack = mutableListOf<ComputableValue<*>>()
        private val stackContent = mutableListOf<ComputableValue<*>>()

        @Throws(RecursiveDependencyException::class)
        fun push(element: ComputableValue<*>) {
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
        
        fun peek(): ComputableValue<*>? = stack.lastOrNull()
        
        fun clear() {
            stack.clear()
            stackContent.clear()
        }
        
        val size: Int
            get() = stack.size
    }
    private val stack = Stack()
    
    override val currentNode: ComputableValue<*>?
        get() = stack.peek()
    
    override fun <T> ComputableValue<*>.withinStackScope(f: () -> T): T {
        stack.push(this)
        return try {
            f()
        } finally {
            if (stack.size > 0) {
                stack.pop()
            }
        }
    }
    
    override fun openComputation() {
        openedComputations++
    }
    
    override val precommitTasks: MutableSet<ComputableValue<*>> = mutableSetOf()
    
    override fun closeComputation(successfully: Boolean) {
        if (!isInsideTransaction) return
        if (successfully) {
            if (openedComputations == 1L) {
                try {
                    while (precommitTasks.isNotEmpty()) {
                        val first = precommitTasks.first()
                        precommitTasks.remove(first)
                        first.result
                    }
                } catch (e: NotCaughtException) {
                    closeComputation(successfully = false)
                    throw e
                }
                if (newStates.isNotEmpty()) {
                    commit()
                } else {
                    clear()
                }
            }
            openedComputations--
        } else {
            openedComputations = 0L
            revert()
        }
    }

    override fun commit() {
        for ((value, newState) in newStates) {
            @Suppress("UNCHECKED_CAST")
            value.storedState = newState as ComputableValueState<Nothing>
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

    override fun setNodeState(value: ComputableValue<*>, newState: ComputableValueState<*>) {
        when {
            !isInsideTransaction -> throw IllegalComputationStateException("Cannot change beyond transaction")
            value.storedState == newState -> newStates.remove(value)
            else -> newStates[value] = newState
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    override fun <T> getNodeState(value: ComputableValue<T>): ComputableValueState<T>? =
        newStates[value] as ComputableValueState<T>?
 
    public class WithHistory public constructor() : ComputingContext(isEager = true) {
        
        
        private var index = 0
        private class Operation(val changes: Map<ComputableValue<*>, Change>) {
            data class Change(val oldState: ComputableValueState<*>, val newState: ComputableValueState<*>)
        }
        private val operations = mutableListOf<Operation>()
        override fun commit() {
            val operation = Operation(newStates.mapValues { (value, newState) ->
                Operation.Change(oldState = value.storedState, newState = newState)
            })
            super.commit()
            while (index in operations.indices) operations.removeLast()
            operations.add(operation)
            index++
        }
        
        public fun undo() {
            require(index > 0) { "Nothing to undo" }
            index--
            for ((value, change) in operations[index].changes) {
                @Suppress("UNCHECKED_CAST")
                value.storedState = change.oldState as ComputableValueState<Nothing>
            }
        }
        
        public fun redo() {
            require(index < operations.size) { "Nothing to redo" }
            for ((value, change) in operations[index].changes) {
                @Suppress("UNCHECKED_CAST")
                value.storedState = change.newState as ComputableValueState<Nothing>
            }
            index++
        }
    }
}
