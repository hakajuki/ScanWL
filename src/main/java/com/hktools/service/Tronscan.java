package com.hktools.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hktools.config.ConfigLoader;
import com.hktools.config.HttpClientConfig;
import com.hktools.parser.Base58;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class Tronscan {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final Gson gson = new GsonBuilder().create();

    public int getBalanceV1(String address) {
        String hexAddress = Base58.base58ToHex(address);

        // Prepare request payload
        JsonObject payload = new JsonObject();
        payload.addProperty("address", hexAddress);
        String requestBody = gson.toJson(payload);

        // Load URL from configuration
        String url = ConfigLoader.getInstance().getTronscanUrl();

        // Build HTTP POST request
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(requestBody, JSON))
                .build();

        try (Response response = HttpClientConfig.executeRequest(request)) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                // Extract balance from response
                if (json.has("balance")) {
                    return json.get("balance").getAsInt();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Call Tronscan v2 account API using the signed "secret" header described by the sample.
     * Returns the integer value of the "transactions" field from the response (0 if missing/error).
     *
     * Note: key/uuid are currently hard-coded to match the example. Move these to `ConfigLoader`
     * if they need to be configurable.
     */
    public int getBalance(String address) {
        long t = System.currentTimeMillis();

        // Example key and uuid from the sample. Consider moving to ConfigLoader.
        String key = "19371c6ec037bc72f0e5650019dafd3764e590636dc28873e6c3af3845e04c7d";
        String uuid = "b45dd399-7257-4064-92b1-d54dae6aae57";

        // Build the string to hash: address={address}&{key}={uuid}&t={t}
        String i = String.format("address=%s&%s=%s&t=%d", address, key, uuid, t);

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(i.getBytes(StandardCharsets.UTF_8));

            // Convert digest to hex string (lower-case)
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            String l = hex.toString();

            // Base64 of the UTF-8 bytes of the hex string (matches the sample steps)
            String secret = Base64.getEncoder().encodeToString(l.getBytes(StandardCharsets.UTF_8));

            String url = "https://apilist.tronscan.org/api/accountv2?address=" + address;
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("accept", "application/json, text/plain, */*")
                    .addHeader("origin", "https://tronscan.org")
                    .addHeader("secret", secret)
                    .addHeader("t", String.valueOf(t))
                    .addHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                    .build();

            try (Response response = HttpClientConfig.executeRequest(request)) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                    if (json.has("transactions")) {
                        return json.get("transactions").getAsInt();
                    }
                }
            }

        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }

        return 0;
    }
}
