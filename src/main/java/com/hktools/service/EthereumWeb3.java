package com.hktools.service;

import com.hktools.config.ConfigLoader;
import com.hktools.config.HttpClientConfig;
import okhttp3.OkHttpClient;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.http.HttpService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class EthereumWeb3 {
    private final List<String> urls;
    private final AtomicInteger currentUrlIndex;
    private final Network network;
    private final OkHttpClient okHttpClient;
    private Web3j web3j;

    public enum Network {
        ETHEREUM,
        BSC
    }

    public EthereumWeb3(Network network) {
        this.network = network;
        this.okHttpClient = HttpClientConfig.getClient();

        // Load URLs from configuration
        ConfigLoader config = ConfigLoader.getInstance();
        this.urls = (network == Network.ETHEREUM) ? config.getEthereumUrls() : config.getBscUrls();

        this.currentUrlIndex = new AtomicInteger(0);
        this.web3j = createWeb3jInstance();
    }

    private Web3j createWeb3jInstance() {
        String url = urls.get(currentUrlIndex.get());
        return Web3j.build(new HttpService(url, okHttpClient));
    }

    private void rotateToNextUrl() {
        int nextIndex = (currentUrlIndex.incrementAndGet()) % urls.size();
        currentUrlIndex.set(nextIndex);
        String newUrl = urls.get(nextIndex);
        System.out.println("[" + network + "] Rotating to next URL (index " + nextIndex + "): " + newUrl);

        // Close old web3j instance if needed
        if (web3j != null) {
            try {
                web3j.shutdown();
            } catch (Exception e) {
                // Ignore shutdown errors
            }
        }

        // Create new instance with next URL
        web3j = createWeb3jInstance();
    }

    public BigDecimal getBalance(String address) {
        int maxRetries = urls.size(); // Try all available URLs
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // 1. Lấy ETH/BSC balance
                EthGetBalance ethBalance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();

                // Check if there's an error in the response
                if (ethBalance.hasError()) {
                    throw new RuntimeException("RPC Error: " + ethBalance.getError().getMessage());
                }

                return new BigDecimal(ethBalance.getBalance()).divide(new BigDecimal("1000000000000000000"), 18, RoundingMode.DOWN);
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage().toLowerCase();

                // Check for 403 error or other HTTP errors
                if (errorMsg.contains("401") || errorMsg.contains("403") || errorMsg.contains("forbidden") ||
                    errorMsg.contains("429") || errorMsg.contains("rate limit")) {

                    System.out.println("[" + network + "] HTTP Error detected (attempt " + (attempt + 1) + "/" + maxRetries + "): " + e.getMessage());

                    // If not the last attempt, rotate to next URL
                    if (attempt < maxRetries - 1) {
                        rotateToNextUrl();
                        // Small delay before retry
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } else {
                    // For other errors, throw immediately
                    throw new RuntimeException("Lỗi lấy balance: " + e.getMessage(), e);
                }
            }
        }

        // If all retries failed
        throw new RuntimeException("Lỗi lấy balance sau " + maxRetries + " lần thử với tất cả URLs: " +
                                   (lastException != null ? lastException.getMessage() : "Unknown error"), lastException);
    }
}
