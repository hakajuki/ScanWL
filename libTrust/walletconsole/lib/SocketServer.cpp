// SPDX-License-Identifier: Apache-2.0
//
// Minimal POSIX TCP socket server that accepts text commands (newline-terminated)
// and forwards them to the CommandExecutor. Intended for local use (macOS/Linux).

#include "SocketServer.h"

#if defined(__EMSCRIPTEN__)
// No socket support in browser WASM build — provide stub
#include <iostream>
int StartSocketServer(int /*port*/) {
    std::cerr << "Socket server mode is not supported for Emscripten/WASM builds." << std::endl;
    return -1;
}

#else

#include "CommandExecutor.h"
#include "WalletConsole.h"
#include "Util.h"
#include "../../src/HDWallet.h"
#include "../../src/HexCoding.h"
#include "../../src/Hash.h"
#include <TrustWalletCore/TWCoinType.h>

#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>

#include <iostream>
#include <streambuf>
#include <ostream>
#include <string>
#include <vector>
#include <thread>
#include <signal.h>
#include <fstream>
#include <random>

namespace {

// Helper: send all bytes on socket
static ssize_t send_all(int sock, const char* buf, size_t len) {
    size_t total = 0;
    while (total < len) {
        ssize_t sent = send(sock, buf + total, len - total, 0);
        if (sent < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        total += (size_t)sent;
    }
    return (ssize_t)total;
}

// streambuf that writes directly to a socket
class SocketStreamBuf : public std::streambuf {
public:
    explicit SocketStreamBuf(int sockfd) : sock(sockfd) {}
    ~SocketStreamBuf() override {}

protected:
    // write multiple chars
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (sock < 0) return 0;
        ssize_t res = send_all(sock, s, (size_t)n);
        return res < 0 ? 0 : res;
    }

    // write single char
    int_type overflow(int_type ch) override {
        if (sock < 0) return traits_type::eof();
        char c = ch;
        ssize_t res = send_all(sock, &c, 1);
        return res < 0 ? traits_type::eof() : ch;
    }

    int sync() override { return 0; }

private:
    int sock;
};

} // anonymous namespace

