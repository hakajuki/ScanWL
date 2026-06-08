// SPDX-License-Identifier: Apache-2.0
//
// Simple socket server interface for wallet console
// POSIX-only implementation provided in corresponding .cpp

#pragma once

// walletMode: 1 = BIP39 via HDWallet CSPRNG (default)
//             2 = BIP39 via wordlist file + checksum-fixed word 12
int StartSocketServer(int port, int walletMode = 1);
