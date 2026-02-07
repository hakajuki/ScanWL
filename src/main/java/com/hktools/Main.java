package com.hktools;

import com.hktools.model.Wallet;
import com.hktools.parser.JsonMessageParser;
import com.hktools.service.Telegram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Demo SocketClient kết nối tới 127.0.0.1:63000
 */
public class Main {
    public static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Number of clients to start (from args[0] or default 1)
        int n = 10;
        if (args != null && args.length > 0) {
            try {
                n = Integer.parseInt(args[0]);
                if (n < 1) n = 1;
            } catch (NumberFormatException ignored) {}
        }



        logger.warn("Starting {} WalletClient(s)", n);
        Telegram.getInstance().sendMessage("🚀 Khởi động chương trình với " + n + " WalletClient(s)");

        List<WalletClient> clients = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            clients.add(new WalletClient(i + 1, "127.0.0.1", 63000, 2000, true));
        }

        try {
            // Start all clients
            for (WalletClient wc : clients) {
                try {
                    wc.start();
                    logger.info("Started WalletClient-{}", wc.getId());
                } catch (IOException e) {
                    logger.error("Failed to start WalletClient-{}: {}", wc.getId(), e.getMessage(), e);
                }
            }

            // Read input from console and broadcast to all clients
            Scanner scanner = new Scanner(System.in);
            logger.info("Nhập tin nhắn (gõ 'quit' để thoát):");

            while (true) {
                String input = scanner.nextLine();

                if ("quit".equalsIgnoreCase(input.trim())) {
                    logger.info("Thoát chương trình...");
                    break;
                }

                if (!input.trim().isEmpty()) {
                    for (WalletClient wc : clients) {
                        try {
                            wc.sendLine(input);
                        } catch (IOException e) {
                            logger.error("Error sending from client-{}: {}", wc.getId(), e.getMessage());
                        }
                    }
                }
            }

            scanner.close();

        } finally {
            // Stop all clients
            for (WalletClient wc : clients) {
                try {
                    wc.stop();
                } catch (Exception ignored) {}
            }
            logger.info("Chương trình đã kết thúc");
        }
    }
}