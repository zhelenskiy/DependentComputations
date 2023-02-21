import kotlinx.collections.immutable.persistentSetOf
import kotlin.reflect.KProperty


context (AbstractComputingContext)
public class DependentComputation<T> public constructor(vararg names: String, private val f: () -> T) :
    ComputableValue<T>(initialState = ComputableValueState.NotInitialized(persistentSetOf(), persistentSetOf()), *names) {
    override fun computeResult(): Result<T> = withinStackScope { runCatching { f() } }

    override fun refresh()  {
        openComputation()
        withinStackScope {
            invalidateAllFromThis()
        }
        result
        closeComputation(successfully = true)
    }
}

context (AbstractComputingContext)
public class Parameter<T> public constructor(value: T, vararg names: String) :
    ComputableValue<T>(initialState = ComputableValueState.WithValue(persistentSetOf(), persistentSetOf(), Result.success(value)), *names) {
    override fun computeResult(): Result<T> = (state as ComputableValueState.WithValue<T>).cachedValue
    
    private fun updateValue(value: T) {
        if (value == this.value) return
        openComputation()
        state = ComputableValueState.WithValue(
            dependencies = state.dependencies,
            dependents = state.dependents,
            cachedValue = Result.success(value),
        )
        refresh()
        closeComputation(successfully = true)
    }

    public operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        names.add(property.name)
        updateValue(value)
    }
    
    override fun refresh()  {
        openComputation()
        val newState = state
        withinStackScope {
            invalidateAllFromThis()
        }
        state = newState
        for (dependent in state.dependents) {
            dependent.result
        }
        closeComputation(successfully = true)
    }
}

public fun main() {
    fun <T> logged(name: String, x: T) = x.also { println("Computing $name: $x") }

    with(ComputingContext.WithHistory()) {
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
        println("$b $c $conditional")
        undo()
        println("$b $c $conditional")
        undo()
        println("$b $c $conditional")
        redo()
        println("$b $c $conditional")
        redo()
        println("$b $c $conditional")

        println()
        println()

        var x1 by Parameter("init")
        println(x1)
        x1 = "set"
        println(x1)
        undo()
        println(x1)
        redo()
        println(x1)
        undo()
        println(x1)
        x1 = "set"
        println(x1)
        
        println()
        println()
       
        val p1Delegate = DependentComputation { logged("p1", "f") }
        val p2Delegate = Parameter("g")
        val p1 by p1Delegate
        val p2 by p2Delegate
        val r1Delegate = DependentComputation { logged("r1", p1 + p2) }
        val r1 by r1Delegate
        println("$p1 $p2 $r1")
        r1Delegate.refresh()
        println("$p1 $p2 $r1")
        p1Delegate.refresh()
        println("$p1 $p2 $r1")
        p2Delegate.refresh()
        println("$p1 $p2 $r1")
    }
}
