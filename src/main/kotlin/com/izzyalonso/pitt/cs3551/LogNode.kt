package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.model.Message
import com.izzyalonso.pitt.cs3551.net.ServerSocketInterface
import com.izzyalonso.pitt.cs3551.util.Logger
import java.net.Socket

const val logNodePort = 65439

class LogNode {
    fun start() {
        ServerSocketInterface(object: ServerSocketInterface.ListenerAdapter() {
            override fun onMessageReceived(message: Message, socket: Socket) {
                message.log()?.let {
                    Logger.i(it)
                }
            }
        }).startListening(logNodePort)
    }
}