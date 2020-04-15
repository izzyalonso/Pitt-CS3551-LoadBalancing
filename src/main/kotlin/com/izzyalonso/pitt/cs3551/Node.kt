package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import com.izzyalonso.pitt.cs3551.annotation.VisibleForInnerAccess
import com.izzyalonso.pitt.cs3551.model.*
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
import kotlin.math.abs

private const val imbalanceThreshold = 0.1

class Node {
    @GuardedBy("this")
    private val queue = LinkedList<Job>()
    private val running = AtomicBoolean(false)

    private val workLock = Object()

    private lateinit var thisNode: NodeInfo
    // The hierarchy this node is in charge of
    private lateinit var hierarchy: TreeNode

    // Maps a node to a level
    @GuardedBy("this") // <- also readonly
    private lateinit var nodeLevelMap: Map<NodeInfo, Int>
    // List of levels mapping node to load
    @GuardedBy("this") // <- includes all the maps as well
    private lateinit var levelLoads: List<MutableMap<NodeInfo, Double>>
    // This node's load, good to cache
    private var currentLoad = 0.0

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
            val work: Job?
            synchronized(this) {
                work = queue.removeFirstOrNull()
            }

            // Work or sleep
            if (work == null) {
                loadTracker.startSleep()
                synchronized(workLock) {
                    workLock.wait(250) // Go to sleep
                }
                loadTracker.endSleep()
            } else {
                loadTracker.startWork()
                doWork(work) // Execute the request
                loadTracker.endWork()
            }

            // Get the load over the last two seconds
            currentLoad = loadTracker.getLoad(2)

            var averageLevelLoad = currentLoad
            var lastLevelLoads = mutableListOf<Double>()
            for (i in levelLoads.size-1 downTo 0) {
                lastLevelLoads.clear()
                levelLoads[i].forEach {
                    lastLevelLoads.add(it.value)
                }
                lastLevelLoads.add(averageLevelLoad)
                averageLevelLoad = lastLevelLoads.average()
            }

            if (hierarchy.parent() == null) {
                // Evaluate whether a load balancing operation needs to occur
                var imbalance = false
                for (load in lastLevelLoads) {
                    if (abs(load - averageLevelLoad) > imbalanceThreshold) {
                        imbalance = true
                        break
                    }
                }

                if (imbalance) {
                    // Trigger load balance operation
                }
            } else {
                // Communicate subtree's load to parent
                val parent = hierarchy.parent()
                sendAsync(Message.create(LoadInfo.create(thisNode, averageLevelLoad)), parent.address(), parent.port())
            }
        }

        loadTracker.done()
    }

    private fun doWork(work: Job) = when (work.type()) {
        Job.Type.FIBONACCI -> runFibo(work.input())
        Job.Type.ERATOSTHENES -> runEratosthenes(work.input())
        Job.Type.SQUARE_SUM -> runSquareSum(work.input())
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

            buildInternalMappings(hierarchy)
            // Let other nodes know
            communicateHierarchy(hierarchy)

            socket.sendAndClose(Message.create(hierarchy))
        }

        message.hierarchy()?.let { hierarchy ->
            this.hierarchy = hierarchy
            buildInternalMappings(hierarchy)
            communicateHierarchy(hierarchy)
        }

        message.doWork()?.let { request ->
            synchronized(this) {
                queue.add(request)
            }
            synchronized(workLock) {
                workLock.notify()
            }
        }

        message.loadInfo()?.let { request ->
            val node = request.node()
            synchronized(this) {
                nodeLevelMap[node]?.let { level ->
                    levelLoads[level][node] = request.load()
                } // ?: throw(something went wrong)
            }
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

    /**
     * Looks much like [communicateHierarchy], just want to populate indices and mappings before communicating
     * externally.
     */
    private fun buildInternalMappings(hierarchy: TreeNode) {
        var level = 0
        val nodeLevelMap = mutableMapOf<NodeInfo, Int>()
        val levelLoads = mutableListOf<MutableMap<NodeInfo, Double>>()
        var currentNode = hierarchy
        while (!currentNode.isLeaf) {
            levelLoads.add(mutableMapOf())
            currentNode.children().forEach {
                if (it.node() == thisNode) {
                    currentNode = it
                } else {
                    nodeLevelMap[it.node()] = level
                    levelLoads[level][it.node()] = 0.0
                }
            }
            level++
        }
        synchronized(this) {
            this.nodeLevelMap = nodeLevelMap.toMap()
            this.levelLoads = levelLoads.toList()
        }
    }

    /**
     * Communicates the hierarchy to all the nodes this node is in charge of.
     */
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
