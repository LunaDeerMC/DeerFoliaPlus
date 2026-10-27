# Servux 内存泄漏复核（2026-10-28，北京时间）

**结论：结构编码器与分片接收器的堆外泄漏均已独立复现。用户提供的主要代码判断和修正方向成立。现有材料不足以独立确认生产内存采样数字，以及该缺陷在某次历史 OOM 中占多大比例。**

本次仅新增验证工具与报告，未修改核心实现、配置、世界或线上服务。

## 测试对象与证据

- 当前 checkout：`16d1dffce779e8db573a0d85d52fa21cec4a1764`，26.3。
- 实测工件：工作区缓存的 **26.2-DEV-f24ea8b** 服务端 JAR；不是用户正式服 JAR。
- JAR SHA-256：`8b70e13cb09e8b09ee8df3025463b9473e50d6331b55e84b9c65c87bc3ebc976`。
- 运行时：Java `25.0.4.1`，Netty `4.2.15.Final`，native access 开启。
- 实测 cleaner：`io.netty.util.internal.CleanerJava24Linker`；默认 allocator 偏好 direct。
- 两组测试：先直接调用 JAR 内真实类；再将当前 checkout 的四个 Servux 源文件原样编译，使用相同依赖运行。整个 Servux 目录从 `f24ea8b` 到当前提交无差异，且本次源码工作区与 HEAD 一致。
- 共 12 个隔离 JVM 运行完成，所有预期观察的断言通过。每进程 `-Xmx256m`、`MaxDirectMemorySize=128m`，没有故意制造 OOM。

[测试入口](../tools/servux-memory-audit/run.py)、[复现说明](../tools/servux-memory-audit/README.md)、[保存的元数据与结果](servux-memory-audit-2026-10-28.json)。原始过程日志在 `build/servux-memory-audit/20261027T174920341037Z/`，目录时间使用 UTC。

这属于实际核心类的组件集成验证，没有启动完整 Minecraft 服务器；当前源码组依然使用 26.2 运行依赖，不是整套 26.3 服务端构建或正式服端到端验证。

## 结构发送泄漏：复现结果与原分析一致

问题位于 [ServuxStructuresProtocol.java](../DeerFoliaPlus-server/src/main/java/org/leavesmc/leaves/protocol/servux/ServuxStructuresProtocol.java) 第 446 行：

```java
buf.writeBytes(payload.buffer().readBytes(payload.buffer().readableBytes()));
```

两组工件均得到以下结果。每次数据大小为 256 KiB，输入与输出由测试明确释放；表格只计算遗失的中间副本。

| 编码次数 | 原编码器未释放 direct 内存 | 直接复制对照组 |
| --- | ---: | ---: |
| 64 | 16 MiB | 0 |
| 128 | 32 MiB | 0 |
| 256 | 64 MiB | 0 |
| 256 次后执行 GC | 64 MiB | 0 |

对照组使用 `buf.writeBytes(input, input.readableBytes())`，不创建临时 ByteBuf。结构 type-2 包在 **0、1、127、128、32762、262144、1048571、1048572、2097149 字节**九种长度下输出逐字节一致，源 readerIndex 的变化也一致；测试包含非零初始 readerIndex。

真实 `PacketSplitter.send → StructuresPayload.CODEC.encode` 三片调用也已运行：2,097,173 字节正文加 4 字节总长度前缀，遗失 **2,097,177 字节** direct 内存，重建出的正文内容正确。回调中的输入/输出释放由测试执行，用于隔离编码器临时副本的问题，不代表生产已有完整释放逻辑。

源码发送源为 heap，并不能否定此结论：实测 `Unpooled.buffer()` 是 heap，而其 `readBytes(int)` 返回 direct。对 Netty 字节码的核查显示该方法调用 `alloc().buffer(...)`，使用默认偏好 direct 的分配器；`writeBytes` 不负责释放这个源参数。

所有上述内存压力测试中，普通 **DirectBufferPool 的增长都是 0**。这直接验证了“DirectBufferPool 很小不能排除该 native 分配”的判断。GC witness 已被回收，但丢失内存仍在。结合 `CleanerJava24Linker` 的显式 `malloc/free`、其 buffer 对象没有自动 GC 释放路径，可判定为该运行配置下的持续原生内存泄漏，不能靠 GC 修复。不能将此结论机械推广成每一种 Java/Netty cleaner 都有相同表现。

## 分片与资源生命周期：不止一行

[PacketSplitter.java](../DeerFoliaPlus-server/src/main/java/org/leavesmc/leaves/protocol/servux/PacketSplitter.java) 第 98 行存在同样的 `writeBytes(data.readBytes(...))`。真实接收两片、共 6 字节，重组正确；释放两片输入和重组结果后，direct 指标仍净增 **6 字节**。

