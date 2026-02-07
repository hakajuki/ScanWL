package com.hktools.config;

/**
 * Simple test class to verify configuration loading
 * Run this to check if config.txt is being loaded correctly
 */
public class ConfigLoaderTest {
    public static void main(String[] args) {
        System.out.println("=== Testing Configuration Loader ===\n");

        ConfigLoader config = ConfigLoader.getInstance();

        System.out.println("1. BTC Scan URL:");
        System.out.println("   " + config.getBtcScanUrl());
        System.out.println();

        System.out.println("2. Tronscan URL:");
        System.out.println("   " + config.getTronscanUrl());
        System.out.println();

        System.out.println("3. Ethereum URLs (" + config.getEthereumUrls().size() + " endpoints):");
        for (int i = 0; i < config.getEthereumUrls().size(); i++) {
            System.out.println("   [" + i + "] " + config.getEthereumUrls().get(i));
        }
        System.out.println();

        System.out.println("4. BSC URLs (" + config.getBscUrls().size() + " endpoints):");
        for (int i = 0; i < config.getBscUrls().size(); i++) {
            System.out.println("   [" + i + "] " + config.getBscUrls().get(i));
        }
        System.out.println();

        System.out.println("5. Telegram Configuration:");
        System.out.println("   Bot Token: " + maskToken(config.getTelegramBotToken()));
        System.out.println("   Group ID:  " + config.getTelegramGroupId());
        System.out.println();

        System.out.println("=== Configuration Test Complete ===");
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 10) return "***";
        return token.substring(0, 10) + "..." + token.substring(token.length() - 4);
    }
}

