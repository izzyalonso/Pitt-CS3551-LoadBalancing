package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.annotation.GuardedBy
import com.izzyalonso.pitt.cs3551.annotation.VisibleForInnerAccess
import com.izzyalonso.pitt.cs3551.model.Message
import com.izzyalonso.pitt.cs3551.model.NodeInfo
import com.izzyalonso.pitt.cs3551.model.notices.NodesSpawned
import com.izzyalonso.pitt.cs3551.model.notices.ResponseMessage
import com.izzyalonso.pitt.cs3551.net.*
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
    @GuardedBy("this")
    private val nodes = mutableListOf<NodeInfo>()
    private var expectedNodeResponses = -1
    // Kept from the time a spin up request is received until the response is sent
    private var spawnRequestSocket: Socket? = null

    private lateinit var ipAddress: String


    /**
     * Starts the node controller.
     */
    fun start() {
        println("Starting node controller.")

        println("Fetching IP address...")
        ipAddress = getIpAddress()
        println("IP address: $ipAddress")

        // Start listening to a port, synchronously
        ServerSocketInterface(object: ServerSocketInterface.ListenerAdapter() {
            override fun onConnected(port: Int) {
                println("Listening to port $port for incoming connections.")
            }
            override fun onMessageReceived(message: Message, socket: Socket) {
                handleMessage(message, socket)
            }

            override fun onError(exception: Exception) {
                if (exception is PortAlreadyUsedException) {
                    println("FATAL: The requested port is already in use.")
                }
            }
        }).startListening(port ?: DEFAULT_PORT)
    }

    @VisibleForInnerAccess
    internal fun handleMessage(message: Message, socket: Socket) {
        message.spinUpNodes()?.let { request ->
            if (spawnRequestSocket != null) {
                socket.sendAndClose(Message.create(ResponseMessage.create(
                    "Controller is spawning nodes. Please, wait until it finishes."
                )))
                return
            }

            expectedNodeResponses = request.nodeCount()
            spawnRequestSocket = socket
            for (i in 0 until request.nodeCount()) {
                val builder = ProcessBuilder("java", "-jar", "lb.jar", "-n")
                val environment = builder.environment()
                environment[ENV_CONTROLLER_PORT] = (port ?: DEFAULT_PORT).toString()
                val process = builder.start()
                nodeProcesses.add(process)
            }
        }

        message.killNodes()?.let { _ ->
            if (spawnRequestSocket != null) {
                socket.sendAndClose(Message.create(ResponseMessage.create(
                    "Controller is spawning nodes. Please, wait until it finishes."
                )))
                return
            }

            nodeProcesses.forEach { process ->
                process.destroy()
            }
            nodeProcesses.clear()
            nodes.clear()
            expectedNodeResponses = -1
        }

        message.nodeOnline()?.let { response ->
            spawnRequestSocket?.let { srSocket ->
                val currentNodeCount: Int
                synchronized(this) {
                    nodes.add(NodeInfo.create(ipAddress, response.port()))
                    currentNodeCount = nodes.size
                }

                if (currentNodeCount == expectedNodeResponses) {
                    val nodesCopy = mutableListOf<NodeInfo>()
                    synchronized(this) {
                        nodesCopy.addAll(nodes)
                    }
                    srSocket.sendAndClose(Message.create(NodesSpawned.create(nodesCopy)))
                }
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 35991

        const val ENV_CONTROLLER_PORT = "LB_CONTROLLER_PORT"
    }
}
