# SPDX-License-Identifier: Apache-2.0
#
# Copyright © 2017 Trust Wallet.

# walletconsolelib library
file(GLOB_RECURSE walletconsolelib_sources *.cpp)
add_library(walletconsolelib ${walletconsolelib_sources})
# Ensure SocketServer.cpp is included (may not be picked up by glob in some CMake runs)
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/SocketServer.cpp")
	list(APPEND walletconsolelib_sources "${CMAKE_CURRENT_SOURCE_DIR}/SocketServer.cpp")
	target_sources(walletconsolelib PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/SocketServer.cpp")
endif()
#target_link_libraries(tests gtest_main TrezorCrypto TrustWalletCore protobuf Boost::boost)
target_link_libraries(walletconsolelib TrezorCrypto TrustWalletCore protobuf Boost::boost)
target_include_directories(walletconsolelib PRIVATE ${CMAKE_SOURCE_DIR}/include ${CMAKE_SOURCE_DIR}/src)
