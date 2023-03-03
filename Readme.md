# Dependent computations

This project provides convenient way to perform multiple connected computations.

Let us deep into using small examples.

All the examples below require to be executed in the context which is described [further](#context).

Each computation is performed only once and is then cached.
```kotlin
var counter = 0
val parameter by Parameter(5)
val result by Computation { counter++; 2 + parameter }
println(parameter) // prints 5
println(result) // prints 7
println(result) // prints 7
println(counter) // prints 1
```

Each computation may depend on others.
```kotlin
val parameter by Parameter(5)
val intermediate by Computation { 2 + parameter }
val result by Computation { intermediate * 2 }
println(parameter) // prints 5
println(intermediate) // prints 7
println(result) // prints 14
```

When dependency value is changed, the dependents are automatically recomputed.
```kotlin
var parameter by Parameter(5)
val intermediate by Computation { 2 + parameter }
val result by Computation { intermediate * 2 }
println(parameter) // prints 5
println(intermediate) // prints 7
println(result) // prints 14
parameter++
println(parameter) // prints 6
println(intermediate) // prints 8
println(result) // prints 16
```

Set of dependencies may change because of parameter changes.
```kotlin
var trueValue by Parameter("true value")
var falseValue by Parameter("false value")
var condition by Parameter(true)
val result by Computation { if (condition) trueValue else falseValue }

// result depends on condition and trueValue now
println(result) // prints "true value"
falseValue = "other1"
println(result) // still prints "true value"
trueValue = "other2"
println(result) // prints "other2"

condition = false

// result depends on condition and falseValue now
println(result) // prints "other1"
falseValue = "other3"
println(result) // prints "other3"
trueValue = "other4"
println(result) // still prints "other3"
```

Computable values can be refreshed explicitly.
```kotlin
val p1Delegate = Computation { counter1++; "f" }
val p2Delegate = Parameter("g")
val p1 by p1Delegate
val p2 by p2Delegate
val rDelegate = Computation { counter2++; p1 + p2 }
val r by rDelegate

println("$p1 $p2 $r") // prints "f g fg"
p1Delegate.refresh() // refreshes p1, r
p2Delegate.refresh() // refreshes p2, r
rDelegate.refresh() // refreshes r
```

There is an option to use delegates manually.
```kotlin
val delegate = Computation { 'f' }
println(delegate.value) // prints "f"
println(delegate.result) // prints "Success('f')"
```

## Context

Using delegates declared above requires evaluation contexts that store configuration of evaluation and states, add other helpful methods and handle errors such as recursive computation.

Contexts are inherited from abstract `AbstractComputationContext`.

### `ComputationContext`

Its simplified signature is
`class ComputationContext(val computeEagerlyByDefault: Boolean)`.

Computing result and recomputing it when `refresh` is called or dependencies changed may be either eager or lazy. So, `computeEagerlyByDefault` specifies the default behaviour. `Computation`s can change it. Advantages of the lazy computing are elimination of useless computations and workaround of the delegate/object initialization order issue. Advantage of the eager computing is cutting off recursive computations when they are only caused.

When recursive computation is found, a `RecursiveComputationException` is thrown. Depending on actual implementation of the recursion, another exception may occur, e.g. when you try to access not yet initialized property.

#### Example:

```kotlin
with(ComputationContext(computeEagerly = true)) {
    val x by Parameter(2)
    val y by Parameter(3)
    val z by Computation { x * y }
    println(z) // prints(6)
    y++
    println(z) // prints(8)
}
```

### `ComputationContext.WithHistory`

Its simplified signature is
`class WithHistory(val computeEagerlyByDefault: Boolean)`. This is an inheritor of `ComputationContext` that supports watching history and operations `undo`, `redo` to move backward and forward in history correspondingly. `undo`, `redo` and watching historic values does not cause new computations.

`AbstractComputationContext` has property `isWatchingHistory` which returns `true` for `ComputationContext.WithHistory` if there are some actions to `redo`.

During watching not last state it is forbidden to compute not yet initialized values as this would lead to implicit history rewriting. It is unintended, so `ComputableValue::result` returns `null` and
`ComputableValue::value`, `ComputableValue::getValue` fall with `NotInitializedException` in this case.
However, there is a way to rewrite history explicitly by calling `ComputableValue.refresh` or using `Parameter.setValue`.

Singular state changes may be caused by performing some previously deferred lazy computations and using them as `undo`/`redo` units would be misleading and inconvenient. So, `undo`/`redo` checkpoints are blocks of actions that start with explicit user actions described above and include all following implicit actions before next explicit one.

Even `Parameter` self-initialization causes a new checkpoint creation in history because otherwise the following `undo` call would do different actions depending on the actual set value. However, no computations are performed in this case.

#### Examples:

```kotlin
with(ComputationContext.WithHistory(computeEagerly = true)) {
    val x by Parameter(2)
    val y by Parameter(3)
    val z by Computation { x * y }
    println(y) // prints(3)
    println(z) // prints(6)
    
    y++
    println(y) // prints(4)
    println(z) // prints(8)
    
    undo()
    println(y) // prints(3)
    println(z) // prints(6)
    
    redo()
    println(y) // prints(4)
    println(z) // prints(8)
}
```

```kotlin
with(ComputationContext.WithHistory(computeEagerly = false)) {
    val x by Parameter(2)
    val y by Parameter(3)
    val z by Computation { x * y }
    println(y) // prints(3)
    println(z) // prints(6)
    
    y++
    println(y) // prints(4)
    // println(z) // z is not computed anymore after y change
    
    undo()
    println(y) // prints(3)
    println(z) // falls with NotInitializedException because computeEagerly is false
}
```

## Known issues

Interaction with other variables cannot be done from different threads. This limitation will be removed not earlier than Kotlin property delegates with context receivers will take receivers from call-site but not from delegate creation site as now.
