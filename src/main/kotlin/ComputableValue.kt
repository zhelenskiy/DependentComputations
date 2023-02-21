import java.util.*
import kotlin.reflect.KProperty

@DslMarker
public annotation class ComputationDsl

context(AbstractComputingContext)
@ComputationDsl
public abstract class ComputableValue<T> internal constructor(initialState: ComputableValueState<T>, vararg names: String) {
    internal val names: SortedSet<String> = sortedSetOf(*names)
    override fun toString(): String {
        return if (names.isNotEmpty()) names.joinToString("/") else super.toString()
    }
    internal var storedState: ComputableValueState<T> = initialState
    internal var state: ComputableValueState<T>
        get() = getNodeState(this) ?: storedState
        set(value) = setNodeState(this, value)
    
    protected abstract fun computeResult(): Result<T>
    public val result: Result<T>
        get() = when (val oldState = state) {
            is ComputableValueState.WithValue -> oldState.cachedValue.also {
                openComputation()
                currentNode?.let { this dependsOn it }
                closeComputation(successfully = true)
                // todo multi-threaded when delegating context receivers will be call-site-wise
                // todo hide main, tests (including errors, regular exceptions, recover after failures, history, refresh, refresh which could make refresh same node twice+)
                // todo readme
            }

            is ComputableValueState.NotInitialized -> {
                openComputation()
                currentNode?.let { this dependsOn it }
                freeDependencies()
                val result = computeResult()
                val notCaughtException = result.exceptionOrNull() as? NotCaughtException
                if (notCaughtException != null) {
                    closeComputation(successfully = false)
                    throw notCaughtException
                }
                state = ComputableValueState.WithValue(state.dependents, state.dependencies, result)
                if (isEager) {
                    this.state.dependents.forEach {
                        @Suppress("UNCHECKED_CAST")
                        it.state = it.state.invalidated() as ComputableValueState<Nothing>
                    }
                    precommitTasks.addAll(this.state.dependents)
                }
                closeComputation(successfully = true)
                result
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
        @Suppress("UNCHECKED_CAST")
        dependent.state = dependent.state.withNewDependency(dependency = this) as ComputableValueState<Nothing>
    }

    private fun freeDependencies() {
        for (dependency in state.dependencies) {
            @Suppress("UNCHECKED_CAST")
            dependency.state = dependency.state.withoutDependent(this) as ComputableValueState<Nothing>
        }
        state = state.withoutAllDependencies()
    }


    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        names.add(property.name)
        return value
    }

    public val value: T
        get() = result.getOrThrow()
    
    public abstract fun refresh()
}
