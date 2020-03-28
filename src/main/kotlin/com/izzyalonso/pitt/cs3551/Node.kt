package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.model.Message
import com.izzyalonso.pitt.cs3551.model.notices.NodeOnline
import com.izzyalonso.pitt.cs3551.net.ServerSocketInterface
import com.izzyalonso.pitt.cs3551.net.send
import java.net.Socket

class Node {
    fun start() {
        println("Starting node.")

        val controllerPort = System.getenv()[NodeController.ENV_CONTROLLER_PORT]?.toInt()
        if (controllerPort == null) {
            println("Environment variable ${NodeController.ENV_CONTROLLER_PORT} needs to be set.")
            return
        }

        ServerSocketInterface(object: ServerSocketInterface.ListenerAdapter() {
            override fun onConnected(port: Int) {
                // Notify the Controller the Node is ready
                // The node's controller will always be in localhost
                send(Message.create(NodeOnline.create(port)), "localhost", controllerPort)
            }

            override fun onMessageReceived(message: Message, socket: Socket) {

            }
        }).startListening()
    }
}
