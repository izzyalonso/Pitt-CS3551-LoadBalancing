package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.annotation.AnyThread
import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import com.izzyalonso.pitt.cs3551.model.Message
import com.izzyalonso.pitt.cs3551.model.NodeInfo
import com.izzyalonso.pitt.cs3551.model.commands.BuildHierarchy
import com.izzyalonso.pitt.cs3551.model.commands.controller.KillNodes
import com.izzyalonso.pitt.cs3551.model.commands.controller.SpinUpNodes
import com.izzyalonso.pitt.cs3551.net.MessageCallback
import com.izzyalonso.pitt.cs3551.net.send
import com.izzyalonso.pitt.cs3551.net.sendAsync
import com.izzyalonso.pitt.cs3551.util.BaseCollector
import com.izzyalonso.pitt.cs3551.util.Logger


class Client(private val controllers: List<NodeInfo>, private val branchingFactor: Int = 2) {
    fun start() {
        // Spin up the nodes
        val nodeCollector = NodeCollector(controllers.size)
        val spinUpCommand = Message.create(SpinUpNodes.create(4))
        controllers.forEach {
            Logger.i("Sending spin up command to controller: $it")
            sendAsync(spinUpCommand, it.address(), it.port(), nodeCollector)
        }
        val nodes = nodeCollector.awaitAndGet()

        // Validation
        if (nodes.isEmpty()) {
            teardown("No nodes could be spun up.")
            return
        }

        // Tell one of the nodes it's in charge of building the hierarchy
        Logger.i("Building node hierarchy")
        val message = Message.create(BuildHierarchy.create(branchingFactor, nodes))
        val hierarchy = send(message, "localhost", nodes[0].port())
        Logger.i(hierarchy)

        // Clean up
        teardown()
    }

    private fun teardown(message: String? = null) {
        message?.let { Logger.i(it) }

        Logger.i("Tearing down nodes.")
        controllers.forEach {
            sendAsync(Message.create(KillNodes.create()), it.address(), it.port())
        }
    }

    @AnyThread
    private class NodeCollector(controllerCount: Int): BaseCollector<List<NodeInfo>>(controllerCount), MessageCallback {
        @GuardedBy("this")
        private val nodes = mutableListOf<NodeInfo>()

        override fun onResponseReceived(response: Message) {
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
