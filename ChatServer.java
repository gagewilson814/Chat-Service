
/**
 * ChatServer.java
 * Represents the server in a chat application.
 */

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class ChatServer {
    private int port;
    private int debugLevel;
    private static final int TIMEOUT = 180000;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private List<ClientHandler> clients;
    private Set<String> channelLists;
    private int totalMessages;
    private volatile boolean serverActive;

    // Fields for idle shutdown
    private final ScheduledExecutorService scheduler;
    private final AtomicLong lastActivityTime;

    /*
     * Constructor for chat server
     */
    public ChatServer(int port, int debugLevel) {
        try {
            serverSocket = new ServerSocket(port);
            threadPool = Executors.newFixedThreadPool(6);
            this.clients = Collections.synchronizedList(new ArrayList<>());
            channelLists = new HashSet<>();
            totalMessages = 0;
            serverActive = true;
            System.out.println("Starting up the server on port: " + port);

            // Initialize idle shutdown fields
            scheduler = Executors.newSingleThreadScheduledExecutor();
            lastActivityTime = new AtomicLong(System.currentTimeMillis());
            scheduler.scheduleAtFixedRate(this::checkIdle, 1, 1, TimeUnit.MINUTES);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to start the server on port: " + port);
        }
    }

    /**
     * Start the server and accept incoming connections
     */
    public void startServer() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::closeServer));

        while (serverActive) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);

                threadPool.execute(clientHandler);

                clients.add(clientHandler);
                if (clientHandler.getClientNickname() != "" && debugLevel == 1) {
                    System.out.println("Client connected: " + clientHandler.getClientNickname());
                } else if (clientHandler.getClientNickname() == "" && debugLevel == 1) {
                    System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());
                }

                // Update last activity time since a new client has connected
                updateLastActivityTime();

            } catch (IOException e) {
                if (serverActive) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
     * Get total clients in the server
     */
    public synchronized Set<ClientHandler> getClients() {
        synchronized (clients) {
            return new HashSet<>(clients);
        }
    }

    /*
     * Get total channels in the server
     */
    public synchronized Set<String> getChannelLists() {
        return new HashSet<>(channelLists);
    }

    /**
     * Add a channel to the server
     * 
     * @param channelName the name of the channel
     */
    public synchronized void addChannel(String channelName) {
        channelLists.add(channelName);
    }

    /**
     * Remove a channel from the server
     * 
     * @param channelName the name of the channel
     */
    public synchronized void removeChannel(String channelName) {
        channelLists.remove(channelName);
    }

    /**
     * Get the total number of messages sent
     * 
     * @return the total number of messages
     */
    public synchronized int getTotalMessages() {
        return totalMessages;
    }

    /**
     * Increment the total number of messages sent
     */
    public synchronized void incrementTotalMessages() {
        totalMessages++;
    }

    /**
     * Broadcast a message to all clients in a channel
     * 
     * @param message        the message to broadcast
     * @param senderNickname the nickname of the sender
     * @param channel        the channel to broadcast to
     */
    public synchronized void broadcastMessage(String message, String senderNickname, String channel) {
        for (ClientHandler clientHandler : clients) {
            if (clientHandler.isInChannel(channel)) {
                clientHandler.sendMessageToClient(message);
            }
        }
        // Increment total messages since broadcasting is considered activity
        incrementTotalMessages();

        // Update last activity time since a message has been sent
        updateLastActivityTime();
    }

    /**
     * Remove a client from the server
     * 
     * @param clientNickname the nickname of the client to remove
     */
    public synchronized void removeClient(String clientNickname) {
        clients.removeIf(clientHandler -> {
            boolean toRemove = clientHandler.getClientNickname().equals(clientNickname);
            if (toRemove && debugLevel == 1) {
                System.out.println("Client disconnected: " + clientNickname);
            } else if (!toRemove && debugLevel == 1) {
                System.out.println("A client with no nickname disconnected: "
                        + clientHandler.getClientSocket().getInetAddress().getHostAddress());
            }
            return toRemove;
        });

        // Update last activity time since a client has disconnected
        updateLastActivityTime();
    }

    /**
     * Removes the specified channel from the server if it has no active members.
     * 
     * @param channelName the name of the channel to potentially remove
     */
    public synchronized void removeChannelIfEmpty(String channelName) {
        // Normalize the channel name to ensure consistency
        String normalizedChannel = channelName.toLowerCase();

        // Prevent removal of the default 'general' channel
        if (normalizedChannel.equals("general")) {
            if (debugLevel == 1) {
                System.out.println("Attempted to remove the default 'general' channel. Operation skipped.");
            }
            return;
        }

        // Check if any client is still in the channel
        boolean isEmpty = true;
        for (ClientHandler client : clients) {
            if (client.isInChannel(normalizedChannel)) {
                isEmpty = false;
                break;
            }
        }

        // If the channel is empty, remove it from the channel list
        if (isEmpty) {
            boolean removed = channelLists.remove(normalizedChannel);
            if (removed) {
                System.out.println("Channel '" + normalizedChannel + "' has been removed as it is now empty.");
                // Optionally, notify all clients about the channel removal
                broadcastMessage("Channel '" + normalizedChannel + "' has been removed as it is now empty.", "Server",
                        normalizedChannel);
            } else {
                if (debugLevel == 1) {
                    System.out.println("Attempted to remove channel '" + normalizedChannel
                            + "', but it was not found in the channel list.");
                }
            }
        } else {
            if (debugLevel == 1) {
                System.out.println("Channel '" + normalizedChannel + "' is not empty. Removal skipped.");
            }
        }
    }

    /**
     * Check if server is active
     * 
     * @return true if server is active, false otherwise
     */
    public synchronized boolean isServerActive() {
        return serverActive;
    }

    /**
     * Check if a nickname is already taken
     * 
     * @param nickname the nickname to check
     * @return true if nickname is taken, false otherwise
     */
    public synchronized boolean isNicknameTaken(String nickname) {
        for (ClientHandler clientHandler : clients) {
            if (clientHandler.getClientNickname().equals(nickname)) {
                System.out.println("Nickname " + nickname + " is already taken");
                return true;
            }
        }
        return false;
    }

    /*
     * Update the last activity timestamp to the current time
     */
    private synchronized void updateLastActivityTime() {
        lastActivityTime.set(System.currentTimeMillis());
        if (debugLevel == 1) {
            System.out.println("Last activity time updated to: " + lastActivityTime.get());
        }
    }

    /*
     * Check if the server has been idle and shut it down if necessary
     */
    private synchronized void checkIdle() {
        long currentTime = System.currentTimeMillis();
        long lastActivity = lastActivityTime.get();
        long idleDuration = currentTime - lastActivity;

        if (idleDuration > TIMEOUT) {
            System.out.println("No activity for over three minutes. Shutting down.");
            closeServer();
        } else {
            if (debugLevel == 1) {
                System.out.println("Idle check: " + idleDuration + "ms since last activity.");
            }
        }
    }

    /**
     * Get the debug level of the server
     * 
     * @return the debug level
     */
    public synchronized int getDebugLevel() {
        return debugLevel;
    }

    /*
     * Close the server and release all resources
     */
    private synchronized void closeServer() {
        if (!serverActive) {
            return;
        }
        serverActive = false;
        System.out.println("Closing down the server as requested");

        // Notify all clients about the shutdown
        synchronized (clients) {
            for (ClientHandler clientHandler : clients) {
                clientHandler.sendMessageToClient("Server is shutting down. Goodbye! Type /quit to exit.");
                clientHandler.closeClient();
            }
        }

        threadPool.shutdown();
        scheduler.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            threadPool.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close the server socket
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("ChatServer has been completely shut down");
    }

    /**
     * Main method to start the chat server
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args.length != 4 || !args[0].equals("-p") || !args[2].equals("-d")) {
            System.out.println("Usage: java ChatServer -p <port#> -d <debug level>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[1]);
        int debugLevel = Integer.parseInt(args[3]);

        ChatServer chatServer = new ChatServer(port, debugLevel);
        chatServer.startServer();
    }
}
