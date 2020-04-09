package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.annotation.AnyThread
import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import com.izzyalonso.pitt.cs3551.annotation.VisibleForInnerAccess
import com.izzyalonso.pitt.cs3551.model.Message
import com.izzyalonso.pitt.cs3551.model.NodeInfo
import com.izzyalonso.pitt.cs3551.model.notices.NodeOnline
import com.izzyalonso.pitt.cs3551.model.notices.NodesSpawned
import com.izzyalonso.pitt.cs3551.model.notices.ResponseMessage
import com.izzyalonso.pitt.cs3551.net.*
import com.izzyalonso.pitt.cs3551.util.Collector
import com.izzyalonso.pitt.cs3551.util.Logger
import java.net.Socket


/**
 * Controls the lifecycle of worker nodes. Building a controller was an architectural design choice, as the
 * infrastructure that's available to me is severely limited. Ideally, a single node would run in a VM, however,
 * due to the limited amount of machines available to the entire class, I decided to build into the system the
 * ability to launch several nodes in a single VM. NodeController bridges the gap. The client instructs the
 * controller to start nodes in separate processes and kill them if necessary. Because I don't want to keep
 * track of the nodes manually, the controller also informs the client about the ports the nodes are listening to.
 *
 * By default, the NodeController listens to [DEFAULT_PORT], but this can be configured by passing a [port].
 */
class NodeController(private val port: Int?) {
    private val nodeProcesses = mutableListOf<Process>()
    private var nodeCollector: NodeCollector? = null

    private lateinit var ipAddress: String


    /**
     * Starts the node controller.
     */
    fun start() {
        Logger.i("Starting node controller.")

        Logger.i("Fetching IP address...")
        ipAddress = getIpAddress()
        Logger.i("IP address: $ipAddress")

        // Start listening to a port, synchronously
        ServerSocketInterface(object: ServerSocketInterface.ListenerAdapter() {
            override fun onConnected(port: Int) {
                Logger.i("Listening to port $port for incoming connections.")
            }
            override fun onMessageReceived(message: Message, socket: Socket) {
                handleMessage(message, socket)
            }

            override fun onError(exception: Exception) {
                if (exception is PortAlreadyUsedException) {
                    Logger.i("FATAL: The requested port is already in use.")
                }
            }
        }).startListening(port ?: DEFAULT_PORT)
    }

    @VisibleForInnerAccess
    internal fun handleMessage(message: Message, socket: Socket) {
        message.spinUpNodes()?.let { request ->
            Logger.i("Received a spin up request for ${request.nodeCount()} nodes. Currently working: ${nodeCollector.isWorking()}")
            if (nodeCollector.isWorking()) {
                socket.sendAndClose(Message.create(ResponseMessage.create(
                    "Controller is spawning nodes. Please, wait until it finishes."
                )))
                return
            }

            nodeCollector = NodeCollector(ipAddress, request.nodeCount())
            for (i in 0 until request.nodeCount()) {
                val builder = ProcessBuilder("java", "-jar", "lb.jar", "-n")
                val environment = builder.environment()
                environment[ENV_CONTROLLER_PORT] = (port ?: DEFAULT_PORT).toString()
                val process = builder.start()
                nodeProcesses.add(process)
            }

            // Let's not leak the socket outside of the thread
            val nodes = nodeCollector?.awaitAndGet()
            socket.sendAndClose(Message.create(NodesSpawned.create(nodes)))
        }

        message.killNodes()?.let { _ ->
            Logger.d("Received a kill request. Currently working: ${nodeCollector.isWorking()}")
            if (nodeCollector.isWorking()) {
                socket.sendAndClose(Message.create(ResponseMessage.create(
                    "Controller is spawning nodes. Please, wait until it finishes."
                )))
                return
            }

            nodeProcesses.forEach { it.destroy() }
            nodeProcesses.clear()
            nodeCollector = null
            socket.close()
        }

        message.nodeOnline()?.let { response ->
            Logger.d("Received a node online notice, working: ${nodeCollector.isWorking()}")
            nodeCollector?.handleResponse(response)
        }
    }

    companion object {
        const val DEFAULT_PORT = 35991

        const val ENV_CONTROLLER_PORT = "LB_CONTROLLER_PORT"
    }

    /**
     * Collects Nodes' online notices.
     */
    @AnyThread
    class NodeCollector(private val ipAddress: String, nodeCount: Int): Collector<List<NodeInfo>>(nodeCount) {

        @GuardedBy("this")
        private val nodes = mutableListOf<NodeInfo>()

        /**
         * Handles a notice and collects its data.
         */
        fun handleResponse(nodeOnline: NodeOnline) {
            synchronized(this) {
                nodes.add(NodeInfo.create(ipAddress, nodeOnline.port()))
            }
            countDown()
        }

        override fun get(): List<NodeInfo> {
            val ret = mutableListOf<NodeInfo>()
            synchronized(this) {
                ret.addAll(nodes)
            }
            return ret
        }
    }

    /**
     * Tells whether a [NodeCollector] is working. A non existent node collector is, by definition, not working.
     */
    private fun NodeCollector?.isWorking() = this?.isWorking() ?: false
}
