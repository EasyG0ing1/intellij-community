// "Add 1st parameter to constructor 'Foo'" "true"

data class Foo(val name: String)

fun test() {
    val foo = Foo(<caret>1, "name")
}