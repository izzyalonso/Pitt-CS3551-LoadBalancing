package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.annotation.AnyThread
import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * A simple mechanism to keep track of the load.
 */
@AnyThread // NOTE: I don't care to tighten the critical section (records) as getLoad() should be pretty fast
class LoadTracker {

    @GuardedBy(who = "this")
    private val records = LinkedList<Record>()
    private var lastClick = 0L

    private var done = false


    /**
     * Signals the tracker the start of a sleep cycle.
     */
    fun startSleep() = synchronized(this) {
        if (done) {
            return
        }
        lastClick = System.nanoTime()
    }

    /**
     * Signals the tracker the end of a sleep cycle.
     */
    fun endSleep() = synchronized(this) {
        if (done) {
            return
        }
        click(Record.Type.SLEEP)
    }

    /**
     * Signals the tracker the start of a work cycle.
     */
    fun startWork() = synchronized(this) {
        if (done) {
            return
        }
        lastClick = System.nanoTime()
    }

    /**
     * Signals the tracker the end of a work cycle.
     */
    fun endWork() = synchronized(this) {
        if (done) {
            return
        }
        click(Record.Type.WORK)
    }

    /**
     * Adds the chunk of time to the record and locks the tracker. Idempotent.
     */
    fun done() = synchronized(this) {
        if (done) {
            return
        }

        val elapsed = System.nanoTime() - lastClick
        records.first?.addTime(elapsed)

        done = true
    }

    /**
     * A common function to perform a click.
     */
    private fun click(type: Record.Type) {
        // If the types mismatch, add a new record of length 0
        if (type != records.firstOrNull()?.type) {
            records.addFirst(Record(type))
        }

        // Add the elapsed time to the most recent record
        val elapsed = System.nanoTime() - lastClick
        records.firstOrNull()?.addTime(elapsed) // This is an optimization to keep records small

        // Record the click time
        lastClick = System.nanoTime()
    }

    /**
     * Gets the load as a unit percentage over the last n [seconds]. -1 to get a complete report.
     */
    fun getLoad(seconds: Int = 1): Double = synchronized(this) {
        // Just a definition; no records means no load. Shouldn't happen though
        if (records.isEmpty()) {
            return 0.0
        }

        // Translate the cap to nanos and calculate time elapsed since the last click
        val cap = if (seconds == -1) {
            Long.MAX_VALUE
        } else {
            TimeUnit.SECONDS.toNanos(seconds.toLong())
        }

        // The time elapsed since the last clock should be of the same type as the most recent block
        var totalTime = 0L
        var workTime = 0L

        // Gather the total and work times off of the record keeping in mind the cap
        for (record in records) {
            if (totalTime >= cap) {
                break
            }

            val delta = getDeltaWithCap(totalTime, record.timeNanos, cap)
            if (record.type == Record.Type.WORK) {
                workTime += delta
            }
            totalTime += delta
        }

        // The load is the fraction of
        return workTime.toDouble()/totalTime
    }

    /**
     * Calculates a delta that won't go over the requested cap.
     */
    private fun getDeltaWithCap(currentValue: Long, desiredDelta: Long, cap: Long): Long {
        return if (currentValue + desiredDelta < cap) {
            desiredDelta
        } else {
            cap - currentValue
        }
    }

    /**
     * A record. Holds a [type] and a [timeNanos].
     */
    private data class Record(internal val type: Type, internal var timeNanos: Long = 0) {

        /**
         * Adds a [deltaNanos] to the record.
         *
         * Should be positive. I, however, trust the correctness of my code, so skipping the check.
         */
        fun addTime(deltaNanos: Long) {
            timeNanos += deltaNanos
        }

        internal enum class Type {
            WORK, SLEEP
        }
    }
}
