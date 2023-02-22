import kotlinx.collections.immutable.persistentSetOf
import kotlin.reflect.KProperty


context (AbstractComputationContext)
public class Computation<T> public constructor(vararg names: String, private val f: () -> T) :
    ComputableValue<T>(initialState = ComputableValueState.NotInitialized(persistentSetOf(), persistentSetOf()), *names) {
    override fun computeResult(): Result<T> = withinStackScope { runCatching { f() } }

    override fun refresh()  {
        openComputation()
        isCausedByUserAction = true
        withinStackScope {
            invalidateAllFromThis()
        }
        if (recomputeEagerly) {
            result ?: throw IllegalComputationStateException("Refresh is caused by user")
        }
        closeComputation(successfully = true)
    }
}

context (AbstractComputationContext)
public class Parameter<T> public constructor(value: T, vararg names: String) :
    ComputableValue<T>(initialState = ComputableValueState.WithValue(persistentSetOf(), persistentSetOf(), Result.success(value)), *names) {
    override fun computeResult(): Result<T> = (state as ComputableValueState.WithValue<T>).cachedValue
    
    private fun updateValue(value: T) {
        openComputation()
        isCausedByUserAction = true
        if (value != this.value) {
            state = ComputableValueState.WithValue(
                dependencies = state.dependencies,
                dependents = state.dependents,
                cachedValue = Result.success(value),
            )
            refresh()
        }
        closeComputation(successfully = true)
    }

    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        names.add(property.name)
        updateValue(value)
    }
    
    override fun refresh()  {
        openComputation()
        isCausedByUserAction = true
        val existingState = state
        withinStackScope {
            invalidateAllFromThis()
        }
        state = existingState
        if (recomputeEagerly) {
            for (dependent in state.dependents) {
                dependent.result ?: throw IllegalComputationStateException("Refresh is caused by user")
            }
        }
        closeComputation(successfully = true)
    }
}
