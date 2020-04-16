package com.izzyalonso.pitt.cs3551.util

import com.izzyalonso.pitt.cs3551.annotation.AnyThread
import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import java.util.concurrent.CountDownLatch


abstract class BaseCollector<T>(count: Int) {
    private val latch = CountDownLatch(count)


    fun isWorking() = latch.count != 0L
    fun countDown() = latch.countDown()
    fun awaitAndGet(): T {
        latch.await()
        return get()
    }

    protected abstract fun get(): T
}

@AnyThread
class Collector<T>(count: Int) {
    private val latch = CountDownLatch(count)
    @GuardedBy("this")
    private val items = mutableListOf<T>()

    fun isWorking() = latch.count != 0L
    fun add(item: T) {
        if (!isWorking()) {
            return
        }
        synchronized(this) {
            items.add(item)
        }
        latch.countDown()
    }
    fun awaitAndGet(): List<T> {
        latch.await()
        val itemsCopy: List<T>
        synchronized(this) {
            itemsCopy = items.toList()
        }
        return itemsCopy
    }
}

fun <T>Collector<T>?.isWorking() = this?.isWorking() ?: false


@AnyThread
class MappingCollector<K, V>(count: Int) {
    private val latch = CountDownLatch(count)
    @GuardedBy("this")
    private val items = mutableMapOf<K, V>()

    fun isWorking() = latch.count != 0L
    fun add(key: K, value: V) {
        if (!isWorking()) {
            return
        }
        synchronized(this) {
            items[key] = value
        }
        latch.countDown()
    }
    fun awaitAndGet(): Map<K, V> {
        latch.await()
        val itemsCopy: Map<K, V>
        synchronized(this) {
            itemsCopy = items.toMap()
        }
        return itemsCopy
    }
}

fun <K, V>MappingCollector<K, V>?.isWorking() = this?.isWorking() ?: false
