package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.annotation.AnyThread
import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import com.izzyalonso.pitt.cs3551.model.Job
import com.izzyalonso.pitt.cs3551.model.Message
import com.izzyalonso.pitt.cs3551.model.NodeInfo
import com.izzyalonso.pitt.cs3551.model.TreeNode
import com.izzyalonso.pitt.cs3551.model.commands.BuildHierarchy
import com.izzyalonso.pitt.cs3551.model.commands.controller.KillNodes
import com.izzyalonso.pitt.cs3551.model.commands.controller.SpinUpNodes
import com.izzyalonso.pitt.cs3551.net.MessageCallback
import com.izzyalonso.pitt.cs3551.net.send
import com.izzyalonso.pitt.cs3551.net.sendAsync
import com.izzyalonso.pitt.cs3551.net.sendLog
import com.izzyalonso.pitt.cs3551.util.BaseCollector
import com.izzyalonso.pitt.cs3551.util.Logger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random


class Client(private val controllers: List<NodeInfo>, private val branchingFactor: Int = 2) {
    fun start() {
        sendLog("\n\n---STARTING RUN---\n")

        // Spin up the nodes
        Logger.i("Controller count: ${controllers.size}")
        val nodeCollector = NodeCollector(controllers.size)
        val spinUpCommand = Message.create(SpinUpNodes.create(4))
        controllers.forEach {
            Logger.i("Sending spin up command to controller: $it")
            sendAsync(spinUpCommand, it.address(), it.port(), nodeCollector)
        }
        val nodes = nodeCollector.awaitAndGet().sorted()

        // Validation
        if (nodes.isEmpty()) {
            teardown("No nodes could be spun up.")
            return
        }

        Logger.i("Nodes: $nodes")

        // Tell one of the nodes it's in charge of building the hierarchy
        Logger.i("Building node hierarchy")
        val message = Message.create(BuildHierarchy.create(branchingFactor, nodes))
        val hierarchy = send(message, nodes[0].address(), nodes[0].port())
        hierarchy?.hierarchy()?.let {
            printHierarchy(it)
        } ?: Logger.i("No Hierarchy")

        // Wait for a few seconds
        Thread.sleep(2000)

        val workloadController = WorkloadController(nodes)
        workloadController.start()
        var quit = false
        while (!quit) {
            val input = readLine() ?: ""
            val tokenized = input.split(" ")
            when (tokenized[0]) {
                "Q" -> quit = true
                "P" -> workloadController.periodicity.set(tokenized[1].toInt())
                "S" -> workloadController.jobSize.set(tokenized[1].toInt())
                "V" -> workloadController.variability.set(tokenized[1].toInt())
                else -> println("Unrecognized command")
            }
        }
        workloadController.stop()

        // Clean up
        teardown()
    }

    private fun printHierarchy(root: TreeNode, prepend: String = "") {
        Logger.i("${prepend}${root.node()}")
        Logger.i("${prepend}Parent: ${root.parent()}")
        if (!root.isLeaf) {
            Logger.i("${prepend}Children:")
            root.children().forEach {
                printHierarchy(it, "${prepend}\t")
            }
        }
    }

    private fun teardown(message: String? = null) {
        message?.let { Logger.i(it) }

        Logger.i("Tearing down nodes.")
        controllers.forEach {
            send(Message.create(KillNodes.create()), it.address(), it.port())
        }
    }

    @AnyThread
    class WorkloadController(private val nodes: List<NodeInfo>) {
        private var running = AtomicBoolean(false)
        // How often to assign jobs
        var periodicity = AtomicInteger(100)
        // How big the jobs inputs should be; quadratic for effect
        var jobSize = AtomicInteger(10)
        // Job size variability; plus or minus half after squaring
        var variability = AtomicInteger(2)

        fun start() {
            Thread {
                running.set(true)
                while (running.get()) {
                    val node = nodes[listOf(0, 2).random()] // Add a skew
                    val job = Job.create(Job.Type.SQUARE_SUM, getSize())
                    val message = Message.create(job)
                    //Logger.i("Sending $message to $node")
                    sendAsync(message, node.address(), node.port())
                    Thread.sleep(periodicity.get().toLong())
                }
            }.start()
        }

        private fun getSize(): Int {
            return periodicity.get()*3 + Random.nextInt(periodicity.get())
        }

        fun stop() {
            running.set(false)
        }
    }

    @AnyThread
    class NodeCollector(controllerCount: Int): BaseCollector<List<NodeInfo>>(controllerCount), MessageCallback {
        @GuardedBy(who = "this")
        private val nodes = mutableListOf<NodeInfo>()

        override fun onResponseReceived(response: Message) {
            Logger.d(response)
            response.nodesSpawned()?.let { notice ->
                Logger.d("Nodes collected: ${notice.nodes()}")
                synchronized(this) {
                    nodes.addAll(notice.nodes())
                }
                countDown()
            } ?: throw RuntimeException("Expected spawned nodes in collector callback.")
        }

        override fun get(): List<NodeInfo> {
            val ret = mutableListOf<NodeInfo>()
            synchronized(this) {
                ret.addAll(nodes)
            }
            return ret
        }
    }
}
