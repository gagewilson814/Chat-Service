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

public class ChatServer {
    private int port;
    private int debugLevel;
    private static final int TIMEOUT = 180000;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private List<ClientHandler> clients;
    private Set<String> channelLists;
    private int totalMessages;
    private boolean serverActive;

    /*
     * Constructor for chat server
     */
    public ChatServer(int port, int debugLevel) {
        try {
            serverSocket = new ServerSocket(port);
            threadPool = Executors.newFixedThreadPool(4);
            this.clients = Collections.synchronizedList(new ArrayList<>()); // use a synchronized list
            channelLists = new HashSet<>();
            totalMessages = 0;
            serverActive = true;
            System.out.println("Starting up the server on port: " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startServer() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeServer()));

        while (serverActive) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);

                threadPool.execute(clientHandler);

                clients.add(clientHandler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * get total clients in the server
     */
    public Set<ClientHandler> getClients() {
        synchronized (clients) {
            return new HashSet<>(clients);
        }
    }

    /*
     * get total channel in the server
     */
    public synchronized Set<String> getChannelLists() {
        return new HashSet<>(channelLists);
    }

    public synchronized void addChannel(String channelName) {
        channelLists.add(channelName);
    }

    public synchronized void removeChannel(String channelName) {
        channelLists.remove(channelName);
    }

    // total messages in server
    public synchronized int getTotalMessages() {
        return totalMessages;
    }

    // update
    public synchronized void incrementTotalMessages() {
        totalMessages++;
    }

    public synchronized void broadcastMessage(String message, String senderNickname, String channel) {
        for (ClientHandler clientHandler : clients) {
            if (clientHandler.isInChannel(channel)) {
                clientHandler.sendMessageToClient(message);
            }
        }
    }

    public synchronized void removeClient(String clientNickname) {
        clients.removeIf(clientHandler -> clientHandler.getClientNickname().equals(clientNickname));
    }

    public synchronized boolean isServerActive() {
        return serverActive;
    }

    private void closeServer() {
        serverActive = false;
        System.out.println("Closing down the server as requested");

        for (ClientHandler clientHandler : clients) {
            clientHandler.sendMessageToClient("Server is shutting down get out");
        }

        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("ChatServer has been completely shut down");
    }

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