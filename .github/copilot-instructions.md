<!-- Copilot / AI assistant instructions for contributors and agents -->
# Repo overview (short)
- **Purpose:** PlanCoinScan is a Java (Maven) tool that connects to a socket feed of wallet JSON messages, parses wallet data, checks balances across BTC/ETH/BSC/TRON, logs and notifies when funds are found.
- **Language / Build:** Java 17, Maven. Key deps: `web3j`, `okhttp`, `gson`, `log4j`, `bouncycastle`.

# High-level architecture (what to read first)
- **Entrypoint:** `com.hktools.Main` — starts one or more `WalletClient` instances that connect to a socket server.
- **Socket & parsing:** `SocketClient` handles TCP connection + reconnect/backoff. `JsonMessageParser` accepts single-line JSON and produces `Wallet` objects.
- **Balance checks:** `WalletClient` composes scanning services: `BtcScan`, `EthereumWeb3` (ETH & BSC), `Tronscan`. These use `HttpClientConfig` (OkHttp) + `ConfigLoader` for endpoints.
- **Output / alerts:** wallets with balances are appended to `wallets_with_balance.txt`, logged with the `WALLET_FOUND` marker, and sent to Telegram via `service/Telegram`.

# Important repo-specific patterns & conventions
- `ConfigLoader` is a singleton that reads/writes `config.txt` in the repository root. Running the app will auto-create `config.txt` with default values if missing.
- Several "tests" are implemented as `public static void main` classes under `src/main/java` (e.g., `com.hktools.config.ConfigLoaderTest`) — they are runnable helpers, not JUnit tests.
- The code does not use dependency injection; services are created inline (e.g., `new BtcScan()`, `new EthereumWeb3(...)`). Prefer minimal, surgical changes when updating constructors.
- Logging: SLF4J + Log4j2. A dedicated marker `WALLET_FOUND` is used for high-signal lines; use that marker when emitting wallet-balance logs.

# Safety & secrets (must-read)
- `ConfigLoader` populates `config.txt` with default tokens/URLs (including a Telegram bot token) — treat these as sensitive. Do not commit real secrets to the repo. When testing, update `config.txt` locally.
- `HttpClientConfig` deliberately disables SSL verification and forces `hostnameVerifier` to accept all certificates and configures a proxy `127.0.0.1:8888` for debugging. This is insecure for production — remove or harden before publishing or running against real endpoints.

# Build / run / debug (exact commands)
- Build JAR (compile):
  - `mvn clean package`
- Run without changing `pom.xml` (use the exec plugin inline):
  - `mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:exec -Dexec.mainClass="com.hktools.Main" -Dexec.args="1"`
- Alternative: copy dependencies and run the class directly (useful for ad-hoc runs):
  - `mvn dependency:copy-dependencies -DoutputDirectory=target/dependency`
  - `java -cp target/classes:target/dependency/* com.hktools.Main 1`
- Run the config test helper:
  - `mvn org.codehaus.mojo:exec-maven-plugin:3.1.0:exec -Dexec.mainClass="com.hktools.config.ConfigLoaderTest"`

# Quick developer tips (concrete examples)
- To change HTTP endpoints, edit `config.txt` (or override at runtime) — `ConfigLoader` parses comma-separated URLs for ETH/BSC.
- To rotate or add RPC endpoints for web3, update `ethereum.urls` / `bsc.urls` in `config.txt`. `EthereumWeb3` rotates URLs on 403/429 errors.
- If you need to debug HTTP traffic, `HttpClientConfig` already points to `127.0.0.1:8888` (Charles/Fiddler). Remove the proxy in production.
- To add a new coin scanner, follow `service/*` pattern: create a scanner class, return standardized numeric balance or BigDecimal, and integrate into `WalletClient.checkBalancesAndSave`.

# Files and locations to inspect for changes
- Entry & orchestration: `src/main/java/com/hktools/Main.java`, `WalletClient.java`, `SocketClient.java`.
- Parsing & models: `src/main/java/com/hktools/parser/JsonMessageParser.java`, `src/main/java/com/hktools/model/*`.
- Scanners & infra: `src/main/java/com/hktools/service/*`, `src/main/java/com/hktools/config/HttpClientConfig.java`, `ConfigLoader.java`.
- Logs & resources: `src/main/resources/log4j2.xml` and `wallets_with_balance.txt` (created at runtime).

# What the AI should NOT change automatically
- Do not enable real Telegram tokens or commit new secrets. If a change requires secret values, prompt the human and suggest using environment variables or a secrets store.
- Do not remove the TLS bypass or proxy silently without confirmation — tests may rely on that during local debugging.
- Avoid large refactors across the codebase without explicit approval; prefer small, well-tested changes.

# If you're unsure, ask these targeted questions
- "Should I rotate or remove the HTTP proxy and trust-all TLS in `HttpClientConfig` for production?"
- "Where should new configuration values go — `config.txt` or environment variables?"
- "Do you want me to add a Maven plugin entry for exec or run instructions to `pom.xml`?"

---
If anything in this summary is incomplete or you want a different level of detail (runbook, test harness, or CI guidance), tell me which area to expand. 