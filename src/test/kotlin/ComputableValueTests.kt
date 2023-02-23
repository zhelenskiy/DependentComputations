import contexts.AbstractComputationContext
import contexts.ComputationContext
import exceptions.NotInitializedException
import exceptions.RecursiveComputationException
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import values.Computation
import values.Parameter
import java.lang.Exception
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ComputableValueTests {
    private fun testComputationContexts(f: context(AbstractComputationContext) (context: AbstractComputationContext) -> Unit) {
        ComputationContext(recomputeEagerly = true).let { f(it, it) }
        ComputationContext(recomputeEagerly = false).let { f(it, it) }
        testComputationContextsWithHistory(f)
    }

    private fun testComputationContextsWithHistory(f: context(ComputationContext.WithHistory) (context: AbstractComputationContext) -> Unit) {
        ComputationContext.WithHistory(recomputeEagerly = true).let { f(it, it) }
        ComputationContext.WithHistory(recomputeEagerly = false).let { f(it, it) }
    }

    @Test
    fun parameterValue() = testComputationContexts {
        var parameter by Parameter(10)
        assertEquals(10, parameter)
        parameter *= 10
        assertEquals(100, parameter)
    }
    
    @Test
    fun independentComputation() = testComputationContexts {
        var counter = 0
        val value by Computation { counter++; 1 }
        assertEquals(0, counter)
        assertEquals(1, value)
        assertEquals(1, counter)
        assertEquals(1, value)
        assertEquals(1, counter)
        
        val exception = Exception()
        val throwing by Computation<Int> { counter++; throw exception }
        assertEquals(1, counter)
        assertEquals(exception, runCatching { throwing }.exceptionOrNull())
        assertEquals(2, counter)
        assertEquals(exception, runCatching { throwing }.exceptionOrNull())
        assertEquals(2, counter)
        assertEquals(exception, runCatching { throwing }.exceptionOrNull())
    }
    
    @Test
    fun dependentComputation() = testComputationContexts {
        var counter = 0
        var parameter by Parameter(10)
        val value by Computation { counter++; parameter }
        assertEquals(0, counter)
        assertEquals(10, value)
        assertEquals(1, counter)
        assertEquals(10, value)
        assertEquals(1, counter)
        parameter++
        assertEquals(if (recomputeEagerly) 2 else 1, counter)
        assertEquals(11, value)
        assertEquals(2, counter)
    }
    
    @Test
    fun changingDependencies() = testComputationContexts {
        var counter = 0
        var trueValue by Parameter(1)
        var falseValue by Parameter(100)
        var condition by Parameter(true)
        val result by Computation { counter++; if (condition) trueValue else falseValue }
        assertEquals(0, counter)
        assertEquals(1, trueValue)
        assertEquals(100, falseValue)
        assertEquals(true, condition)
        assertEquals(0, counter)
        assertEquals(1, result)
        assertEquals(1, counter)
        
        trueValue++ // changing dependency value
        
        assertEquals(2, trueValue)
        assertEquals(100, falseValue)
        assertEquals(true, condition)
        assertEquals(if (recomputeEagerly) 2 else 1, counter)
        assertEquals(2, result)
        assertEquals(2, counter) // changed: 1 -> 2
        
        falseValue++ // changing other value

        assertEquals(2, trueValue)
        assertEquals(101, falseValue)
        assertEquals(true, condition)
        assertEquals(2, counter) // didn't change: 2 -> 2
        assertEquals(2, result)
        assertEquals(2, counter)
        
        condition = false // changing set of dependencies

        assertEquals(2, trueValue)
        assertEquals(101, falseValue)
        assertEquals(false, condition)
        assertEquals(if (recomputeEagerly) 3 else 2, counter)
        assertEquals(101, result)
        assertEquals(3, counter) // changed: 2 -> 3
        
        trueValue++ // changing old dependency, that is no more a dependency

        assertEquals(3, trueValue)
        assertEquals(101, falseValue)
        assertEquals(false, condition)
        assertEquals(3, counter)
        assertEquals(101, result)
        assertEquals(3, counter) // didn't change: 3 -> 3
        
        falseValue++ // changing new dependency that was not one before

        assertEquals(3, trueValue)
        assertEquals(102, falseValue)
        assertEquals(false, condition)
        assertEquals(if (recomputeEagerly) 4 else 3, counter)
        assertEquals(102, result)
        assertEquals(4, counter) // changed: 3 -> 4
    }
    
    @Test
    fun recursion() = testComputationContexts { context ->
        var counter = 0
        var param by Parameter(100)
        val result by Computation { counter++; param + 1 }
        
        assertEquals(100, param)
        assertEquals(0, counter)
        assertEquals(101, result)
        assertEquals(1, counter)
        
        class Recur {
            val x by Computation { this.y }
            val y: Int by Computation { this.x }
        }

        val r = Recur()
        val exception = runCatching { r.x }.exceptionOrNull()
        assertIs<RecursiveComputationException>(exception)
        assertEquals(listOf(setOf("x"), setOf("y"), setOf("x")), exception.chain.map { it.names })
        assertEquals("Recursive chain: x => y => x", exception.message)

        // Checking successful recovery
        param++

        assertEquals(101, param)
        assertEquals(if (recomputeEagerly) 2 else 1, counter)
        assertEquals(102, result)
        assertEquals(2, counter)
        
        if (context is ComputationContext.WithHistory) with(context) {
            undo()

            assertEquals(100, param)
            assertEquals(2, counter)
            assertEquals(101, result)
            assertEquals(2, counter)
            
            redo()

            assertEquals(101, param)
            assertEquals(2, counter)
            assertEquals(102, result)
            assertEquals(2, counter)
        }
    }
    
    @Test
    fun undoRedo() = testComputationContextsWithHistory {
        var counter = 0
        var parameter by Parameter(10)
        val result by Computation { counter++; parameter * parameter }
        
        assertEquals(10, parameter)
        assertEquals(0, counter)
        assertEquals(100, result)
        assertEquals(1, counter)
        
        parameter++ // change 1

        assertEquals(11, parameter)
        assertEquals(if (recomputeEagerly) 2 else 1, counter)
        assertEquals(121, result)
        assertEquals(2, counter)
        
        parameter++ // change 2

        assertEquals(12, parameter)
        assertEquals(if (recomputeEagerly) 3 else 2, counter)
        assertEquals(144, result)
        assertEquals(3, counter)
        
        undo()

        assertEquals(11, parameter) // old value
        assertEquals(3, counter) // does not recount
        assertEquals(121, result) // old value
        assertEquals(3, counter) // does not recount
        
        undo()

        assertEquals(10, parameter) // very older value
        assertEquals(3, counter) // does not recount
        assertEquals(100, result) // very old value
        assertEquals(3, counter) // does not recount
        
        runCatching { undo() }.exceptionOrNull().let {
            assertIs<IllegalArgumentException>(it)
            assertEquals("Nothing to undo", it.message)
        }

        assertEquals(10, parameter) // very older value
        assertEquals(3, counter) // does not recount
        assertEquals(100, result) // very old value
        assertEquals(3, counter) // does not recount
        
        redo()

        assertEquals(11, parameter) // old value
        assertEquals(3, counter) // does not recount
        assertEquals(121, result) // old value
        assertEquals(3, counter) // does not recount
        
        redo()

        assertEquals(12, parameter) // new value
        assertEquals(3, counter) // does not recount
        assertEquals(144, result) // new value
        assertEquals(3, counter) // does not recount

        runCatching { redo() }.exceptionOrNull().let {
            assertIs<IllegalArgumentException>(it)
            assertEquals("Nothing to redo", it.message)
        }

        assertEquals(12, parameter) // new value
        assertEquals(3, counter) // does not recount
        assertEquals(144, result) // new value
        assertEquals(3, counter) // does not recount
    }
    
    @ParameterizedTest
    @ValueSource(ints = [10, 20, 30])
    fun undoDetach(newValue: Int) = testComputationContextsWithHistory {
        var counter = 0
        var parameter by Parameter(10)
        val delegate = Computation { counter++; parameter * parameter }
        val result by delegate

        assertEquals(10, parameter)
        assertEquals(0, counter)
        assertEquals(100, result)
        assertEquals(1, counter)

        parameter = 20 // change 1

        assertEquals(20, parameter)
        
        assertEquals(if (recomputeEagerly) 2 else 1, counter)
        assertEquals(400, result)
        assertEquals(2, counter)
        
        undo()

        assertEquals(10, parameter)
        assertEquals(2, counter)
        assertEquals(100, result)
        assertEquals(2, counter)
        
        parameter = newValue

        assertEquals(newValue, parameter)
        assertEquals(if (newValue == 10 || !recomputeEagerly) 2 else 3, counter)
        assertEquals(newValue * newValue, result)
        assertEquals(if (newValue == 10) 2 else 3, counter)
        
        
        counter = 0
        
        undo()

        assertEquals(10, parameter)
        assertEquals(0, counter)
        assertEquals(100, result)
        assertEquals(0, counter)
        
        delegate.refresh()

        assertEquals(10, parameter)
        assertEquals(if (recomputeEagerly) 1 else 0, counter)
        assertEquals(100, result)
        assertEquals(1, counter)
        assertThrows<IllegalArgumentException> { redo() }
    }
    
    @Suppress("UNUSED_EXPRESSION")
    @Test
    fun recomputeHistory() = testComputationContextsWithHistory {
        var x by Parameter(5)
        x++
        val resultDelegate = Computation { x * x }
        val result by resultDelegate
        result
        undo()
        assertNull(resultDelegate.result)
        assertThrows<NotInitializedException> { result }
        runCatching { result }.exceptionOrNull().let {
            assertIs<NotInitializedException>(it)
            assertEquals(resultDelegate, it.value)
            val expectedMessage = "Property result is not available at this moment of history. " +
                    "Refresh it explicitly to clear the following history."
            assertEquals(expectedMessage, it.message)
        }
    }
    
    @Test
    fun refresh() = testComputationContexts {
        var counter1 = 0
        var counter2 = 0
        val p1Delegate = Computation { counter1++; "f" }
        val p2Delegate = Parameter("g")
        val p1 by p1Delegate
        val p2 by p2Delegate
        val rDelegate = Computation { counter2++; p1 + p2 }
        val r by rDelegate
        
        fun checkValues() {
            assertEquals("f", p1)
            assertEquals("g", p2)
            assertEquals("fg", r)
        }
        
        checkValues()
        assertEquals(1, counter1)
        assertEquals(1, counter2)
        
        rDelegate.refresh()

        checkValues()
        assertEquals(1, counter1)
        assertEquals(2, counter2)
        
        p1Delegate.refresh()

        checkValues()
        assertEquals(2, counter1)
        assertEquals(3, counter2)
        
        p2Delegate.refresh()

        checkValues()
        assertEquals(2, counter1)
        assertEquals(4, counter2)
    }
    
    @Test
    fun rhombusEvaluation() = testComputationContexts {
        var counter = 0
        val parameterDelegate = Parameter(1)
        var parameter by parameterDelegate
        val parameterCopyDelegate = Computation { parameter }
        val parameterCopy by parameterCopyDelegate
        val b by Computation { parameterCopy + 1 }
        val c by Computation { parameterCopy + 2 }
        val result by Computation { counter++; b * c }
        
        assertEquals(0, counter)
        assertEquals(6, result)
        assertEquals(1, counter)
        
        parameter++

        assertEquals(if (recomputeEagerly) 2 else 1, counter)
        assertEquals(12, result)
        assertEquals(2, counter)
        
        parameterDelegate.refresh()

        assertEquals(if (recomputeEagerly) 3 else 2, counter)
        assertEquals(12, result)
        assertEquals(3, counter)
        
        parameterDelegate.refresh()

        assertEquals(if (recomputeEagerly) 4 else 3, counter)
        assertEquals(12, result)
        assertEquals(4, counter)
    }
}
