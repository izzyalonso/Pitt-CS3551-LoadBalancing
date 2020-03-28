package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.model.Message
import com.izzyalonso.pitt.cs3551.model.commands.controller.SpinUpNodes
import com.izzyalonso.pitt.cs3551.net.send

class Client {
    fun start() {
        println(send(Message.create(SpinUpNodes.create(2)), "localhost", NodeController.DEFAULT_PORT))
    }
}