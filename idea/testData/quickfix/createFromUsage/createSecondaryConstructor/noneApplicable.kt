// "Create secondary constructor" "true"
// ERROR: Primary constructor call expected

class CtorSecondary() {
    constructor(p: Int) : this()
}

fun construct() {
    val vA = <caret>CtorSecondary(2, 3)
}