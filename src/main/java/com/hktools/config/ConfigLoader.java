package com.hktools.config;

import java.io.*;
import java.util.*;

public class ConfigLoader {
    private static ConfigLoader instance;
    private Properties properties;
    private static final String CONFIG_FILE = "config.txt";

    private ConfigLoader() {
        properties = new Properties();
        loadConfig();
    }

    public static ConfigLoader getInstance() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
        return instance;
    }

    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);

        // If config file doesn't exist, create it with default values
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        // Load configuration
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
            System.out.println("Configuration loaded successfully from " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            setDefaultValues();
        }
    }

    private void createDefaultConfig() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            Properties defaultProps = new Properties();

            // BTC Scan URLs
            defaultProps.setProperty("btcscan.url", "https://btcscan.org/api/address/{address}");

            // Tronscan URLs
            defaultProps.setProperty("tronscan.url", "https://tron.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj/wallet/getaccount");

            // Ethereum URLs (comma-separated list)
            defaultProps.setProperty("ethereum.urls",
                "https://ethereum.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj," +
                "https://mainnet.infura.io/v3/b6bf7d3508c941499b10025c0776eaf8,"
            );

            // BSC URLs (comma-separated list)
            defaultProps.setProperty("bsc.urls",
                "https://bsc.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj," +
                "https://bsc-mainnet.infura.io/v3/b6bf7d3508c941499b10025c0776eaf8,"
            );

            // Telegram Configuration
            defaultProps.setProperty("telegram.bot_token", "6116978471:AAFrxX4j3ZsJ-mzN1dS47bvwh7F53nmelDY");
            defaultProps.setProperty("telegram.group_id", "-1003835357560");

            defaultProps.store(output, "PlanCoinScan Configuration File\n" +
                    "# Edit these values as needed\n" +
                    "# For multiple URLs, use comma-separated values");

            System.out.println("Default configuration file created: " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("Error creating default configuration: " + e.getMessage());
        }
    }

    private void setDefaultValues() {
        properties.setProperty("btcscan.url", "https://btcscan.org/api/address/{address}");
        properties.setProperty("tronscan.url", "https://tron.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj/wallet/getaccount");
        properties.setProperty("ethereum.urls", "https://ethereum.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj");
        properties.setProperty("bsc.urls", "https://bsc.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj");
        properties.setProperty("telegram.bot_token", "6116978471:AAFrxX4j3ZsJ-mzN1dS47bvwh7F53nmelDY");
        properties.setProperty("telegram.group_id", "-1003835357560");
    }

    public String getBtcScanUrl() {
        return properties.getProperty("btcscan.url", "https://btcscan.org/api/address/{address}");
    }

    public String getTronscanUrl() {
        return properties.getProperty("tronscan.url", "https://tron.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj/wallet/getaccount");
    }

    public List<String> getEthereumUrls() {
        String urls = properties.getProperty("ethereum.urls", "https://ethereum.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj");
        return Arrays.asList(urls.split(","));
    }

    public List<String> getBscUrls() {
        String urls = properties.getProperty("bsc.urls", "https://bsc.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj");
        return Arrays.asList(urls.split(","));
    }

    public String getTelegramBotToken() {
        return properties.getProperty("telegram.bot_token", "6116978471:AAFrxX4j3ZsJ-mzN1dS47bvwh7F53nmelDY");
    }

    public String getTelegramGroupId() {
        return properties.getProperty("telegram.group_id", "-1003835357560");
    }

    // Method to reload configuration without restarting the application
    public void reloadConfig() {
        loadConfig();
        System.out.println("Configuration reloaded");
    }
}

