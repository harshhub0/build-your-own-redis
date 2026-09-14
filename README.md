# Redis In Java

A Redis-compatible in-memory data store implemented from scratch in **Java 17**.

This project is built to understand how a Redis-like server works internally, including networking, the RESP protocol, command dispatching, Redis data types, expiration, transactions, Pub/Sub, and persistence.

The server communicates using the **RESP (Redis Serialization Protocol)** and can be accessed using `redis-cli`, the built-in CLI, or other Redis-compatible clients.

---

## Features

### Networking

* TCP server on port `6379` by default
* Java NIO based non-blocking networking
* Selector-based event loop
* Multiple client connections
* Non-blocking reads and writes
* Handles partial TCP packets
* Per-client connection state

### RESP Protocol

Implements parsing and writing of Redis RESP messages.

Supported protocol components include:

* Simple Strings
* Bulk Strings
* Arrays
* Integers
* Errors
* Null Bulk Strings

This allows the server to communicate with standard Redis clients.

### Redis Data Types

The implementation supports:

* Strings
* Lists
* Sets
* Hashes
* Sorted Sets

Sorted Sets are implemented using a custom **Skip List** based data structure.

### Key Management

Supports:

* Key existence checks
* Key expiration
* TTL / PTTL
* Persistent keys
* Key renaming
* Key scanning
* Pattern-based key lookup
* Random key selection
* Database size

### Transactions

Supports Redis-style transactions:

```text
MULTI
EXEC
DISCARD
WATCH
UNWATCH
```

Commands issued after `MULTI` are queued and executed when `EXEC` is called.

### Pub/Sub

Supports:

```text
SUBSCRIBE
UNSUBSCRIBE
PSUBSCRIBE
PUNSUBSCRIBE
PUBLISH
PUBSUB
```

The server maintains subscription state for connected clients and delivers published messages to matching subscribers.

### Persistence

Two persistence mechanisms are implemented.

#### AOF - Append Only File

Write commands can be persisted to an AOF file.

Default:

```text
appendonly.aof
```

Supported fsync policies:

```text
always
everysec
no
```

AOF also supports rewriting/compaction to reduce the size of the log.

#### RDB

The project also implements an RDB-style snapshot mechanism.

Default snapshot:

```text
dump.rdb
```

Supported operations include:

```text
SAVE
BGSAVE
```

The snapshot stores:

* Multiple databases
* Keys
* Redis data types
* Expiration timestamps

### Authentication

A password can be configured using:

```text
--requirepass <password>
```

Unauthenticated clients are restricted until `AUTH` is executed.

### Multiple Databases

The server supports multiple isolated Redis databases.

Default:

```text
16 databases
```

Database selection is performed using:

```text
SELECT <db>
```

---

## Architecture

The project is divided into several layers.

```text
                         Redis Client
                    redis-cli / Built-in CLI
                              |
                              | TCP
                              v
                  +-------------------------+
                  |      RedisServer        |
                  |      Java NIO           |
                  |      Selector           |
                  +------------+------------+
                               |
                               v
                  +-------------------------+
                  |    ClientConnection     |
                  | Read / Write Buffers    |
                  +------------+------------+
                               |
                               v
                  +-------------------------+
                  |      RESP Parser         |
                  |      RESP Writer         |
                  +------------+------------+
                               |
                               v
                  +-------------------------+
                  |    CommandRegistry      |
                  | Command Dispatch        |
                  +------------+------------+
                               |
             +-----------------+------------------+
             |                 |                  |
             v                 v                  v
       StringCommands     ListCommands       HashCommands
             |                 |                  |
             +-----------------+------------------+
                               |
             +-----------------+------------------+
             |                 |                  |
             v                 v                  v
        SetCommands       ZSetCommands      ServerCommands
                               |
                               v
                    +---------------------+
                    |    RedisDatabase    |
                    |      DataStore      |
                    +----------+----------+
                               |
             +-----------------+------------------+
             |                                    |
             v                                    v
       ExpiryEngine                         RedisValue
                                                  |
                            +---------------------+-------------------+
                            |          |          |          |       |
                          String      List       Set       Hash    ZSet
                                                               |
                                                               v
                                                           SkipList

                               |
                    +----------+-----------+
                    |                      |
                    v                      v
              AOF Persistence       RDB Persistence
              appendonly.aof          dump.rdb
```

---

## Project Structure

