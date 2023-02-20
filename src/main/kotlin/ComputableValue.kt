import java.util.*
import kotlin.reflect.KProperty

@DslMarker
public annotation class ComputationDsl

@ComputationDsl
public abstract class ComputableValue<T>(vararg names: String) {
    public abstract val result: Result<T>

    public abstract operator fun getValue(thisRef: Any?, property: KProperty<*>): T
    internal val names: SortedSet<String> = sortedSetOf(*names)
    override fun toString(): String {
        return if (names.isNotEmpty()) names.joinToString("/") else super.toString()
    }
}