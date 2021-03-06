package com.izzyalonso.pitt.cs3551

import com.izzyalonso.pitt.cs3551.model.NodeInfo
import com.izzyalonso.pitt.cs3551.util.Logger
import java.io.File


fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printHelp()
        return
    }

    // NOTE: NOT MY WORK. See https://stackoverflow.com/a/54027443
    val map = args.fold(Pair(emptyMap<String, List<String>>(), "")) { (map, lastKey), elem ->
        if (elem.startsWith("-"))  Pair(map + (elem to emptyList()), elem)
        else Pair(map + (lastKey to map.getOrDefault(lastKey, emptyList()) + elem), lastKey)
    }.first
    // END NOTE

    if (map.containsKey("-h")) {
        printHelp()
        return
    }

    val components = getComponents(map)
    if (components.size != 1) {
        println("Exactly one of the following must be specified as an argument: -x, -c, -n")
        printHelp()
        return
    }

    Logger.debug = map.containsKey("-d")
    Logger.d("Debug run.")

    when (components[0]) {
        "-x" -> runClient(map["--controllers"]?.get(0))
        "-c" -> runNodeController(map["--port"]?.first()?.toInt())
        "-n" -> runNode()
        "-l" -> runLogger()
        else -> printHelp()
    }
}

private fun getComponents(map: Map<String, List<String>>): List<String> {
    val components = mutableListOf<String>()
    arrayOf("-x", "-c", "-n", "-l").forEach {
        if (map.containsKey(it)) {
            components.add(it)
        }
    }
    return components
}

private fun runClient(controllerFilePath: String?) {
    if (controllerFilePath == null) {
        print("When running the client, make sure you're specifying the path to the controller csv file")
        printHelp()
        return
    }

    val controllers = mutableListOf<NodeInfo>()
    File(controllerFilePath).readLines().forEach {
        val parts = it.split(",")
        controllers.add(NodeInfo.create(parts[0], parts[1].toInt()))
    }

    Client(controllers).start()
}

private fun runNodeController(overridePort: Int?) {
    NodeController(overridePort).start()
}

private fun runNode() {
    Node().start()
}

private fun runLogger() {
    LogNode().start()
}

private fun printHelp() {
    println("Use one of the following arguments:")
    println("\t-x to run the client.")
    println("\t-c to run a node controller.")
    println("\t-n to run a node. This operation should only be performed by a controller in prod.")
    println("\t-l to tun the logger.")
    println("\t--port P (node controller only) to override the node controller's operating port, where P is the port number.")
    println("\t--controllers F (client only) to let the client where the node controllers are, where F is the path to the csv file.")
    println("\t-d to print out debug logs.")
    println("\t-h for help.")
}
