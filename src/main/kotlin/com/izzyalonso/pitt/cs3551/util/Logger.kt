package com.izzyalonso.pitt.cs3551.util

import com.izzyalonso.pitt.cs3551.annotation.AnyThread


/**
 * Centralized logger allowing debug printouts.
 */
@AnyThread
object Logger {
    var debug = false

    /**
     * Debug log. Only prints in debug runs.
     */
    fun d(message: Any?) = synchronized(this) {
        if (debug) println(message)
    }

    /**
     * Info log. Always prints.
     */
    fun i(message: Any) = synchronized(this) {
        println(message)
    }
}
