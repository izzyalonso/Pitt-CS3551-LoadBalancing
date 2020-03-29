package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.annotation.AnyThread
import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import com.izzyalonso.pitt.cs3551.model.Message
import com.izzyalonso.pitt.cs3551.model.NodeInfo
import com.izzyalonso.pitt.cs3551.model.commands.controller.KillNodes
import com.izzyalonso.pitt.cs3551.model.commands.controller.SpinUpNodes
import com.izzyalonso.pitt.cs3551.net.MessageCallback
import com.izzyalonso.pitt.cs3551.net.sendAsync
import com.izzyalonso.pitt.cs3551.util.Collector
import com.izzyalonso.pitt.cs3551.util.Logger

class Client(private val controllers: List<NodeInfo>, private val branchingFactor: Int = 2) {
    fun start() {
        // Spin up the nodes
        val nodeCollector = ControllerCollector(controllers.size)
        val spinUpCommand = Message.create(SpinUpNodes.create(branchingFactor))
        controllers.forEach {
            Logger.i("Sending spin up command to controller: $it")
            sendAsync(spinUpCommand, it.address(), it.port(), nodeCollector)
        }
        nodeCollector.awaitAndGet()

        // Teardown
        controllers.forEach {
            sendAsync(Message.create(KillNodes.create()), it.address(), it.port())
        }
    }

    @AnyThread
    private class ControllerCollector(controllerCount: Int): Collector<List<NodeInfo>>(controllerCount), MessageCallback {
        @GuardedBy("this")
        private val nodes = mutableListOf<NodeInfo>()

        override fun onResponseReceived(response: Message) {
            response.nodesSpawned()?.let { notice ->
                Logger.d("Nodes collected: ${notice.nodes()}")
                synchronized(this) {
                    nodes.addAll(notice.nodes())
                }

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
