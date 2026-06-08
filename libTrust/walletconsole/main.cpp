// SPDX-License-Identifier: Apache-2.0
//
// Copyright © 2017 Trust Wallet.

#include "WalletConsole.h"
#include "SocketServer.h"
#include <iostream>

int main(int argc, char* argv[]) {
    // arg[1]:  "1" = socket server, HDWallet CSPRNG wallet generation (default)
    //          "2" = socket server, wordlist-file + checksum-fixed word 12
    //          (omitted) = interactive console
    if (argc >= 2) {
        const std::string mode(argv[1]);
        if (mode == "1" || mode == "2") {
            int walletMode = std::stoi(mode);
            int port = 12345;
            if (argc >= 3) {
                try {
                    port = std::stoi(argv[2]);
                } catch (...) {
                    // fall back to default port
                }
            }
            return StartSocketServer(port, walletMode);
        }
    }

    TW::WalletConsole::WalletConsole console(std::cin, std::cout);
    console.loop();
    return 0;
}