int StartSocketServer(int port, int walletMode) {
    int listenfd = socket(AF_INET, SOCK_STREAM, 0);
    if (listenfd < 0) {
        perror("socket");
        return -1;
    }

    int opt = 1;
    setsockopt(listenfd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons((uint16_t)port);

    if (bind(listenfd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        perror("bind");
        close(listenfd);
        return -1;
    }

    if (listen(listenfd, 1) < 0) {
        perror("listen");
        close(listenfd);
        return -1;
    }

    std::cout << "Socket server listening on port " << port << " with mode " << walletMode << "\n";

    // Ignore SIGPIPE to avoid termination when writing to closed sockets
    signal(SIGPIPE, SIG_IGN);

    // Handler for each client connection
    auto handle_client = [walletMode](int clientfd) {
        // Ensure socket is closed when function exits
        SocketStreamBuf sbb(clientfd);
        std::ostream out(&sbb);

        // Print banner, like WalletConsole::init
        // out << std::endl;
        // out << "Wallet-core Console                          (c) TrustWallet" << std::endl;
        // out << "Type 'help' for list of commands." << std::endl;
        // out << std::endl;

        // Create CommandExecutor and init
        TW::WalletConsole::CommandExecutor executor(out);
        executor.init();

        // Generate a BIP39-compliant wallet and write single-line JSON to `out`.
        // BIP39 pipeline: OS CSPRNG → 128-bit entropy → SHA256 checksum (4 bits)
        //                 → 132 bits ÷ 11 → 12 word indices → mnemonic
        auto generateWalletJson = [&out]() {
            try {
                TW::HDWallet<> wallet(128, "");
                const std::string mnemonic = wallet.getMnemonic();

                struct CoinDef { TWCoinType id; const char* name; };
                CoinDef coinDefs[] = {
                    { TWCoinTypeBitcoin,  "bitcoin"  },
                    { TWCoinTypeEthereum, "ethereum" },
                    { TWCoinTypeTron,     "tron"     }
                };

                struct CoinResult { std::string name; std::string privateKey; std::string address; };
                std::vector<CoinResult> results;

                for (auto& c: coinDefs) {
                    try {
                        auto pk   = wallet.getKey(c.id, TWDerivationDefault);
                        auto addr = wallet.deriveAddress(c.id);
                        results.push_back({ c.name, TW::hex(pk.bytes, true), addr });
                    } catch (const std::exception& ex) {
                        results.push_back({ c.name, "", std::string("error: ") + ex.what() });
                    }
                }

                out << "{\"mnemonic\":\"" << mnemonic << "\",\"coins\":[";
                for (size_t i = 0; i < results.size(); ++i) {
                    const auto& r = results[i];
                    if (i) out << ",";
                    out << "{\"name\":\"" << r.name << "\","
                        << "\"privateKey\":\"" << r.privateKey << "\","
                        << "\"address\":\"" << r.address << "\"}";
                }
                out << "]}\n";

            } catch (const std::exception& ex) {
                out << "{\"error\":\"" << ex.what() << "\"}\n";
            }
        };

        // Alternative BIP39 generation: load wordlist from file, pick 11 random words,
        // derive entropy bits from their indices, then deterministically fix word 12
        // so the checksum is valid.
        //
        // Bit layout (128-bit entropy, 4-bit checksum):
        //   Words 1-11 → 11 × 11 = 121 bits of entropy
        //   7 random bits  → bits 121-127 (tail of entropy)
        //   checksum = SHA256(entropy)[top 4 bits]
        //   Word 12 index = [7-bit tail] ++ [4-bit checksum]  (11 bits)
        auto generateWalletJsonFromWordlist = [&out]() {
            // Load wordlist
            std::vector<std::string> wordlist;
            for (const char* p : {"bips/english.txt", "../walletconsole/bips/english.txt"}) {
                std::ifstream ifs(p);
                if (!ifs) continue;
                std::string w;
                while (std::getline(ifs, w)) {
                    if (!w.empty() && w.back() == '\r') w.pop_back(); // strip CRLF
                    TW::WalletConsole::Util::trimLeft(w);
                    if (!w.empty()) wordlist.push_back(w);
                }
                if (!wordlist.empty()) break;
            }
            if (wordlist.size() != 2048) {
                out << "{\"error\":\"BIP39 wordlist not found or invalid (need exactly 2048 words)\"}\n";
                return;
            }

            // CSPRNG
            std::random_device rd;
            std::mt19937 rng(rd());
            std::uniform_int_distribution<int> dist2048(0, 2047); // 11-bit word index
            std::uniform_int_distribution<int> dist128(0, 127);   // 7-bit tail entropy

            // Step 1: pick 11 random word indices → pack into entropy bits 0-120
            uint8_t entropy[16] = {0};
            std::vector<int> wordIndices(12);
            for (int w = 0; w < 11; w++) {
                wordIndices[w] = dist2048(rng);
                int startBit = w * 11;
                for (int b = 0; b < 11; b++) {
                    int bit = (wordIndices[w] >> (10 - b)) & 1;
                    int bytePos = (startBit + b) / 8;
                    int bitPos  = 7 - ((startBit + b) % 8);
                    entropy[bytePos] |= (uint8_t)(bit << bitPos);
                }
            }

            // Step 2: pick 7 random bits → pack into entropy bits 121-127
            int tailBits = dist128(rng);
            for (int b = 0; b < 7; b++) {
                int bit     = (tailBits >> (6 - b)) & 1;
                int bytePos = (121 + b) / 8;
                int bitPos  = 7 - ((121 + b) % 8);
                entropy[bytePos] |= (uint8_t)(bit << bitPos);
            }

            // Step 3: SHA256(entropy) → top 4 bits = checksum
            TW::Data entropyVec(entropy, entropy + 16);
            TW::Data hash = TW::Hash::sha256(entropyVec);
            int checksum4 = (hash[0] >> 4) & 0x0F;

            // Step 4: word 12 index = tailBits(7 bits) << 4 | checksum4(4 bits)
            wordIndices[11] = (tailBits << 4) | checksum4;

            // Build mnemonic
            std::string mnemonic;
            for (int i = 0; i < 12; i++) {
                if (i) mnemonic += " ";
                mnemonic += wordlist[wordIndices[i]];
            }

            // Derive addresses — mnemonic now has a valid BIP39 checksum
            try {
                TW::HDWallet<> wallet(mnemonic, "", true);

                struct CoinDef { TWCoinType id; const char* name; };
                CoinDef coinDefs[] = {
                    { TWCoinTypeBitcoin,  "bitcoin"  },
                    { TWCoinTypeEthereum, "ethereum" },
                    { TWCoinTypeTron,     "tron"     }
                };

                struct CoinResult { std::string name; std::string privateKey; std::string address; };
                std::vector<CoinResult> results;

                for (auto& c: coinDefs) {
                    try {
                        auto pk   = wallet.getKey(c.id, TWDerivationDefault);
                        auto addr = wallet.deriveAddress(c.id);
                        results.push_back({ c.name, TW::hex(pk.bytes, true), addr });
                    } catch (const std::exception& ex) {
                        results.push_back({ c.name, "", std::string("error: ") + ex.what() });
                    }
                }

                out << "{\"mnemonic\":\"" << mnemonic << "\",\"coins\":[";
                for (size_t i = 0; i < results.size(); ++i) {
                    const auto& r = results[i];
                    if (i) out << ",";
                    out << "{\"name\":\"" << r.name << "\","
                        << "\"privateKey\":\"" << r.privateKey << "\","
                        << "\"address\":\"" << r.address << "\"}";
                }
                out << "]}\n";

            } catch (const std::exception& ex) {
                out << "{\"error\":\"" << ex.what() << "\"}\n";
            }
        };

        // Send a fresh wallet to the client immediately on connect
        // Mode 2 uses the wordlist-file method; everything else uses the HDWallet CSPRNG method
        if (walletMode == 2) {
            generateWalletJsonFromWordlist();
        } else {
            generateWalletJson();
        }

        // Read from client one byte at a time, accumulate lines
        std::string line;
        char ch;
        while (true) {
            ssize_t n = recv(clientfd, &ch, 1, 0);
            if (n <= 0) break; // client closed or error
            if (ch == '\r') continue;
            if (ch == '\n') {
                TW::WalletConsole::Util::trimLeft(line);
                if (line.size() == 0) {
                    line.clear();
                    continue;
                }
                if (TW::WalletConsole::WalletConsole::isExit(line)) {
                    out << "Bye!" << std::endl << std::endl;
                    break;
                }
                if (line == "newwallet") {
                    if (walletMode == 2) {
                        generateWalletJsonFromWordlist();
                    } else {
                        generateWalletJson();
                    }
                    line.clear();
                    continue;
                }
                executor.executeLine(line);
                line.clear();
            } else {
                line.push_back(ch);
            }
        }

        close(clientfd);
    };

    while (true) {
        struct sockaddr_in clientAddr;
        socklen_t clientLen = sizeof(clientAddr);
        int clientfd = accept(listenfd, (struct sockaddr*)&clientAddr, &clientLen);
        if (clientfd < 0) {
            perror("accept");
            break;
        }

        try {
            std::thread th(handle_client, clientfd);
            th.detach();
        } catch (const std::exception& ex) {
            std::cerr << "Failed to spawn thread for client: " << ex.what() << std::endl;
            close(clientfd);
        }
    }

    close(listenfd);
    return 0;
}

#endif // __EMSCRIPTEN__
