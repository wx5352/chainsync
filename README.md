# chainsync

**Zero-config EVM multi-chain (ETH / BNB / Polygon) data synchronization for Java & Spring backends.**

[![Maven Central](https://img.shields.io/maven-central/v/io.github.wx5352/chainsync-spring-boot-starter?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.wx5352/chainsync-spring-boot-starter)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21%2B-orange.svg)](https://adoptium.net/)

[English](#english) | [中文](#中文)

---

## English

`chainsync` gives Java/Spring teams reliable, resumable, no-loss on-chain data with almost no code.
You write *"what to do when a block arrives"* — chainsync handles everything hard about pulling
blockchain data:

- **One API, three chains** — ETH, BNB Chain, Polygon (any EVM chain) via configuration only.
- **Resume / no data loss** — durable cursors; restart continues exactly where it left off (at-least-once delivery).
- **Reorg handling** — detects chain reorganizations via `parentHash` linkage, rewinds, and re-syncs.
- **Multi-RPC failover** — rotates across endpoints with retry + exponential backoff when a provider is down or rate-limited.
- **Adaptive polling** — full-speed concurrent catch-up when behind, gentle polling once at the head.
- **Built-in decoding** — decode ERC-20 / ERC-721 `Transfer` events out of the box via `TransferDecoder`.
- **Pluggable output** — persist to JDBC, publish to Kafka, or implement your own `BlockHandler`.

It is intentionally **not** another high-throughput indexer competing with Envio/SQD/Ponder — it is
the *easiest* way for a Java backend to consume EVM data.

### Installation

#### Maven Central (recommended)

No extra repository configuration needed — just add the dependency:

```xml
<dependency>
    <groupId>io.github.wx5352</groupId>
    <artifactId>chainsync-spring-boot-starter</artifactId>
    <version>0.1.2</version>
</dependency>
```

> Modules are published as `io.github.wx5352:<module>:<version>` — swap the `artifactId` for
> `chainsync-core`, `chainsync-store-jdbc`, `chainsync-store-kafka`, etc. as needed.

#### JitPack (alternative)

Also available via [JitPack](https://jitpack.io) (uses a `com.github.*` groupId and a tag as the
version):

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.wx5352.chainsync</groupId>
    <artifactId>chainsync-spring-boot-starter</artifactId>
    <version>v0.1.1</version>
</dependency>
```

### Quick start (Spring Boot)

**1. Add the dependency** (see [Installation](#installation) above)

**2. Configure your chains** (`application.yml`)

```yaml
chainsync:
  store: jdbc            # jdbc | memory
  chains:
    - name: ethereum
      chain-id: 1
      rpc-urls: [https://eth.llamarpc.com]
      start-block: latest
      confirmations: 12
    - name: bsc
      chain-id: 56
      rpc-urls: [https://bsc-dataseed.binance.org]
    - name: polygon
      chain-id: 137
      rpc-urls: [https://polygon-rpc.com]
```

**3. (Optional) Write a handler** — the only application code you need:

```java
@Component
public class MyHandler implements BlockHandler {
    @Override
    public void onBlock(SyncedBlock block) {
        // your logic: monitor an address, index transfers, trigger business events...
    }
}
```

If you don't declare a handler and a `DataSource` is present, blocks are persisted to
`chainsync_block` / `chainsync_transaction` automatically. Sync cursors are persisted to
`chainsync_cursor`.

Start your app — data flows in, reliably.

### Quick start (plain Java, no Spring)

```java
var chains = List.of(
    ChainConfig.builder("ethereum").chainId(1)
        .rpcUrls(List.of("https://eth.llamarpc.com"))
        .confirmations(12).build());

var synchronizer = new ChainSynchronizer(
    chains,
    new InMemoryCursorStore(),
    block -> System.out.println("block " + block.block().number()));

synchronizer.start();
```

### Modules

| Module | Purpose |
|---|---|
| `chainsync-core` | Framework-agnostic domain model + SPI (`BlockHandler`, `CursorStore`). Zero runtime deps. |
| `chainsync-engine` | Sync engine: adaptive polling, reorg handling, resume, multi-RPC failover (web3j). |
| `chainsync-store-jdbc` | JDBC `CursorStore` + block-persisting `BlockHandler`. |
| `chainsync-store-kafka` | Kafka `BlockHandler` publishing each block as JSON. |
| `chainsync-spring-boot-starter` | Auto-configuration from `chainsync.*` properties. |
| `chainsync-samples` | Runnable Spring Boot demo. |

### Publish to Kafka instead of a database

```yaml
chainsync:
  store: kafka
  kafka:
    topic-prefix: chainsync   # effective topic: chainsync.ethereum.blocks
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

Add `chainsync-store-kafka` + `spring-kafka` to the classpath and each synced block is published as a
JSON message, keyed by block number. Cursors are still persisted (e.g. via JDBC) for resume.

### Run the demo

```bash
mvn -pl chainsync-samples -am spring-boot:run
```

### Build

```bash
mvn clean verify
```

Requires JDK 21+.

### Releasing to Maven Central

Publishing is wired through the `release` profile (sources + javadoc + GPG signing +
`central-publishing-maven-plugin`) and the `release.yml` GitHub Actions workflow. Push a `v*` tag
with the required secrets configured, or run locally:

```bash
mvn -Prelease deploy
```

### License

Apache License 2.0.

---

## 中文

`chainsync` 让 Java/Spring 团队几乎零代码就能拿到**可靠、可续传、不丢不重**的链上数据。
你只需要写"拿到区块后要做什么"，其余最难的同步工程全部由 chainsync 处理：

- **一套 API，三条链** — 仅靠配置支持 ETH、BNB Chain、Polygon（任意 EVM 链）。
- **断点续传 / 不丢数据** — 游标持久化，重启从上次位置继续（at-least-once 投递）。
- **Reorg 处理** — 通过 `parentHash` 链式校验检测分叉，自动回滚并重新同步。
- **多 RPC 容灾** — 节点宕机或限流时自动轮换端点，配合重试与指数退避。
- **自适应轮询** — 落后时全速并发追赶，追平后温和轮询新块。
- **可插拔输出** — 默认落库（JDBC），也可实现 `BlockHandler` 接入自己的逻辑（Kafka、业务事件等）。

它**不是**又一个和 Envio/SQD/Ponder 拼吞吐的索引器，而是让 Java 后端**最省心**地消费 EVM 数据的方式。

### 安装

#### Maven Central（推荐）

无需额外配置仓库，直接引入依赖即可：

```xml
<dependency>
    <groupId>io.github.wx5352</groupId>
    <artifactId>chainsync-spring-boot-starter</artifactId>
    <version>0.1.2</version>
</dependency>
```

> 各模块以 `io.github.wx5352:<模块名>:<版本>` 发布，按需把 `artifactId` 换成
> `chainsync-core`、`chainsync-store-jdbc`、`chainsync-store-kafka` 等。

#### JitPack（备选）

也可通过 [JitPack](https://jitpack.io) 引入（groupId 为 `com.github.*`，版本用 tag）：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.wx5352.chainsync</groupId>
    <artifactId>chainsync-spring-boot-starter</artifactId>
    <version>v0.1.1</version>
</dependency>
```

### 快速开始（Spring Boot）

**1. 引入依赖**（见上方 [安装](#安装)）

**2. 配置链**（`application.yml`）

```yaml
chainsync:
  store: jdbc
  chains:
    - name: ethereum
      chain-id: 1
      rpc-urls: [https://eth.llamarpc.com]
      confirmations: 12
    - name: bsc
      chain-id: 56
      rpc-urls: [https://bsc-dataseed.binance.org]
    - name: polygon
      chain-id: 137
      rpc-urls: [https://polygon-rpc.com]
```

**3.（可选）写一个处理器** —— 你唯一需要写的业务代码：

```java
@Component
public class MyHandler implements BlockHandler {
    @Override
    public void onBlock(SyncedBlock block) {
        // 你的逻辑：监控地址入账、索引 Transfer、触发业务事件……
    }
}
```

不写处理器且存在 `DataSource` 时，区块会自动落到 `chainsync_block` / `chainsync_transaction`，
同步游标落到 `chainsync_cursor`。

启动应用，数据就可靠地流进来了。

### 运行示例

```bash
mvn -pl chainsync-samples -am spring-boot:run
```

### 构建

```bash
mvn clean verify
```

需要 JDK 21+。

### 许可证

Apache License 2.0。
