package com.hktools.parser;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hktools.model.CoinEntry;
import com.hktools.model.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Parser that attempts to parse a single-line JSON message into a Wallet object.
 * It only tries parsing when the line appears to be JSON (trim starts with '{').
 * Strict validation: mnemonic must be present and coins must include bitcoin, ethereum and tron
 * each with non-empty privateKey and address. Extra coins are allowed and ignored.
 */
public class JsonMessageParser {
    private static final Logger logger = LoggerFactory.getLogger(JsonMessageParser.class);
    private final Gson gson = new Gson();

    public Optional<Wallet> parseLine(String line) {
        if (line == null) return Optional.empty();
        String trimmed = line.trim();
        if (!trimmed.startsWith("{")) {
            return Optional.empty();
        }

        try {
            JsonElement el = JsonParser.parseString(trimmed);
            if (!el.isJsonObject()) return Optional.empty();
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("error")) {
                String err = obj.get("error").getAsString();
                logger.warn("Server returned error JSON: {}", err);
                return Optional.empty();
            }

            // Try to parse as Wallet
            Wallet wallet = gson.fromJson(obj, Wallet.class);
            if (wallet == null) return Optional.empty();

            if (!isValidStrict(wallet)) {
                logger.warn("Discarding wallet due to strict validation failure: {}", wallet);
                return Optional.empty();
            }
            String firstWords = firstWords(wallet.mnemonic, 3);
            logger.info("Accepted wallet with mnemonic (first words): {}...", firstWords);
            logger.info("Storing private keys in-memory as plaintext (ensure this is intended): {}", firstWords);
            return Optional.of(wallet);

        } catch (Exception e) {
            logger.warn("Failed to parse JSON line: {} (cause: {})", line, e.toString());
            return Optional.empty();
        }
    }

    private boolean isValidStrict(Wallet wallet) {
        if (wallet.mnemonic == null || wallet.mnemonic.trim().isEmpty()) return false;
        if (wallet.coins == null) return false;

        Map<String, CoinEntry> map = wallet.coins.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(c -> c.name == null ? "" : c.name.toLowerCase(), c -> c, (a, b) -> a));

        String[] required = new String[]{"bitcoin", "ethereum", "tron"};
        for (String r : required) {
            CoinEntry ce = map.get(r);
            if (ce == null) return false;
            if (ce.privateKey == null || ce.privateKey.trim().isEmpty()) return false;
            if (ce.address == null || ce.address.trim().isEmpty()) return false;
        }
        return true;
    }

    private String firstWords(String s, int n) {
        if (s == null) return "";
        String[] parts = s.trim().split("\\s+");
        return String.join(" ", Arrays.copyOfRange(parts, 0, Math.min(n, parts.length)));
    }

    public void reset() {
        // nothing to do for stateless parser, but kept for API symmetry
    }
}

