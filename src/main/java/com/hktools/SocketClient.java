// java
package com.hktools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class SocketClient {
    private static final Logger logger = LoggerFactory.getLogger(SocketClient.class);

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final boolean autoReconnect;

    private Socket socket;
    private BufferedWriter writer;
    private Thread readerThread;
    private volatile boolean running = false;

    // Fields to support reconnect
    private Thread reconnectThread;
    private volatile boolean stopRequested = false;
    private Consumer<String> lineConsumer; // stored so reconnect can restart reader
    private final Object reconnectLock = new Object();

    public SocketClient(String host, int port, int connectTimeoutMs, boolean autoReconnect) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.autoReconnect = autoReconnect;
    }

    public synchronized void connect() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) return;
        socket = new Socket();
        try {
            socket.connect(new java.net.InetSocketAddress(host, port), connectTimeoutMs);
        } catch (IOException e) {
            // if autoReconnect is enabled, start background reconnect attempts instead of throwing
            if (autoReconnect) {
                logger.warn("Initial connect failed, starting reconnect loop: {}", e.getMessage());
                startReconnectLoop();
                return;
            }
            throw e;
        }
        try {
            socket.setTcpNoDelay(true); // disable Nagle to avoid small-write delays
        } catch (SocketException e) {
            logger.warn("Unable to set TCP_NODELAY", e);
        }
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        running = true;

        // If a consumer was already provided via startListening, start the reader
        if (lineConsumer != null) {
            startReader();
        }
    }

    public void startListening(Consumer<String> lineConsumer) {
        // store the consumer so reconnects can restart the reader when connected
        this.lineConsumer = lineConsumer;
        // if reader already running, nothing to do
        if (readerThread != null && readerThread.isAlive()) return;

        // if socket is connected, start reader immediately; otherwise, if autoReconnect is enabled,
        // ensure reconnect loop is running which will start reader after successful connect
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            startReader();
            return;
        }

        if (autoReconnect) {
            startReconnectLoop();
        }
    }

    // helper to start the reader thread (assumes lineConsumer is set and socket is connected)
    private void startReader() {
        if (lineConsumer == null) return; // nothing to do
        if (readerThread != null && readerThread.isAlive()) return;

        readerThread = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (running && (line = br.readLine()) != null) {
                    try {
                        lineConsumer.accept(line);
                    } catch (Exception ex) {
                        logger.warn("Exception in line consumer", ex);
                    }
                }
            } catch (IOException e) {
                if (running) logger.error("Reader error", e);
                // if autoReconnect is enabled, start reconnect attempts
                if (autoReconnect && !stopRequested) {
                    startReconnectLoop();
                }
            } finally {
                running = false;
            }
        }, "SocketClient-Reader");
        readerThread.setDaemon(true);
        running = true;
        readerThread.start();
    }

    // start a background thread that will try to connect until successful or stopped
    private void startReconnectLoop() {
        synchronized (reconnectLock) {
            if (reconnectThread != null && reconnectThread.isAlive()) return;
            stopRequested = false;
            reconnectThread = new Thread(() -> {
                long delay = 1000L; // start with 1s
                final long maxDelay = 30000L; // cap backoff at 30s
                while (!stopRequested) {
                    try {
                        logger.info("Attempting to reconnect to {}:{}", host, port);
                        // Try to connect; connect() will set up writer and reader if lineConsumer non-null
                        try {
                            connect();
                        } catch (IOException e) {
                            // connect() will throw only if autoReconnect is false or unrecoverable
                            logger.debug("connect() in reconnect loop threw: {}", e.getMessage());
                        }

                        // If connected, break the loop (reader will have been started by connect())
                        synchronized (this) {
                            if (socket != null && socket.isConnected() && !socket.isClosed()) {
                                logger.info("Reconnected to {}:{}", host, port);
                                break;
                            }
                        }

                    } catch (Throwable t) {
                        logger.warn("Unexpected error in reconnect loop", t);
                    }

                    // wait using reconnectLock to avoid busy-waiting
                    synchronized (reconnectLock) {
                        try {
                            reconnectLock.wait(delay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    // exponential backoff
                    delay = Math.min(maxDelay, delay * 2);
                }
            }, "SocketClient-Reconnect");
            reconnectThread.setDaemon(true);
            reconnectThread.start();
        }
    }

    public void sendLine(String line) throws IOException {
        if (line == null) return;
        synchronized (this) {
            if (writer == null) throw new IOException("Not connected");
            writer.write(line);
            writer.write("\n"); // ensure newline for readLine on server side
            writer.flush();     // flush immediately to avoid buffering delays
        }
    }

    public synchronized void close() {
        stopRequested = true;
        running = false;
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        if (readerThread != null) readerThread.interrupt();
        if (reconnectThread != null) reconnectThread.interrupt();
        // notify any waiting reconnect thread so it can exit promptly
        synchronized (reconnectLock) {
            reconnectLock.notifyAll();
        }
    }
}
