package com.izzyalonso.pitt.cs3551.net

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
fun Socket.sendAndClose(message: Message) = printWriter().use { writer ->
    writer.println(message)
}

/**
 * Sends a message through a socket.
 */
fun send(message: Message, socket: Socket) {
    socket.printWriter().use { writer ->
        writer.println(message.toJson())
    }
}

fun send(message: Message, address: String, port: Int): Message? {
    try {
        Socket(address, port).use { socket ->
            socket.printWriter().use { out ->
                // Send the message
                out.println(message.toJson())

                // Try reading a response
                try {
                    socket.bufferedReader().use { reader ->
                        // If the other end closes the connection the line will be null, signaling the end of the stream
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