额外测试结果如下；这些断言验证的是当前缺陷的存在，不是宣称回归测试已经消除了缺陷。

| 场景 | 结果 |
| --- | --- |
| 未完成会话，释放输入 | 静态 session 表仍持有拼接缓冲区 |
| 仅 2 MiB 长度头、零正文 | 接受并立即分配 2 MiB heap；默认实际采用 64 MiB S2C 上限，而非已定义的 1 MiB C2S 上限 |
| 负长度 | 抛异常，坏 session 留存 |
| 大于 64 MiB 的长度头 | 不分配正文，但坏 session 留存，下一片触发 NPE |
| 声明 4 字节，累计收到 5 字节 | 接受并当作完成 |
| 正常发送 | 源被释放，分片由 handler 接管 |
| 发送 handler 抛异常 | 源和当前分片仍为 `refCnt=1` |

静态检查还确认以下需要修复的生命周期问题：

- `READING_SESSIONS` 只在收齐时删除，没有过期或取消机制。[实体协议](../DeerFoliaPlus-server/src/main/java/org/leavesmc/leaves/protocol/servux/ServuxEntityDataProtocol.java) 第 41 行的退出处理只删除 UUID 到 key 的映射，未清理实际 session；未完成会话的 heap 数据因此仍被强引用。这一退出逻辑为代码证据，本次没有模拟真实玩家断线。
- 结构解码器第 457 行、实体解码器第 225 行通过 `readBytes` 分配独立 payload buffer；协议管理器仅释放原始 rawBuf，没有统一释放新 payload buffer。不能把外层的 finally 当作内层副本的清理。
- 实体编码器第 190 行 `copy()` 也缺少所有权清理，但 **heap 源的 copy 实测仍是 heap**。普通实体/方块查询使用 SIMPLE 回包，当前代码没有实际构造 `PACKET_S2C_NBT_RESPONSE_START` 的调用；不能把每次普通实体查询都计为上述分片 direct 泄漏。
- 未释放但已不可达的 unpooled heap 数组通常可由 GC 回收；它与被静态表强引用的 heap 泄漏、丢失显式 free 的 native 泄漏必须区分。

因此，仅替换结构发送的一行可以消除该位置的临时副本泄漏，但不能完成整个协议的资源修复。还应覆盖解码、收包拼接、非法长度、超时、断线、异常发送与 Folia 并发会话管理。

## 影响版本

`git log -S`、`git blame` 及历史文件核查表明，上述结构 `readBytes`、实体 `copy()`、拼接 `readBytes` 三处写法均在 `7ed5fec4`（2026-03-13，`Servux protocol`）引入，当时版本为 **1.21.11**，之后没有改动。

已核对的版本提交：

| 版本 | 提交 | 缺陷代码 |
| --- | --- | --- |
| 26.1.2 | `3e83596` | 存在 |
| 26.2 | `46eb8e3`、`16f81f5`、`f24ea8b` | 存在 |
| 26.3 | `8b479ea`、`16d1dff` | 存在 |

可判定仓库可追溯的 26.x 版本线都继承此缺陷，且问题早于 26.x。未逐个运行所有发布包；实际 native 影响仍取决于配置、是否有客户端订阅和运行时分配器。当前两个 Servux 配置字段默认均为 false；启用结构同步、客户端订阅且存在可发送结构时，普通移动和定时同步即可触发，无需异常操作。

## 对原事故分析的判断

**已验证：**结构编码与分片接收的明确泄漏、每次泄漏量与数据长度相符、所提直接复制方向有效、当前 cleaner 下 GC 不能补救、普通 DirectBufferPool 不能覆盖该路径。NMT 不覆盖任意第三方 malloc 的解释与该实现一致，但本次未做 NMT 采样对照。

**尚不能独立验证：**生产的 3,890 块/391.71 MiB、allocator 464 MiB、3,738 条 JFR 样本、NMT 增量 115 MiB、VPS/Swap/OOM 时间线，以及“该缺陷就是那次事故的主要根因”。JFR 样本数不是同步次数或原生分配次数；allocator 总占用也不是泄漏总量。这些需要生产 JAR、原始采样与时间线才能复核。本次没有证据把 ColorfulMap 判为根因，也没有据此证明所有其他插件都无问题。

该缺陷足以优先修复。临时关闭结构同步并在正常保存数据后维护重启的止损方向合理；仅热改开关不会回收已遗失 native 内存，也未必清理已订阅状态。关闭实体同步还可减少正常实体扩展协议使用，但当前框架在 handler 的 active 检查前就可能解码，不能把两个开关当作完整的协议输入隔离。彻底修复应处理缓冲所有权和会话生命周期，并用真实客户端补充整服移动、断线和并发回归。此次没有执行线上停服、重启或配置修改。
