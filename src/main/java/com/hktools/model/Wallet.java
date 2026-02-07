package com.hktools.model;

import java.util.List;

/**
 * Simple POJO representing a wallet message.
 */
public class Wallet {
    public String mnemonic;
    public List<CoinEntry> coins;

    public Wallet() {
    }

    @Override
    public String toString() {
        return "Wallet{" +
                "mnemonic='" + mnemonic + '\'' +
                ", coins=" + coins +
                '}';
    }
}