```text
src/main/java/com/redis
│
├── Main.java
│
├── cli/
│   └── RedisCli.java
│
├── commands/
│   ├── Command.java
│   ├── CommandContext.java
│   ├── CommandRegistry.java
│   ├── StringCommands.java
│   ├── ListCommands.java
│   ├── HashCommands.java
│   ├── SetCommands.java
│   ├── ZSetCommands.java
│   ├── KeyCommands.java
│   ├── ServerCommands.java
│   ├── TransactionCommands.java
│   └── PubSubCommands.java
│
├── config/
│   └── ServerConfig.java
│
├── datastructures/
│   ├── SkipList.java
│   ├── ZSet.java
│   └── ZSetEntry.java
│
├── net/
│   ├── ClientConnection.java
│   ├── PubSubManager.java
│   └── RedisServer.java
│
├── persistence/
│   ├── AofEngine.java
│   └── RdbEngine.java
│
├── resp/
│   ├── RespParser.java
│   ├── RespToken.java
│   └── RespWriter.java
│
├── storage/
│   ├── DataStore.java
│   ├── ExpiryEngine.java
│   ├── RedisDatabase.java
│   ├── RedisType.java
│   └── RedisValue.java
│
└── test/
    ├── EndToEndServerTest.java
    ├── RespParserTest.java
    ├── SkipListTest.java
    ├── StorageCommandsTest.java
    ├── TransactionTest.java
    └── TestRunner.java
```

---

## Requirements

* Java 17+
* Maven 3.8+

Check Java:

```bash
java -version
```

Check Maven:

```bash
mvn -version
```

---

## Build

Clone the repository:

```bash
git clone <repository-url>
cd build-your-own-redis
```

Build the project:

```bash
mvn clean package
```

This generates the executable JAR under:

```text
target/
```

---

## Run the Server

Using Maven:

```bash
mvn package
java -jar target/redis-java-1.0.0.jar
```

Or, after building:

```bash
java -jar target/redis-java-1.0.0.jar
```

The server listens on:

```text
0.0.0.0:6379
```

by default.

---

## Start the Built-in CLI

The project includes its own Java CLI client.

```bash
java -jar target/redis-java-1.0.0.jar cli
```

You can also specify a host and port:

```bash
java -jar target/redis-java-1.0.0.jar cli -h 127.0.0.1 -p 6379
```

---

## Using redis-cli

Because the server implements RESP, a standard Redis CLI can be used:

```bash
redis-cli -p 6379
```

Example:

```text
127.0.0.1:6379> SET name "Harsh"
OK

127.0.0.1:6379> GET name
"Harsh"

127.0.0.1:6379> INCR counter
(integer) 1
```

---

# Configuration

The server supports command-line configuration.

### Port

```bash
java -jar target/redis-java-1.0.0.jar --port 6380
```

Default:

```text
6379
```

### Bind Address

```bash
java -jar target/redis-java-1.0.0.jar --bind 127.0.0.1
```

Default:

```text
0.0.0.0
```

### Data Directory

```bash
java -jar target/redis-java-1.0.0.jar --dir ./data
```

### AOF

Enable AOF:

```bash
java -jar target/redis-java-1.0.0.jar --appendonly yes
```

Disable AOF:

```bash
java -jar target/redis-java-1.0.0.jar --appendonly no
```

### AOF Fsync

```bash
--appendfsync always
```

```bash
--appendfsync everysec
```

```bash
--appendfsync no
```

Default:

```text
everysec
```

### Password

```bash
java -jar target/redis-java-1.0.0.jar --requirepass mypassword
```

Then authenticate:

```text
AUTH mypassword
```

### Number of Databases

```bash
java -jar target/redis-java-1.0.0.jar --databases 16
```

Default:

```text
16
```

---

# Supported Commands

## Strings

```text
SET
GET
MSET
MGET
MSETNX
SETNX
SETEX
PSETEX
GETSET
INCR
DECR
INCRBY
DECRBY
INCRBYFLOAT
APPEND
STRLEN
```

Example:

```text
SET counter 10
INCR counter
GET counter
```

`SET` supports options such as:

```text
EX
PX
EXAT
PXAT
NX
XX
GET
KEEPTTL
```

Example:

```text
SET session abc123 EX 60
```

---

## Keys

```text
DEL
EXISTS
TYPE
EXPIRE
PEXPIRE
EXPIREAT
PEXPIREAT
TTL
PTTL
PERSIST
KEYS
SCAN
RENAME
RENAMENX
RANDOMKEY
DBSIZE
```

Example:

```text
SET temp value EX 30
TTL temp
```

