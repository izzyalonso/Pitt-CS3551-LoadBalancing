package com.izzyalonso.pitt.cs3551


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

    when (components[0]) {
        "-x" -> runClient()
        "-c" -> runNodeController(map["--port"]?.first()?.toInt())
        "-n" -> runNode()
        else -> printHelp()
    }
}

private fun getComponents(map: Map<String, List<String>>): List<String> {
    val components = mutableListOf<String>()
    arrayOf("-x", "-c", "-n").forEach {
        if (map.containsKey(it)) {
            components.add(it)
        }
    }
    return components
}

private fun runClient() {
    Client().start()
}

private fun runNodeController(overridePort: Int?) {
    NodeController(overridePort).start()
}

private fun runNode() {
    Node().start()
}

private fun printHelp() {
    println("Use one of the following arguments:")
    println("\t-x to run the client.")
    println("\t-c to run a node controller.")
    println("\t-n to run a node. This operation should only be performed by a controller in prod.")
    println("\t--port P, where P is the port number, to override the node controller's operating port.")
    println("\t-h for help.")
}
