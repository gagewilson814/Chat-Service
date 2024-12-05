
/**
 * ClientHandler.java
 * Handles the client commands for the chat server.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ChatServer chatServer;
    private String clientNickname = "";
    private String currentChannel;
    private int totalMessagesFromOne;

    /**
     * Constructor for the ClientHandler.
     *
     * @param clientSocket the socket of the client
     * @param chatServer   the chat server object
     */
    public ClientHandler(Socket clientSocket, ChatServer chatServer) {
        this.clientSocket = clientSocket;
        this.chatServer = chatServer;
        this.currentChannel = "general";
        chatServer.addChannel("general");

        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (SocketException e) {
            sendMessageToClient("Goodbye!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    /**
     * Run method for the ClientHandler thread.
     */
    public void run() {
        try {
            sendMessageToClient("Welcome to the ChatServer, choose a name: ");
            String nameResponse = reader.readLine();
            while (chatServer.isNicknameTaken(nameResponse)) {
                sendMessageToClient("Nickname is already taken. Choose another");
                nameResponse = reader.readLine();
            }
            handleNickname(nameResponse);
            handleHelp();

            String clientInput;
            while ((clientInput = reader.readLine()) != null) {
                handleClientCommand(clientInput);
            }
        } catch (SocketException e) {
            handleClientDisconnection(e);
        } catch (IOException e) {
            handleClientDisconnection(e);
        } finally {
            chatServer.removeClient(clientNickname);
            sendMessageToAll(clientNickname + " has left the server");
            closeAll();
        }
    }

    /**
     * Handles the disconnection of a client from the server
     * 
     * @param e the exception that caused the disconnection
     */
    private void handleClientDisconnection(Exception e) {
        String clientIdentifier = clientNickname.isEmpty() ? clientSocket.getInetAddress().toString() : clientNickname;
        if (chatServer.getDebugLevel() == 1) {
            System.out.println("Client " + clientIdentifier + " disconnected.");
        }
    }

    /**
     * Simply returns the client's nickname
     *
     * @return client's nickname
     */
    public synchronized String getClientNickname() {
        return clientNickname;
    }

    /**
     * Sends a message just to the client (no other clients see this message)
     *
     * @param message the message to send to client
     */
    public synchronized void sendMessageToClient(String message) {
        try {
            writer.println(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized Socket getClientSocket() {
        return clientSocket;
    }

    public synchronized String getChannel() {
        return getCurrentChannel();
    }

    public synchronized void closeClient() {
        try {
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if the client is in a channel.
     *
     * @param channel the channel name
     * @return true if client is in the channel, false otherwise
     */
    public synchronized boolean isInChannel(String channel) {
        return currentChannel.equalsIgnoreCase(channel);
    }

    /**
     * Sends a message to all clients in the current channel.
     *
     * @param message message to send to all clients
     */
    public synchronized void sendMessageToAll(String message) {
        chatServer.broadcastMessage(message, clientNickname, getCurrentChannel());
        totalMessagesFromOne++;
        chatServer.incrementTotalMessages();
    }

    /**
     * get message number for /stats command
     *
     */
    public synchronized int getMessageNum() {
        return totalMessagesFromOne;
    }

    /**
     * Handles the setting of a nickname on the chat server
     *
     * @param nickname the nickname to set to
     */
    private synchronized void handleNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            sendMessageToClient("Invalid name. Choose another");
        } else if (chatServer.isNicknameTaken(nickname)) {
            sendMessageToClient("Nickname is already taken. Choose another");
        } else {
            sendMessageToClient("Nickname set to: " + nickname);
            clientNickname = nickname;
        }
    }

    /**
     * Handles the various client commands able to be used on the chat server
     *
     * @param command the command being supplied
     */
    private synchronized void handleClientCommand(String command) {
        String[] tokens = command.trim().split("\\s+", 2);
        String cmd = tokens[0].toLowerCase();
        String argument = tokens.length > 1 ? tokens[1].trim() : "";

        switch (cmd) {
            case "/nick":
                if (!argument.isEmpty()) {
                    handleNickname(argument);
                } else {
                    sendMessageToClient("Usage: /nick <nickname>");
                }
                break;

            case "/join":
                if (!argument.isEmpty()) {
                    handleJoin(argument);
                } else {
                    sendMessageToClient("Usage: /join <channel>");
                }
                break;

            case "/leave":
                handleLeave();
                break;

            case "/quit":
                sendMessageToClient("Goodbye!");
                closeAll();
                break;

            case "/help":
                handleHelp();
                break;

            case "/list":
                handleList();
                break;

            default:
                sendMessageToAll("[" + getCurrentChannel() + "] " + clientNickname + ": " + command);
                break;
        }
    }

    /**
     * Prints a list of the connected clients and the channels on the server.
     */
    private synchronized void handleList() {
        // list of connected clients
        sendMessageToClient("\nList of connected clients: ");
        for (ClientHandler client : chatServer.getClients()) {
            sendMessageToClient(client.getClientNickname());
        }
        // list of channels
        sendMessageToClient("\nList of channels in the server: ");
        for (String channel : chatServer.getChannelLists()) {
            sendMessageToClient(channel);
        }
    }

    /**
     * Handles the joining of a client to a channel
     *
     * @param channel the channel to join
     */
    private synchronized void handleJoin(String channel) {
        if (channel.isEmpty()) {
            sendMessageToClient("Invalid channel, choose a different one");
        } else {
            channel = channel.toLowerCase();

            // Leave the current channel
            sendMessageToAll("User " + clientNickname + " has left the channel: " + currentChannel);
            chatServer.removeChannelIfEmpty(currentChannel);

            // Update current channel
            currentChannel = channel;
            chatServer.addChannel(channel);
            sendMessageToClient("You have joined channel: " + channel);
            chatServer.broadcastMessage("User " + clientNickname + " has joined the channel: " + channel,
                    clientNickname, channel);
        }
    }

    /**
     * Handles a client leaving their current channel
     */
    private synchronized void handleLeave() {
        if (currentChannel.equals("general")) {
            sendMessageToClient("You cannot leave the default 'general' channel.");
            return;
        }
        sendMessageToAll("User " + clientNickname + " has left the channel: " + currentChannel);
        chatServer.removeChannelIfEmpty(currentChannel);

        // Set current channel to 'general'
        currentChannel = "general";
        chatServer.addChannel("general");
        sendMessageToClient("You have joined channel: general");
        chatServer.broadcastMessage("User " + clientNickname + " has joined the channel: general",
                clientNickname, "general");
    }

    /*
     * Help message for the client
     */
    private synchronized void handleHelp() {
        sendMessageToClient("List of available commands: \n");
        sendMessageToClient("/nick <nickname> - sets your nickname");
        sendMessageToClient("/list - lists all available channels");
        sendMessageToClient("/join <channel> - joins a channel");
        sendMessageToClient("/leave - leaves the current channel");
        sendMessageToClient("/quit - quits the server");
        sendMessageToClient("/help - displays this message");
    }

    /**
     * Gets the current channel and returns it
     *
     * @return the current channel
     */
    private synchronized String getCurrentChannel() {
        return currentChannel;
    }

    /**
     * Cleanup method to close sockets, readers, and writers.
     */
    private void closeAll() {
        try {
            reader.close();
            writer.close();
            clientSocket.close();
        } catch (SocketException e) {
            sendMessageToClient("Goodbye!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
