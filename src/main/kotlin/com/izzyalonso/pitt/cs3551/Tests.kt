package com.izzyalonso.pitt.cs3551

import java.util.concurrent.CountDownLatch


fun main() {
    test_awaitOnLatch_whenCountIsAlready0() // Success
}

fun test_awaitOnLatch_whenCountIsAlready0() {
    val latch = CountDownLatch(0)
    latch.await()
    println("Hayo!")
}