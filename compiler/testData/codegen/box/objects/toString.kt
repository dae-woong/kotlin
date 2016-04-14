// See KT-4107 Generate toString() for objects
// WITH_RUNTIME

package test

import kotlin.test.assertEquals

// 1

object SimpleObject

// 2

object ObjectWithDeclaredToString {
    override fun toString() = "OK"
}

// 3

abstract class BaseClassWithOpenToString {
    override fun toString() = "Fail"
}

object ObjectWithOpenToStringInSuperclass : BaseClassWithOpenToString()

// 4

abstract class BaseClassWithFinalToString {
    override final fun toString() = "OK"
}

object ObjectWithFinalToStringInSuperclass : BaseClassWithFinalToString()

// -----

fun box(): String {
    assertEquals("SimpleObject", SimpleObject.toString())
    assertEquals("OK", ObjectWithDeclaredToString.toString())
    assertEquals("ObjectWithOpenToStringInSuperclass", ObjectWithOpenToStringInSuperclass.toString())
    assertEquals("OK", ObjectWithFinalToStringInSuperclass.toString())

    return "OK"
}