---

## Lists

```text
LPUSH
RPUSH
LPUSHX
RPUSHX
LPOP
RPOP
LLEN
LRANGE
LINDEX
LSET
LREM
LTRIM
RPOPLPUSH
```

Example:

```text
LPUSH tasks "task-1"
LPUSH tasks "task-2"
LRANGE tasks 0 -1
```

---

## Hashes

```text
HSET
HGET
HDEL
HEXISTS
HLEN
HGETALL
HKEYS
HVALS
HMSET
HMGET
HINCRBY
HINCRBYFLOAT
HSETNX
```

Example:

```text
HSET user:1 name Harsh age 25
HGET user:1 name
HGETALL user:1
```

---

## Sets

```text
SADD
SREM
SMEMBERS
SISMEMBER
SMISMEMBER
SCARD
SPOP
SRANDMEMBER
SUNION
SINTER
SDIFF
SMOVE
```

Example:

```text
SADD languages Java Python Go
SMEMBERS languages
```

---

## Sorted Sets

```text
ZADD
ZREM
ZSCORE
ZMSCORE
ZRANK
ZREVRANK
ZRANGE
ZREVRANGE
ZRANGEBYSCORE
ZREVRANGEBYSCORE
ZCARD
ZCOUNT
ZINCRBY
ZPOPMIN
ZPOPMAX
```

Example:

```text
ZADD leaderboard 100 Alice
ZADD leaderboard 200 Bob
ZADD leaderboard 150 Charlie

ZRANGE leaderboard 0 -1 WITHSCORES
```

The sorted-set implementation uses a custom Skip List.

---

# Transactions

Start a transaction:

```text
MULTI
```

Queue commands:

```text
SET a 10
SET b 20
INCR a
```

Execute:

```text
EXEC
```

Example:

```text
MULTI
SET name Harsh
SET age 25
INCR age
EXEC
```

Discard queued commands:

```text
DISCARD
```

Optimistic concurrency control is supported through:

```text
WATCH
UNWATCH
```

---

# Pub/Sub

Subscribe to a channel:

```text
SUBSCRIBE news
```

Publish a message from another client:

```text
PUBLISH news "Hello Redis"
```

Pattern subscriptions are also supported:

```text
PSUBSCRIBE news:*
```

Unsubscribe using:

```text
UNSUBSCRIBE news
```

or:

```text
PUNSUBSCRIBE news:*
```

---

# Persistence

## AOF

When AOF is enabled, write commands are appended to:

```text
appendonly.aof
```

Example:

```text
SET name Harsh
INCR counter
LPUSH queue task
```

These write operations are persisted as RESP-encoded commands.

On restart, the AOF can be replayed to reconstruct the in-memory state.

AOF rewriting is available through:

```text
BGREWRITEAOF
```

---

## RDB

The server can create a point-in-time snapshot:

```text
SAVE
```

For background saving:

```text
BGSAVE
```

The snapshot is stored as:

```text
dump.rdb
```

The implementation stores the database contents along with expiration information.

---

# Expiration

Keys can have an expiration time.

Example:

```text
SET token abc EX 60
```

The key expires after 60 seconds.

Milliseconds:

```text
SET token abc PX 5000
```

Check remaining lifetime:

```text
TTL token
```

or:

```text
PTTL token
```

Expiration uses passive eviction: when a key is accessed, its expiration timestamp is checked and the key is removed if it has expired.

---

# RESP Request Flow

A command such as:

```text
SET name Harsh
```

is sent by the client as a RESP array.

Conceptually:

```text
*3
$3
SET
$4
name
$5
Harsh
```

The server processes it through:

```text
TCP Socket
    |
    v
ClientConnection
    |
    v
RespParser
    |
    v
RespToken
    |
    v
CommandRegistry
    |
    v
StringCommands.set()
    |
    v
RedisDatabase
    |
    v
RespWriter
    |
    v
TCP Socket
```

The response:

```text
+OK
```

is then returned to the client.

---

# Networking Model

The server uses Java NIO rather than creating one blocking thread per client.

The basic flow is:

```text
ServerSocketChannel
        |
        v
     Selector
        |
        +---- OP_ACCEPT
        |
        +---- OP_READ
        |
        +---- OP_WRITE
```

When a client connects:

```text
Client
  |
  v
ServerSocketChannel
  |
  v
accept()
  |
  v
SocketChannel
  |
  v
ClientConnection
  |
  v
Selector
```

For incoming data:

```text
SocketChannel
      |
      v
Read Buffer
      |
      v
RESP Parser
      |
      v
Command Execution
```

For outgoing data:

```text
Command Result
      |
      v
RESP Writer
      |
      v
Write Queue
      |
      v
SocketChannel
```

This also handles the case where a command is split across multiple TCP packets.

---

# Storage Model

The project maintains multiple logical Redis databases.

```text
DataStore
   |
   +--- Database 0
   |      |
   |      +--- key -> RedisValue
   |
   +--- Database 1
   |      |
   |      +--- key -> RedisValue
   |
   +--- Database 2
   |
   +--- ...
   |
   +--- Database 15
```

Each `RedisDatabase` maintains:

```text
entries
expires
keyVersions
```

`entries` stores the actual Redis values.

`expires` stores expiration timestamps.

`keyVersions` is used for transaction/watch related change tracking.

---

# RedisValue

Values are represented using a common `RedisValue` abstraction.

```text
RedisValue
    |
    +--- STRING
    +--- LIST
    +--- SET
    +--- HASH
    +--- ZSET
```

This allows commands to verify the type of a key and return Redis-style `WRONGTYPE` errors when necessary.

Example:

```text
SET user hello
LPUSH user value
```

The second operation results in a wrong-type error because `user` already contains a String.

---

# Skip List

Sorted Sets use a custom Skip List implementation.

Conceptually:

```text
Level 3:     ---------------------> 200
Level 2:     ---------> 100 -------> 200
Level 1:     -> 50 ---> 100 ---> 150 ---> 200
Level 0:     -> 50 ---> 100 ---> 150 ---> 200 ---> 300
```

This provides an efficient ordered structure for operations such as:

```text
ZRANGE
ZRANK
ZADD
ZREM
ZRANGEBYSCORE
```

---

# Testing

The project includes tests for the major components.

```text
EndToEndServerTest
RespParserTest
SkipListTest
StorageCommandsTest
TransactionTest
```

A custom test runner is also included:

```text
com.redis.test.TestRunner
```

Build and run tests with:

```bash
mvn test
```

---

# Example Session

Start the server:

```bash
java -jar target/redis-java-1.0.0.jar
```

Connect:

```bash
redis-cli -p 6379
```

Then:

```text
127.0.0.1:6379> SET name Harsh
OK

127.0.0.1:6379> GET name
"Harsh"

127.0.0.1:6379> SET counter 10
OK

127.0.0.1:6379> INCR counter
(integer) 11

127.0.0.1:6379> LPUSH queue task1
(integer) 1

127.0.0.1:6379> LPUSH queue task2
(integer) 2

127.0.0.1:6379> LRANGE queue 0 -1
1) "task2"
2) "task1"

127.0.0.1:6379> HSET user name Harsh
(integer) 1

127.0.0.1:6379> HGET user name
"Harsh"
```

---

# Design Goals

This project focuses on understanding the internal architecture of a Redis-like database rather than reproducing every Redis feature.

The main goals are:

1. Understand how a TCP server handles multiple clients.
2. Implement a Redis-compatible wire protocol.
3. Build Redis data types from scratch.
4. Implement key expiration.
5. Understand command dispatch and execution.
6. Implement transactions and optimistic key watching.
7. Implement Pub/Sub.
8. Understand AOF and RDB persistence.
9. Implement an ordered data structure using Skip Lists.
10. Build an end-to-end database server using Java's standard library.

---

# Limitations

This is an educational Redis implementation and is **not intended to be a production replacement for Redis**.

It does not aim to provide all Redis features, production-level durability guarantees, clustering, replication, Sentinel, Lua scripting, modules, or the full Redis command set.

Performance and behavior may also differ from official Redis.

---

# Tech Stack

* **Java 17**
* **Maven**
* **Java NIO**
* **TCP/IP**
* **RESP**
* **ConcurrentHashMap**
* **Concurrent data structures**
* **Custom Skip List**
* **AOF persistence**
* **RDB-style snapshots**

No external runtime dependencies are required.

---

# Learning Outcomes

By working through this project, you can understand how the major pieces of an in-memory database fit together:

```text
Network
   ↓
Protocol
   ↓
Command Dispatcher
   ↓
Data Structures
   ↓
Expiration
   ↓
Transactions
   ↓
Persistence
```

The project therefore serves as a practical implementation of the core concepts behind a Redis-style server.

---

## License

Add the appropriate license for your repository here.

For example:

```text
MIT License
```
