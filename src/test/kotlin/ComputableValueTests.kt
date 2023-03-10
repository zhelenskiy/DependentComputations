import contexts.AbstractComputationContext
import contexts.ComputationContext
import contexts.transaction
import exceptions.NotInitializedException
import exceptions.RecursiveComputationException
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import values.ComputableValue
import values.Computation
import values.Parameter
import kotlin.reflect.KProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@Suppress("UNUSED_EXPRESSION")
class ComputableValueTests {
    private fun testComputationContexts(f: context(AbstractComputationContext) (context: AbstractComputationContext) -> Unit) {
        ComputationContext(computeEagerlyByDefault = true).let { f(it, it) }
        ComputationContext(computeEagerlyByDefault = false).let { f(it, it) }
        testComputationContextsWithHistory(f)
    }

    private fun testComputationContextsWithHistory(f: context(ComputationContext.WithHistory) (context: AbstractComputationContext) -> Unit) {
        ComputationContext.WithHistory(computeEagerly = true).let { f(it, it) }
        ComputationContext.WithHistory(computeEagerly = false).let { f(it, it) }
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
        assertEquals(if (computeEagerlyByDefault) 1 else 0, counter)
        assertEquals(1, value)
        assertEquals(1, counter)
        assertEquals(1, value)
        assertEquals(1, counter)

        val exception = Exception()
        val throwing by Computation<Int> { counter++; throw exception }
        assertEquals(if (computeEagerlyByDefault) 2 else 1, counter)
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
        assertEquals(if (computeEagerlyByDefault) 1 else 0, counter)
        assertEquals(10, value)
        assertEquals(1, counter)
        assertEquals(10, value)
        assertEquals(1, counter)
        parameter++
        assertEquals(if (computeEagerlyByDefault) 2 else 1, counter)
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
        assertEquals(if (computeEagerlyByDefault) 1 else 0, counter)
        assertEquals(1, trueValue)
        assertEquals(100, falseValue)
        assertEquals(true, condition)
        assertEquals(if (computeEagerlyByDefault) 1 else 0, counter)
        assertEquals(1, result)
        assertEquals(1, counter)

        trueValue++ // changing dependency value

        assertEquals(2, trueValue)
        assertEquals(100, falseValue)
        assertEquals(true, condition)
        assertEquals(if (computeEagerlyByDefault) 2 else 1, counter)
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
        assertEquals(if (computeEagerlyByDefault) 3 else 2, counter)
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
        assertEquals(if (computeEagerlyByDefault) 4 else 3, counter)
        assertEquals(102, result)
        assertEquals(4, counter) // changed: 3 -> 4
    }

    @Test
    fun recursion() = testComputationContexts { context ->
        var counter = 0
        var param by Parameter(100)
        val result by Computation { counter++; param + 1 }

        assertEquals(100, param)
        assertEquals(if (computeEagerlyByDefault) 1 else 0, counter)
        assertEquals(101, result)
        assertEquals(1, counter)

        class Recur1 {
            val x by Computation { this.y }
            val y: Int by Computation { this.x }
        }

        val r1 = Recur1()
        val exception1 = runCatching { r1.x }.exceptionOrNull()
        if (computeEagerlyByDefault) {
            assertIs<NullPointerException>(exception1, exception1?.stackTraceToString())
        } else {
            assertIs<RecursiveComputationException>(exception1, exception1?.stackTraceToString())
            assertEquals(listOf(setOf("x"), setOf("y"), setOf("x")), exception1.chain.map { it.names })
            assertEquals("Recursive chain: x => y => x", exception1.message)
        }

        var condition2 by Parameter(false)

        class Recur2 {
            val x by Computation { if (condition2) this.y else 0 }
            val y: Int by Computation { if (condition2) this.x else 0 }
        }

        val r2 = Recur2()
        val exception = runCatching { condition2 = true; r2.x }.exceptionOrNull()
        assertIs<RecursiveComputationException>(exception, exception?.stackTraceToString())
        assertEquals(listOf(setOf("x"), setOf("y"), setOf("x")), exception.chain.map { it.names })
        assertEquals("Recursive chain: x => y => x", exception.message)


        // Checking successful recovery
        param++

        assertEquals(101, param)
        assertEquals(if (computeEagerlyByDefault) 2 else 1, counter)
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
        assertEquals(if (computeEagerlyByDefault) 1 else 0, counter)
        assertEquals(100, result)
        assertEquals(1, counter)

        parameter++ // change 1

        assertEquals(11, parameter)
        assertEquals(if (computeEagerlyByDefault) 2 else 1, counter)
        assertEquals(121, result)
        assertEquals(2, counter)

        parameter++ // change 2

        assertEquals(12, parameter)
        assertEquals(if (computeEagerlyByDefault) 3 else 2, counter)
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

        undo()

        assertThrows<NotInitializedException> { result }
        assertThrows<NotInitializedException> { parameter }

        runCatching { undo() }.exceptionOrNull().let {
            assertIs<IllegalArgumentException>(it)
            assertEquals("Nothing to undo", it.message)
        }

        assertThrows<NotInitializedException> { result }
        assertThrows<NotInitializedException> { parameter }

        redo()

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
        assertEquals(if (computeEagerlyByDefault) 1 else 0, counter)
        assertEquals(100, result)
        assertEquals(1, counter)

        parameter = 20 // change 1

        assertEquals(20, parameter)

        assertEquals(if (computeEagerlyByDefault) 2 else 1, counter)
        assertEquals(400, result)
        assertEquals(2, counter)

        undo()

        assertEquals(10, parameter)
        assertEquals(2, counter)
        assertEquals(100, result)
        assertEquals(2, counter)

        parameter = newValue

        assertEquals(newValue, parameter)
        assertEquals(if (newValue == 10 || !computeEagerlyByDefault) 2 else 3, counter)
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
        assertEquals(if (computeEagerlyByDefault) 1 else 0, counter)
        assertEquals(100, result)
        assertEquals(1, counter)
        assertThrows<IllegalArgumentException> { redo() }
    }

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

