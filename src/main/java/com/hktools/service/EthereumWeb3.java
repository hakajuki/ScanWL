package com.hktools.service;

import com.hktools.config.HttpClientConfig;
import okhttp3.OkHttpClient;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.http.HttpService;

import java.math.BigDecimal;

public class EthereumWeb3 {
    private static final String ETH_TW = "https://ethereum.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj";
    private static final String BSC_TW = "https://bsc.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj";

    //    private static final String ETH_TW = "https://mainnet.infura.io/v3/b6bf7d3508c941499b10025c0776eaf8";
    //    private static final String BSC_TW = "https://bsc-mainnet.infura.io/v3/b6bf7d3508c941499b10025c0776eaf8";
    private final Web3j web3j;

    public enum Network {
        ETHEREUM,
        BSC
    }

    public EthereumWeb3(Network network) {
        // Use the shared OkHttpClient instance
        OkHttpClient okHttpClient = HttpClientConfig.getClient();

        if (network == Network.ETHEREUM) {
            this.web3j = Web3j.build(new HttpService(ETH_TW, okHttpClient));
        } else {
            this.web3j = Web3j.build(new HttpService(BSC_TW, okHttpClient));
        }
    }

    public BigDecimal getBalance(String address) {
        try {
            // 1. Lấy ETH balance
            EthGetBalance ethBalance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            BigDecimal ethHuman = new BigDecimal(ethBalance.getBalance()).divide(new BigDecimal("1000000000000000000"));
            return ethHuman;
//                if(ethHuman.compareTo(new BigDecimal(0)) > 0) {
//                    balances.add(new TokenBalanceChecker.Balance("ETH", ethHuman.toPlainString(), "Native ETH"));
//                }
        } catch (Exception e) {
            throw new RuntimeException("Lỗi lấy balance: " + e.getMessage(), e);
        }
    }
}
