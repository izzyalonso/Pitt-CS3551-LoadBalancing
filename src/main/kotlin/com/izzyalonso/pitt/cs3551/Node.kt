package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import com.izzyalonso.pitt.cs3551.annotation.VisibleForInnerAccess
import com.izzyalonso.pitt.cs3551.model.Message
import com.izzyalonso.pitt.cs3551.model.NodeInfo
import com.izzyalonso.pitt.cs3551.model.TreeNode
import com.izzyalonso.pitt.cs3551.model.Work
import com.izzyalonso.pitt.cs3551.model.commands.BuildHierarchy
import com.izzyalonso.pitt.cs3551.model.notices.NodeOnline
import com.izzyalonso.pitt.cs3551.net.ServerSocketInterface
import com.izzyalonso.pitt.cs3551.net.send
import com.izzyalonso.pitt.cs3551.net.sendAndClose
import com.izzyalonso.pitt.cs3551.net.sendAsync
import java.lang.Integer.min
import java.net.Socket
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.NoSuchElementException

class Node {
    @GuardedBy("this")
    private val queue = LinkedList<Work>()
    private val running = AtomicBoolean(false)

    private val workLock = Object()

    private lateinit var thisNode: NodeInfo
    // The hierarchy this node is in charge of
    private lateinit var hierarchy: TreeNode

    private lateinit var loadTracker: LoadTracker


    fun start() {
        println("Starting node.")
        running.set(true)

        loadTracker = LoadTracker()

        val controllerPort = System.getenv()[NodeController.ENV_CONTROLLER_PORT]?.toInt()
        if (controllerPort == null) {
            println("Environment variable ${NodeController.ENV_CONTROLLER_PORT} needs to be set.")
            return
        }

        ServerSocketInterface(object: ServerSocketInterface.ListenerAdapter() {
            override fun onConnected(port: Int) {
                // It's good to know who we are
                thisNode = NodeInfo.create("localnode", port)

                // Notify the Controller the Node is ready
                // The node's controller will always be in localhost
                send(Message.create(NodeOnline.create(port)), "localhost", controllerPort)
            }

            override fun onMessageReceived(message: Message, socket: Socket) {
                handleMessage(message, socket)
            }

            override fun onDisconnected() {
                running.set(false)
            }
        }).startListeningAsync()

        while (running.get()) {
            // Dequeue work
            val work: Work?
            synchronized(this) {
                work = queue.removeFirstOrNull()
            }

            if (work == null) {
                loadTracker.startSleep()
                workLock.wait() // Go to sleep
            } else {
                loadTracker.startWork()
                doWork(work) // Execute the request
            }
        }

        loadTracker.done()
    }

    private fun doWork(work: Work) = when (work.type()) {
        Work.Type.FIBONACCI -> runFibo(work.input())
        Work.Type.ERATOSTHENES -> runEratosthenes(work.input())
        Work.Type.SQUARE_SUM -> runSquareSum(work.input())
        null -> 0 // Never gonna happen, just keeping the linter happy. Eyeroll.
    }

    private fun runFibo(nth: Int): Int {
        var f1 = 0
        var f2 = 1
        repeat(min(nth-2, 0)) {
            val tmp = f1+f2
            f1 = f2
            f2 = tmp
        }
        return if (nth == 0) {
            f1
        } else {
            f2
        }
    }

    private fun runEratosthenes(nth: Int): Int {
        val field = BooleanArray(nth+1) { true }
        for (i in 2 until nth) {
            if (!field[i]) {
                continue
            }
            for (j in 2*i until nth step i) {
                field[j] = false
            }
        }
        return field[nth].int()
    }

    private fun runSquareSum(nth: Int): Int {
        var result = 0
        repeat(nth) {
            repeat(nth) {
                result++
            }
        }
        return result
    }

    @VisibleForInnerAccess
    internal fun handleMessage(message: Message, socket: Socket) {
        message.buildHierarchy()?.let { request ->
            hierarchy = buildHierarchy(request)

            // Let other nodes know
            communicateHierarchy(hierarchy)

            socket.sendAndClose(Message.create(hierarchy))
        }

        message.hierarchy()?.let { hierarchy ->
            this.hierarchy = hierarchy
            communicateHierarchy(hierarchy)
        }

        message.doWork()?.let { request ->
            synchronized(this) {
                queue.add(request)
            }
            workLock.notify()
        }
    }

    fun buildHierarchy(request: BuildHierarchy): TreeNode {
        // Create a list of leaves
        val leaves = LinkedList<TreeNode>()
        request.nodes().forEach {
            leaves.add(TreeNode.create(it))
        }

        // First leaf will be the root
        val root = leaves.remove()
        val parents = LinkedList<TreeNode>()
        parents.add(root)

        val extraChildren = request.branchingFactor()-1

        while (leaves.isNotEmpty()) {
            val parent = parents.remove()
            parent.children().add(TreeNode.create(parent.node()))
            parents.add(parent.children()[0]) // We can probably be smarter about this, comes a point when we don't need to add more
            repeat(min(extraChildren, leaves.size)) {
                val nextLeaf = leaves.remove().assignParent(parent.node())
                // Exploiting the fact that a member of an autovalue class is not mutable
                // Really shouldn't be doing this, but who cares
                parent.children().add(nextLeaf)
                parents.add(nextLeaf)
            }
        }

        return root
    }

    private fun communicateHierarchy(hierarchy: TreeNode) {
        var currentNode = hierarchy
        while (!currentNode.isLeaf) {
            currentNode.children().forEach {
                if (it.node() == thisNode) {
                    currentNode = it
                } else {
                    sendAsync(Message.create(it), it.node().address(), it.node().port())
                }
            }
        }
    }

    /**
     * Dequeues an item but returns null instead of throwing if there ain't any.
     *
     * NOTE: not thread safe.
     */
    private fun <T>LinkedList<T>.removeFirstOrNull(): T? {
        return try {
            removeFirst()
        } catch (nsex: NoSuchElementException) {
            null
        }
    }

    private fun Boolean.int() = if (this) {
        1
    } else {
        0
    }
}

fun main() {
    val nodes = mutableListOf<NodeInfo>()
    for (i in 0 until 4) {
        nodes.add((NodeInfo.create("node", i)))
    }

    println(Node().buildHierarchy(BuildHierarchy.create(2, nodes)).toJson())
}