        assertEquals(if (computeEagerlyByDefault) 1 else 0, counter)
        assertEquals(6, result)
        assertEquals(1, counter)

        parameter++

        assertEquals(if (computeEagerlyByDefault) 2 else 1, counter)
        assertEquals(12, result)
        assertEquals(2, counter)

        parameterDelegate.refresh()

        assertEquals(if (computeEagerlyByDefault) 3 else 2, counter)
        assertEquals(12, result)
        assertEquals(3, counter)

        parameterDelegate.refresh()

        assertEquals(if (computeEagerlyByDefault) 4 else 3, counter)
        assertEquals(12, result)
        assertEquals(4, counter)
    }

    @Test
    fun parameterInitializationUndoing() = testComputationContextsWithHistory {
        val computed by Computation { 2 }
        val parameter by Parameter(computed)
        assertEquals(2, parameter)
        undo()
        assertThrows<NotInitializedException> { parameter }
        undo()
        assertThrows<NotInitializedException> { computed }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun overriddenDefaultEagerness(eager: Boolean) = testComputationContexts {
        var counter = 0
        var parameter by Parameter(1)
        val delegate = Computation(computeEagerly = eager) { counter++; parameter }
        assertEquals(if (eager) 1 else 0, counter)
        val value by delegate
        assertEquals(if (eager) 1 else 0, counter)
        assertEquals(1, value)
        assertEquals(1, counter)
        parameter = 100
        assertEquals(if (eager) 2 else 1, counter)
        assertEquals(100, value)
        assertEquals(2, counter)
        delegate.refresh()
        assertEquals(if (eager) 3 else 2, counter)
        assertEquals(100, value)
        assertEquals(3, counter)
    }
    
    @Test
    fun logging() = testComputationContexts {
        val logger = StringBuilder()
        val original = Computation { "Hi!" }
        val modifiedParameter: ComputableValue<String> = object : ComputableValue<String> by original {
            override fun getValue(thisRef: Any?, property: KProperty<*>): String {
                return original.getValue(thisRef, property).also { logger.appendLine("$this retrieved value: $it") }
            }
        }
        val variable by modifiedParameter
        assertEquals("Hi!", variable)
        assertEquals("Hi!", variable)
        assertEquals("variable retrieved value: Hi!\n".repeat(2), logger.toString())
    }
    
    context(AbstractComputationContext)
    @Suppress("SameParameterValue")
    private fun <T> segmentTree(items: Array<Parameter<T>>, neutral: T, operation: (T, T) -> T): (IntRange) -> T {
        val n = items.size
        @Suppress("UNCHECKED_CAST")
        val segmentTreeImpl = arrayOfNulls<ComputableValue<T>>(2 * n - 1).apply {
            for (i in items.indices) {
                set(i + n - 1, items[i])
            }
            for (i in (n - 2) downTo 0) {
                set(i, Computation { operation(get(2 * i + 1)!!.value, get(2 * i + 2)!!.value) })
            }
        } as Array<ComputableValue<T>>
        return {
            val queryL = it.first
            val queryR = it.last + 1
            fun query(self: Int, selfL: Int, selfR: Int): T {
                if (queryL <= selfL && queryR >= selfR) return segmentTreeImpl[self].value
                if (queryR <= selfL || queryL >= selfR) return neutral
                val mid = (selfL + selfR) / 2
                return operation(query(self * 2 + 1, selfL, mid), query(self * 2 + 2, mid, selfR))
            }
            query(0, 0, n)
        }
    }
    
    @Test
    fun segmentTree() = testComputationContexts {
        val values = Array(4) { it + 1 }
        val parameters = Array(4) { Parameter(values[it]) }
        val query = segmentTree(parameters, 0) { a, b -> a + b }
        fun check() {
            for (i in 0..3) {
                for (j in i..3) {
                    assertEquals(values.slice(i..j).sum(), query(i..j), "$i..$j")
                }
            }
        }
        check()
        for (i in values.indices) {
            values[i] *= -1
            var parameter by parameters[i]
            parameter *= -1
            check()
        }
    }
    
    @Test
    fun transaction() = testComputationContexts {
        var counter = 0
        val parameterDelegate = Parameter(0)
        var parameter by parameterDelegate
        val computationDelegate = Computation { counter++; parameter }
        val computation by computationDelegate
        assertEquals(0, computation)
        assertEquals(1, counter)
        transaction {
            parameter++
            parameter++
            computationDelegate.refresh()
            computationDelegate.refresh()
            parameterDelegate.refresh()
            parameterDelegate.refresh()
        }
        assertEquals(2, computation)
        assertEquals(2, counter)
    }
}
