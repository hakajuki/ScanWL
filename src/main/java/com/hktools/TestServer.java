package com.hktools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Simple test server để test SocketClient.
 * Chạy server này trên port 63000 để test SocketClient.
 */
public class TestServer {
    private static final Logger logger = LoggerFactory.getLogger(TestServer.class);
    private static final int PORT = 63000;

    public static void main(String[] args) {
        logger.info("Khởi động test server trên port {}...", PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Server đang lắng nghe trên port {}", PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Client đã kết nối: {}", clientSocket.getRemoteSocketAddress());

                // Xử lý mỗi client trong thread riêng
                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (IOException e) {
            logger.error("Lỗi server: {}", e.getMessage(), e);
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
            );
            PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(clientSocket.getOutputStream()),
                true
            )
        ) {
            // Gửi tin nhắn chào mừng
            writer.println("Welcome to test server!");

            // Đọc và echo lại tin nhắn từ client
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("Nhận từ client: {}", line);

                // Echo lại tin nhắn
                String response = "Echo: " + line;
                writer.println(response);
                logger.info("Đã gửi: {}", response);
            }

            logger.info("Client đã ngắt kết nối: {}", clientSocket.getRemoteSocketAddress());

        } catch (IOException e) {
            logger.error("Lỗi xử lý client: {}", e.getMessage());
        }
    }
}

