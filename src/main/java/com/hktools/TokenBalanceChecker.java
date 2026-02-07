package com.hktools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hktools.config.HttpClientConfig;
import okhttp3.Request;
import okhttp3.Response;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TokenBalanceChecker {

//    private static final String LLAMARPC_URL = "https://eth-mainnet.rpcfast.com?api_key=xbhWBI1Wkguk8SNMu1bvvLurPGLXmgwYeC4S6g2H7WdwFigZSmPWVZRxrskEQwIf";
//    private static final String LLAMARPC_URL = "https://eth-mainnet.g.alchemy.com/v2/eRP_IY_87zYv3VM7OK1pQUaWqaFLODOb";
    private static final String ETH_TW = "https://rpc.sentio.xyz/mainnet";
        //"https://ethereum-rpc.publicnode.com";
        //"https://eth-mainnet.public.blastapi.io";
    //https://ethereum.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj";
//    private static final String BSC_TW = "https://bsc.twnodes.com/naas/session/OWFjNzJmMjItYmQ3MC00Y2ZkLWJhODMtODZlMjNlYmQ4Mzdj";
    private static final String URL = ETH_TW;
    private final Web3j web3j;

    // Top 10 ERC20 tokens phổ biến (contract addresses)
    private static final Map<String, String> TOP_TOKENS = Map.of(
            "USDT", "0xdAC17F958D2ee523a2206206994597C13D831ec7",
            "USDC", "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
//            ,
//            "DAI", "0x6B175474E89094C44Da98b954EedeAC495271d0F",
//            "WETH", "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2",
//            "UNI", "0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984",
//            "LINK", "0x514910771AF9Ca656af840dff83E8264EcF986CA",
//            "WBTC", "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599",
//            "AAVE", "0x7Fc66500c84A76Ad7e9c93437bFc5Ac33E2DDaE9",
//            "MKR", "0x9f8F72aA9304c8B593d555F12eF6589cC3A579A2",
//            "COMP", "0xc00e94Cb662C3520282E6f5717214004A7f26888"
    );
    Gson gson = new GsonBuilder().create();

    public TokenBalanceChecker() {
        // Use shared OkHttp client for Web3j
        this.web3j = Web3j.build(new HttpService(URL, HttpClientConfig.getClient()));
    }

    /**
     * Lấy top số dư tokens (sắp xếp từ lớn đến bé)
     * @param address Địa chỉ ví
     * @param maxTokens Số lượng tokens tối đa (default 10)
     * @return List<Balance> sắp xếp theo balance descending
     */
    public List<Balance> getTopTokenBalances(String address, int maxTokens) {
        List<Balance> balances = new ArrayList<>();

        try {
            // 1. Lấy ETH balance
            EthGetBalance ethBalance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            BigDecimal ethHuman = new BigDecimal(ethBalance.getBalance()).divide(new BigDecimal("1000000000000000000"));
            if(ethHuman.compareTo(new BigDecimal(0)) > 0) {
                balances.add(new Balance("ETH", ethHuman.toPlainString(), "Native ETH"));
            }

            // 2. Lấy balances cho top tokens
            for (Map.Entry<String, String> entry : TOP_TOKENS.entrySet()) {
                String symbol = entry.getKey();
                String contract = entry.getValue();

                BigInteger balanceWei = getTokenBalance(address, contract);
                if (balanceWei.compareTo(BigInteger.ZERO) > 0) {
                    // Giả sử decimals = 18 cho hầu hết tokens (USDT/USDC=6, nhưng approx)
//                    BigDecimal humanBalance = new BigDecimal(balanceWei).divide(new BigDecimal("1000000000000000000"));
                    BigDecimal humanBalance = new BigDecimal(balanceWei).divide(new BigDecimal("1000000"));
                    balances.add(new Balance(symbol, humanBalance.toPlainString(), contract));
                }
            }

            // 3. Sắp xếp từ lớn đến bé
            balances.sort((a, b) -> {
                BigDecimal ba = new BigDecimal(a.balance);
                BigDecimal bb = new BigDecimal(b.balance);
                return bb.compareTo(ba);
            });

            // Giới hạn số lượng
            return balances.stream().limit(Math.min(maxTokens, balances.size())).collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Lỗi lấy token balances: " + e.getMessage(), e);
        }
    }

    public int getTxCountBTC(String address) {
        Request request = new Request.Builder()
                .url(String.format("https://btcscan.org/api/address/%s", address))
                .build();

        try (Response response = HttpClientConfig.executeRequest(request)) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
//                System.out.println(body);

                JsonObject json = gson.fromJson(body, JsonObject.class);
                if(!json.has("chain_stats")) return 0;
                json = json.get("chain_stats").getAsJsonObject();
                return json.get("tx_count").getAsInt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Gọi balanceOf(address) cho ERC20 contract
     */
    private BigInteger getTokenBalance(String owner, String contract) throws IOException {
        Function function = new Function(
                "balanceOf",
                Collections.singletonList(new Address(owner)),
                Collections.singletonList(new TypeReference<Uint256>() {})
        );

        String encodedFunction = FunctionEncoder.encode(function);

        EthCall response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(null, contract, encodedFunction),
                DefaultBlockParameterName.LATEST
        ).send();

//        List<Uint256> results = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        List outputParameters = new ArrayList();
        outputParameters.add(new TypeReference<Uint256>() {
        });
        List<Uint256> results = FunctionReturnDecoder.decode(response.getValue(), outputParameters);
        return results.size() > 0 ?  results.get(0).getValue() : (new BigInteger("-1"));
    }

    /**
     * Đóng connection
     */
    public void close() {
        web3j.shutdown();
    }

    // Balance class
    public static class Balance {
        public final String symbol;
        public final String balance;
        public final String contract;

        public Balance(String symbol, String balance, String contract) {
            this.symbol = symbol;
            this.balance = balance;
            this.contract = contract;
        }

        @Override
        public String toString() {
            return String.format("🏆 %s: %s (%s)", symbol, balance, contract.substring(0, 10) + "...");
        }
    }

    public static void main(String[] args) {
        String address = "0x2ab35a9e0eb211eb91baaf4e8626d97165ce8a5c";  // Vitalik
//        String address = "0xf8de5e61322302b2c6e0a525cc842f10332811bf";

        System.out.println("💰 TOP SỐ DỰ TOKENS (Từ Lớn Đến Bé)");
        System.out.println("📍 Address: " + address);
        System.out.println("═" + "═".repeat(60) + "═\n");

        TokenBalanceChecker checker = new TokenBalanceChecker();

        try {
            // Lấy top 10 balances
            List<Balance> topBalances = checker.getTopTokenBalances(address, 10);

            if (topBalances.isEmpty()) {
                System.out.println("❌ Không tìm thấy token nào (hoặc ví rỗng)!");
                return;
            }

            // In bảng
            System.out.println("RANK | SYMBOL | BALANCE | CONTRACT");
            System.out.println("─".repeat(50));

            for (int i = 0; i < topBalances.size(); i++) {
                Balance b = topBalances.get(i);
                System.out.printf("%2d   | %6s | %8s | %s\n",
                        i + 1, b.symbol, b.balance, b.contract);
            }

            // Tổng giá trị ETH (approx, không USD)
            System.out.println("\n📊 THỐNG KÊ:");
            BigDecimal totalEth = topBalances.stream()
                    .filter(b -> b.symbol.equals("ETH"))
                    .map(b -> new BigDecimal(b.balance))
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
            System.out.printf("• ETH Balance: %.4f ETH\n", totalEth);
            System.out.printf("• Top Tokens: %d (từ top 11: ETH + 10 ERC20)\n", topBalances.size());

        } finally {
            checker.close();
        }
    }
}