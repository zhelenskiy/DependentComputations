package contexts

import values.ComputableValue
import states.ComputableValueState
import states.casted
import exceptions.IllegalComputationStateException
import exceptions.NotCaughtException
import exceptions.RecursiveComputationException
import exceptions.NotInitializedException
import values.Parameter

/**
 * Inheritor of [AbstractComputationContext] that does not support history operations.
 * 
 * @param computeEagerlyByDefault Initializes [ComputationContext.computeEagerlyByDefault] property.
 * @property computeEagerlyByDefault Implementation of [AbstractComputationContext.computeEagerlyByDefault] property.
 */
public open class ComputationContext public constructor(public override val computeEagerlyByDefault: Boolean) : AbstractComputationContext() {
    override var isCausedByUserAction: Boolean = false
        set(value) {
            if (value && !isInsideTransaction) throw IllegalComputationStateException("Cannot set cause beyond transaction")
            field = value
        }
    
    override val newStates = mutableMapOf<ComputableValue<*>, ComputableValueState<*>>()
    private var openedComputations: Long = 0L
    private val isInsideTransaction: Boolean
        get() = openedComputations > 0L
    
    private class Stack {
        private val stack = mutableListOf<ComputableValue<*>>()
        private val stackContent = mutableListOf<ComputableValue<*>>()

        @Throws(RecursiveComputationException::class)
        fun push(element: ComputableValue<*>) {
            if (stackContent.contains(element)) {
                val chain = buildList {
                    add(element)
                    for (frame in stack.asReversed()) {
                        add(frame)
                        if (frame == element) break
                    }
                }.asReversed()
                throw RecursiveComputationException(chain)
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
                        first.computeIfNotLazy()
                    }
                } catch (e: NotCaughtException) {
                    closeComputation(successfully = false)
                    throw e
                }
                if (newStates.isNotEmpty() || this.isCausedByUserAction) {
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
            value.storedState = newState.casted
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
        isCausedByUserAction = false
    }

    override fun setNodeState(value: ComputableValue<*>, newState: ComputableValueState<*>) {
        when {
            !isInsideTransaction -> throw IllegalComputationStateException("Cannot change beyond transaction")
            value.storedState == newState -> newStates.remove(value)
            else -> newStates[value] = newState
        }
    }
    
    override fun <T> getNodeState(value: ComputableValue<T>): ComputableValueState<T>? =
        newStates[value]?.casted

    override val isWatchingHistory: Boolean
        get() = false

    /**
     * Inheritor of [AbstractComputationContext] that supports history operations.
     * 
     * This [AbstractComputationContext] allows users to watch history by [WithHistory.undo] and [WithHistory.redo] operations.
     * 
     * During watching not last state it is forbidden to compute not yet initialized values as this would lead to implicit history rewriting.
     * It is unintended, so [ComputableValue.result] returns `null` and
     * [ComputableValue.value], [ComputableValue.getValue] fall with [NotInitializedException] in this case.
     * However, there is a way to rewrite history explicitly by calling [ComputableValue.refresh] or using [Parameter.setValue].
     * 
     * Singular state changes may be caused by performing some previously deferred lazy computations and
     * using them as [WithHistory.undo]/[WithHistory.redo] units would be misleading and inconvenient.
     * So, [WithHistory.undo]/[WithHistory.redo] checkpoints are blocks of actions
     * that start with explicit user actions described above and include all following implicit actions before next explicit one.
     * 
     * Even [Parameter] self-initialization causes a new checkpoint creation in history because otherwise the following [WithHistory.undo] call
     * would do different actions depending on the actual set value. However, no computations are performed in this case.
     *
     * @param computeEagerly Initializes [ComputationContext.WithHistory.computeEagerlyByDefault] property.
     */
    public class WithHistory public constructor(computeEagerly: Boolean = true) : ComputationContext(computeEagerlyByDefault = computeEagerly) {
        
        private var index = 0
        private class Operation(val isCausedByUserAction: Boolean, val changes: Map<ComputableValue<*>, Change>) {
            data class Change(val oldState: ComputableValueState<*>, val newState: ComputableValueState<*>)
        }
        private val operations = mutableListOf<Operation>()
        
        override val isWatchingHistory: Boolean
            get() = index < operations.size
        
        override fun commit() {
            val operation = Operation(
                isCausedByUserAction = this.isCausedByUserAction,
                changes = newStates.mapValues { (value, newState) ->
                    Operation.Change(oldState = value.storedState, newState = newState)
                }
            )
            super.commit()
            while (index in operations.indices) operations.removeLast()
            operations.add(operation)
            index++
        }

        /**
         * Moves to the previous checkpoint in history.
         * 
         * @throws IllegalArgumentException If there is no checkpoint before.
         */
        public fun undo() {
            val operationsToUndo: Int? = run {
                var result = 1
                while (index - result >= 0 && !operations[index - result].isCausedByUserAction) result++
                when {
                    index - result >= 0 -> result
                    result > 1 -> result - 1
                    else -> null
                }
            }
            require(operationsToUndo != null) { "Nothing to undo" }
            repeat(operationsToUndo) {
                index--
                for ((value, change) in operations[index].changes) {
                    value.storedState = change.oldState.casted
                }
            }
        }

        /**
         * Moves to the next checkpoint in history.
         *
         * @throws IllegalArgumentException If there is no checkpoint after.
         */
        public fun redo() {
            val operationsToRedo: Int? = run {
                if (index == operations.size) return@run null
                var result = 1
                while (index + result < operations.size && !operations[index + result].isCausedByUserAction) result++
                result
            }
            require(operationsToRedo != null) { "Nothing to redo" }
            repeat(operationsToRedo) {
                for ((value, change) in operations[index].changes) {
                    value.storedState = change.newState.casted
                }
                index++
            }
        }
    }
}
