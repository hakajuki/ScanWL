package com.hktools.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hktools.config.HttpClientConfig;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class BtcScan {
    private final Gson gson = new GsonBuilder().create();

    public int getBalance(String address) {
        Request request = new Request.Builder()
                .url(String.format("https://btcscan.org/api/address/%s", address))
                .build();

        try (Response response = HttpClientConfig.executeRequest(request)) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JsonObject json = gson.fromJson(body, JsonObject.class);
                if (!json.has("chain_stats")) return 0;
                json = json.get("chain_stats").getAsJsonObject();
                return json.get("tx_count").getAsInt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
