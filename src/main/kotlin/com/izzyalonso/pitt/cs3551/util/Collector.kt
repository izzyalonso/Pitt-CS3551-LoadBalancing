package com.izzyalonso.pitt.cs3551.util

import java.util.concurrent.CountDownLatch


abstract class Collector<T>(count: Int) {
    private val latch = CountDownLatch(count)


    fun isWorking() = latch.count != 0L
    fun countDown() = latch.countDown()
    fun awaitAndGet(): T {
        latch.await()
        return get()
    }

    protected abstract fun get(): T
}
