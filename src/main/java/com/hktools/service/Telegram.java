package com.hktools.service;

import com.hktools.Main;
import com.hktools.config.HttpClientConfig;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

public class Telegram {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static Telegram _instance;

    public static Telegram getInstance() {
        if(_instance == null) {
            _instance = new Telegram();
        }
        return _instance;
    }

    public String BOT_TOKEN = "6116978471:AAFrxX4j3ZsJ-mzN1dS47bvwh7F53nmelDY";
    public String GROUP_ID = "-1003835357560";

    public void sendMessage(String message) {
        String telegramBotToken = BOT_TOKEN;
        String groupId = GROUP_ID;
        String url = String.format("https://api.telegram.org/bot%s/sendMessage", telegramBotToken);
        String body = String.format("{\"chat_id\":\"%s\",\"text\":\"%s\"}", groupId, message.replace("\"", "\\\""));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response response = HttpClientConfig.executeRequest(request)) {
            if (response.code() != 200) {
                Main.logger.error("Failed to send Telegram message: HTTP {} - {}", response.code(), response.body() != null ? response.body().string() : "");
            } else {
                Main.logger.info("Sent Telegram message: {}", message);
            }
        } catch (IOException e) {
            Main.logger.error("Error sending Telegram message: {}", e.getMessage(), e);
        }
    }
}
