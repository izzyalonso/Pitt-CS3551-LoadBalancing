package com.izzyalonso.pitt.cs3551.util

class ConditionLock(private val condition: () -> Boolean) {
    fun await() {
        while (!condition()) {
            try {
                Thread.sleep(50)
            } catch (ix: InterruptedException) {
                // Nothing to do.
            }
        }
    }
}
