// TODO: Enable for JS when it supports Java class library.
// TARGET_BACKEND: JVM
class SomeClass { companion object }

fun box() = 
    if (SomeClass.toString() == "Companion")
        "OK"
    else
        "Fail: $SomeClass"
