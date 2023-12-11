package com.caveman.chat.server;

import com.caveman.network.TCPConnection;
import com.caveman.network.TCPConnectionListener;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.util.*;

public class ChatServer implements TCPConnectionListener {

    public static void main(String[] args) {
        new ChatServer();
    }

    private final HashMap<String, TCPConnection> userIpMap = new HashMap<>();
    private final ArrayList<TCPConnection> connections = new ArrayList<>();

    private ChatServer() {
        System.out.println("Server works!");
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            while (true) {
                try {
                    new TCPConnection(this, serverSocket.accept());
                } catch (IOException e) {
                    System.out.println("TCPConnection exception: " + e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public synchronized void onConnectionReady(TCPConnection tcpConnection) {
        connections.add(tcpConnection);
        sendToAllConnection("Client connected: " + tcpConnection);
        tcpConnection.sendString("Please set your login by /setLogin-YourLogin");
    }

    @Override
    public synchronized void onReceiveString(TCPConnection tcpConnection, String value) {
        if (value.startsWith("/setLogin")) {
            String[] parts = value.split("-");
            String userLogin = parts[1];
            if (!userIpMap.containsKey(userLogin)) {
                // Запомним логин и IP адрес пользователя
                userIpMap.put(userLogin, tcpConnection);

                // Отправим уведомление о подключении нового пользователя
                sendToAllConnection(userLogin + " has signed up");
                tcpConnection.sendString("All commands by /cmd");
            } else {
                // Логин уже занят, предположим, что здесь нужна логика повторного запроса логина или что-то подобное
                tcpConnection.sendString("Login is already taken. Please choose another one.");
            }
        } else if (value.equals("/cmd")) {
            sendAllCommandsList(tcpConnection);
        } else if (value.equals("/usersOnline")) {
            tcpConnection.sendString(getOnlineUsersList());
        } else if (value.startsWith("/private")) {
            // Обработка приватных сообщений
            String[] parts = value.split("-");
            String recipientLogin = parts[1];
            String content = parts[2];
            String message = senderName(userIpMap, tcpConnection) + " - " + content;

            // Определение IP адреса получателя
            TCPConnection recipientsConnection = userIpMap.get(recipientLogin);

            // Отправка приватного сообщения
            if (recipientsConnection != null) {
                sendPrivateMessage(recipientsConnection, message);
            } else {
                tcpConnection.sendString("User " + recipientLogin + " not found.");
            }
        } else {
            // Отправка обычных сообщений
            sendToAllConnection(senderName(userIpMap, tcpConnection) + ": " + value);
        }
    }

    @Override
    public synchronized void onDisconnect(TCPConnection tcpConnection) {
        sendToAllConnection(senderName(userIpMap, tcpConnection) + " disconnected");
        connections.remove(tcpConnection);
        removeByValue(userIpMap, tcpConnection);
    }

    @Override
    public synchronized void onException(TCPConnection tcpConnection, Exception e) {
        System.out.println("TCPConnection exception " + e);
    }

    private void sendToAllConnection(String value) {
        final int cnt = connections.size();
        for (int i = 0; i < cnt; i++) {
            connections.get(i).sendString(value);
        }
    }

    private String getOnlineUsersList() {
        StringBuilder onlineUsers = new StringBuilder("Online Users: ");
        Iterator<String> iterator = userIpMap.keySet().iterator();
        while (iterator.hasNext()) {
            onlineUsers.append(iterator.next());
            if (iterator.hasNext()) {
                onlineUsers.append(", ");
            }
        }
        return onlineUsers.toString();
    }

    private void sendAllCommandsList(TCPConnection tcpConnection) {
        tcpConnection.sendString("/setLogin-YourLogin --- sets your login");
        tcpConnection.sendString("/usersOnline --- too see all online users");
        tcpConnection.sendString("/private-RecipientsLogin-YourMessage --- too send private message");
    }

    private static <K, V> K senderName(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (value.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static <K, V> void removeByValue(HashMap<K, V> map, V value) {
        Iterator<Map.Entry<K, V>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, V> entry = iterator.next();
            if (entry.getValue().equals(value)) {
                iterator.remove();
            }
        }
    }

    private void sendPrivateMessage(TCPConnection recipientsConnection, String message) {
        // Отправка приватного сообщения только получателю
        for (TCPConnection connection : connections) {
            if (connection.equals(recipientsConnection)) {
                connection.sendString("Private message from " + message);
            }
        }
    }
}
