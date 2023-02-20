import kotlin.reflect.KProperty


context (ComputingContext)
public class DependentComputation<T> public constructor(vararg names: String, private val f: () -> T) : ComputableValue<T>(*names) {
    internal var storedState: DependentComputationState<T> = DependentComputationState.NotInitialized(setOf(), setOf())
    private var state: DependentComputationState<T>
        get() = getNodeState(this) ?: storedState
        set(value) = setNodeState(this, value)

    internal fun invalidateAllFromThis() {
        fun <T> DependentComputation<T>.invalidateCurrent() {
            state = state.invalidated()
        }

        val visited = mutableSetOf<DependentComputation<*>>()
        fun invalidateAllFromThisImpl(current: DependentComputation<*>) {
            if (current in visited) return
            visited.add(current)
            current.invalidateCurrent()
            for (dependent in current.state.dependents) {
                invalidateAllFromThisImpl(dependent)
            }
        }
        invalidateAllFromThisImpl(this)
    }

    override val result: Result<T>
        get() = when (val oldState = state) {
            is DependentComputationState.WithValue -> oldState.cachedValue.also {
                openComputation()
                currentNode?.let { this dependsOn it }
                closeComputation(successfully = true) // todo history
                // todo multi-threaded
                // todo hide main, tests (including errors, regular exceptions, recover after failures, history)
                // todo effective sets
                // todo readme
            }
            is DependentComputationState.NotInitialized -> {
                openComputation()
                currentNode?.let { this dependsOn it }
                freeDependencies()
                val result = withinStackScope { runCatching { f() } }
                val notCaughtExceptions = result.exceptionOrNull() as? NotCaughtException
                if (notCaughtExceptions != null) {
                    closeComputation(successfully = false)
                    throw notCaughtExceptions
                }
                setNodeState(this, DependentComputationState.WithValue(state.dependents, state.dependencies, result))
                if (isEager) {
                    precommitTasks.addAll(this.state.dependents.map { dependent -> { dependent.result } })
                }
                closeComputation(successfully = true)
                result
            }
        }

    private infix fun dependsOn(dependent: DependentComputation<*>) {
        this.state = this.state.withNewDependent(dependent)
        @Suppress("UNCHECKED_CAST")
        dependent.state = dependent.state.withNewDependency(dependency = this) as DependentComputationState<Nothing>
    }
    
    private fun freeDependencies() {
        for (dependency in state.dependencies) {
            @Suppress("UNCHECKED_CAST")
            dependency.state = dependency.state.withoutDependent(this) as DependentComputationState<Nothing>
        }
        state = state.withoutAllDependencies()
    }


    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        names.add(property.name)
        return value
    }
}

public val <T> DependentComputation<T>.value: T
    get() = result.getOrThrow()

context (ComputingContext)
public class Parameter<T> public constructor(value: T, vararg names: String) : ComputableValue<T>(*names) {
    private var internalValue: T = value
        set(value) {
            if (value != field) {
                val oldValue = field
                field = value
                try {
                    openComputation()
                    dependentComputation.invalidateAllFromThis()
                    dependentComputation.result
                    closeComputation(successfully = true)
                } catch (e: NotCaughtException) {
                    closeComputation(successfully = false)
                    field = oldValue
                    throw e
                }
            }
        }
    private val dependentComputation = DependentComputation { internalValue }
    override val result: Result<T>
        get() = dependentComputation.result

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T = dependentComputation.getValue(thisRef, property)

    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        names.add(property.name)
        this.internalValue = value
    }
}

public fun main() {
    fun <T> logged(name: String, x: T) = x.also { println("Computing $name: $x") }
    
    with(ComputingContext(isEager = true)) {
        val x by DependentComputation { logged("x", 2) }
        var a by Parameter(5)
        val y by DependentComputation { logged("y", x + x * a) }
        val z by DependentComputation { logged("z", x + x) }
        println("$a $x $y $z")
        a++
        println("$a $x $y $z")
        
        println()
        println()
        
        var bParameter by Parameter("b")
        var cParameter by Parameter("c")
        val b by DependentComputation { logged("b", bParameter) }
        val c by DependentComputation { logged("c", cParameter) }
        val conditional by DependentComputation { logged("conditional", if (a % 2 == 0) b else c) }
        println("$b $c $conditional")
        bParameter += "1"
        println("$b $c $conditional")
        cParameter += "1"
        println("$b $c $conditional")
        a++
        println("$b $c $conditional")
        bParameter += "1"
        println("$b $c $conditional")
        cParameter += "1"
        println("$b $c $conditional")
        class Recur {
            val x by DependentComputation { this.y }
            val y: Int by DependentComputation { this.x }
        }
        val r = Recur()
        println(runCatching { r.x }.exceptionOrNull()?.message)
    }
}
