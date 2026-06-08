// SPDX-License-Identifier: Apache-2.0
//
// Copyright © 2017 Trust Wallet.

#include "WalletConsole.h"
#include "SocketServer.h"
#include <iostream>

int main(int argc, char* argv[]) {
    // If first arg == "1" start socket server mode, else run interactive console
    if (argc >= 2 && std::string(argv[1]) == "1") {
        int port = 12345;
        if (argc >= 3) {
            try {
                port = std::stoi(argv[2]);
            } catch (...) {
                // fall back to default
            }
        }
        return StartSocketServer(port);
    }

    TW::WalletConsole::WalletConsole console(std::cin, std::cout);
    console.loop();
    return 0;
}