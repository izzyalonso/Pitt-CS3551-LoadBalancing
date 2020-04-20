package com.izzyalonso.pitt.cs3551.net

import com.izzyalonso.pitt.cs3551.logNodePort
import com.izzyalonso.pitt.cs3551.model.Message
import java.io.*
import java.net.Socket
import java.net.URL
import kotlin.text.Charsets.UTF_8


private fun URL.bufferedReader() = BufferedReader(InputStreamReader(openStream()))

fun Socket.printWriter() = PrintWriter(OutputStreamWriter(getOutputStream(), UTF_8), true)
fun Socket.bufferedReader() = BufferedReader(InputStreamReader(getInputStream()))

/**
 * Returns the external IP address of the machine.
 *
 * NOTE: This function performs blocking network IO.
 */
fun getIpAddress() = URL("http://checkip.amazonaws.com/").bufferedReader().use { reader ->
    reader.readLine() ?: throw RuntimeException("The IP address couldn't be determined")
}

fun Socket.send(message: Message) = printWriter().println(message.toJson())
// Send and close closes before the other side gets to read the message.
// This is obv no bueno, but I'm keeping it around cause I'm curious.
fun Socket.sendAndClose(message: Message) = printWriter().use { writer ->
    writer.println(message)
}

fun sendLog(message: String) {
    sendAsync(Message.create(message), "localhost", logNodePort)
}

fun sendLogSync(message: String) {
    send(Message.create(message), "localhost", logNodePort)
}

/**
 * Sends a [message] to a machine at the provided [address] and [port] asynchronously. Optionally, you can register
 * a [callback] to get notified iff and when the recipient sends a response.
 */
fun sendAsync(message: Message, address: String, port: Int, callback: MessageCallback? = null) {
    Thread {
        send(message, address, port)?.let {
            callback?.onResponseReceived(it)
        }
    }.start()
}

/**
 * Sends a [message] to a machine at the provided [address] and [port] synchronously.
 */
fun send(message: Message, address: String, port: Int): Message? {
    try {
        Socket(address, port).use { socket ->
            socket.printWriter().let { out ->
                // Send the message
                out.println(message.toJson())

                try { // Try reading a response
                    socket.bufferedReader().let { reader ->
                        // If the other end closes the connection the line will be null
                        val line = reader.readLine()
                        if (line != null) {
                            // Read and deliver the response
                            return@send Message.fromJson(line)
                        }
                    }
                } catch (iox: IOException) {
                    // DRC
                }
            }
        }
    } catch (x: Exception) {
        // x.printStackTrace();
    }

    // No message to return
    return null
}

/**
 * Callback for async send operations. Because TCP ensures delivery ans we know the protocol we don't need a
 * delivered function. We'll assume the message got to its destination. If timing is a concern (ie. you need
 * to know when an operation completes), please ensure the recipient sends an acknowledgement message back or
 * somehow build that into the protocol.
 */
interface MessageCallback {
    fun onResponseReceived(response: Message)
}
