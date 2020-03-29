package com.izzyalonso.pitt.cs3551.net

import com.izzyalonso.pitt.cs3551.model.Message
import com.izzyalonso.pitt.cs3551.util.Logger
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * This interface listens to a port and delivers each received message along with its socket. Listening can be done
 * both, synchronously and asynchronously. Each connection is offloaded to a separate thread and the client is in
 * charge of closing sockets when they're done using them.
 */
class ServerSocketInterface @JvmOverloads constructor(private val listener: Listener, private val timeoutMillis: Int = 1000) {

    private var running: Boolean = false
    var connected: Boolean = false
        private set

    init {
        running = false
        connected = false
    }

    fun startListeningAsync(port: Int? = null) {
        Thread {
            startListening(port)
        }.start()
    }

    /**
     * Let the games begin. If [port] is not defined one will be assigned automagically.
     */
    fun startListening(port: Int? = null) {
        running = true
        try { // <- Exclusively for checking if the port is already in use, this is a fatal
            ServerSocket(port ?: 0).use { serverSocket ->
                // Initialize the socket
                connected = true
                listener.onConnected(serverSocket.localPort)
                serverSocket.soTimeout = timeoutMillis

                // Main loop, the socket will accept incoming connections until running becomes false
                while (running) {
                    try { // <- For the timer loop exception
                        val clientSocket = serverSocket.accept()

                        Thread { // Incoming connection, offloading to a new thread
                            try { // <- to separate IOExceptions I don't really care about from invalid messages
                                for (input in clientSocket.bufferedReader().lines()) {
                                    try { // <- Invalid messages, most likely a programmer error
                                        // Parse and deliver the message, along with the socket in a separate thread
                                        val message = Message.fromJson(input)
                                        Logger.d(message)
                                        listener.onMessageReceived(message, clientSocket)
                                        break // <- one line per incoming connection
                                    } catch (x: Exception) {
                                        Logger.d("Received invalid message:")
                                        Logger.d(input)
                                    }
                                }
                            } catch (iox: IOException) {
                                // Don't care if anything breaks here, we still got the outer loop
                                // iox.printStackTrace();
                            }
                        }.start() // <- Thread
                    } catch (stx: SocketTimeoutException) {
                        // System.out.println("Socket timeout. Checking if still running."); // <- Very spammy
                    }
                }
            }
        } catch (iox: IOException) {
            listener.onError(PortAlreadyUsedException())
            running = false
        } finally {
            if (connected) {
                connected = false
                listener.onDisconnected()
            }
        }
    }

    /**
     * Releases the socket.
     */
    fun stopListening() {
        running = false
    }


    /**
     * The interface's listener.
     */
    interface Listener {
        /**
         * Called when the interface has managed to open a socket on a port.
         */
        fun onConnected(port: Int)

        /**
         * Called when a message comes through.
         *
         * @param message the message.
         */
        fun onMessageReceived(message: Message, socket: Socket)

        /**
         * Called when an error worth communicating happens.
         *
         * @param exception the error.
         */
        fun onError(exception: Exception)

        /**
         * Called when the connection to the socket has been closed.
         */
        fun onDisconnected()
    }

    // Just an adapter to the listener, don't wanna clutter code in other places
    open class ListenerAdapter: Listener {
        override fun onConnected(port: Int) {}
        override fun onMessageReceived(message: Message, socket: Socket) {}
        override fun onError(exception: Exception) {}
        override fun onDisconnected() {}
    }
}
