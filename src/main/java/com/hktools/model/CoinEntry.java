package com.hktools.model;

/**
 * Simple POJO for a coin entry in a wallet message.
 * Fields are public so Gson can populate them directly.
 */
public class CoinEntry {
    public String name;
    public String privateKey;
    public String address;

    public CoinEntry() {
    }

    @Override
    public String toString() {
        return "CoinEntry{" +
                "name='" + name + '\'' +
                ", address='" + address + '\'' +
                '}';
    }
}

