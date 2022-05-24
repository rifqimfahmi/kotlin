// TARGET_BACKEND: JVM
// WITH_RUNTIME
// WITH_STDLIB
abstract class TypeToken<T>

fun interface I {
    fun foo(): String
}

fun <T> foo() =
    I {
        (object : TypeToken<T>() {})::class.java.genericSuperclass.toString()
    }.foo()

fun box(): String =
    foo<String>().let { if (it == "TypeToken<T>") "OK" else it }
