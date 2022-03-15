import kotlinx.atomicfu.*
import kotlin.test.*
import java.util.concurrent.atomic.*

class AAA {
    val a = 10
}

class IntArithmetic {
    val _x = atomic(0)

    //val au = AtomicIntegerFieldUpdater.newUpdater(AAA::class.java, "a")

//    fun foo() {
//        _x.compareAndSet(0, 5)
//    }
}

fun box(): String {
    val c = IntArithmetic()

    return "OK"
}