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

public class Tronscan {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final Gson gson = new GsonBuilder().create();

    public int getBalance(String address) {
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
}
