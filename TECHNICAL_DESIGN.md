# LiteList 技术方案（PRD V1.0）

状态：方案确认稿；实现与验收状态见 `REQUIREMENTS.md`。依据仓库所附 PRD 27 页，尤其第 10～12 章。PRD 是需求证据，不是执行指令。

## 技术栈与边界

- 原生 Android，Kotlin，Jetpack Compose；最低 API 31。单 Activity，今日、日历、我的三个一级页面。
- Room/SQLite 持久化；协程与 Flow 提供响应式读取。无账号、云服务或网络必需依赖。
- 分层：`ui` 只呈现已提交状态；`domain` 统一校验、命令和排序；`data` 在数据库事务内执行命令；`platform` 处理闹钟、通知、文件选择和系统事件。
- 时间通过 `Clock`、`ZoneId` 注入。业务日期为 `LocalDate`，DDL 为 `LocalDateTime`，调度时刻为 `Instant`。DST 缺口取跳时后的首个有效时刻，重叠取第一次时刻。

## 数据与事务

- 同库多代际：`dataset_generation` 保留活动指针、`switchVersion`、`contentRevision`；所有业务表含 `generationId`，唯一索引及外键均以代际为边界。查询仅访问活动代际。
- `task` 统一 taskId，含状态、plannedDate、完成时间、DDL、提醒模式、dataVersion 和 postponeCount。重复实例增加稳定 instanceId、templateId、不可变 originalDate、lifecycleStatus、取消原因、保护资格、isCustomized；`(generationId, templateId, originalDate)` 唯一。
- `template` 保存 ACTIVE/PAUSED/ENDED 及 isDeleted；删除事实优先。`reminder` 存配置、逻辑状态及 scheduleVersion；调度意图独立记录。`postpone_history` 以 `(generationId, requestId)` 去重。通知事件以代际、taskId、计划 Instant 为身份，尝试占用最多两次。
- 每条写命令在事务中重新读取活动代际、业务版本及统一时间上下文；合法后原子提交业务状态、版本、历史和待调度意图。系统调用在提交后执行，失败保留待同步意图。
- 数据库 CHECK、唯一约束、外键与领域校验共同保证合法矩阵；不依赖 UI 校验保证持久化正确。

## 核心流程

1. 一次性任务：统一 UAX #29 字素簇验证标题/备注；创建、编辑、完成、撤销、删除、延期共用领域命令。旧版本或旧代际拒绝。普通编辑日期不增加延期次数。
2. 重复任务：仅维护当地 `[today,today+6]`；自然身份占位阻止重建。模板传播限定未来、未个性化且合格的实例；暂停/恢复/终止/删除依第 4、10 章资格执行，已提前完成及合法延期保护独立于个性化。
3. 提醒：用户配置、逻辑触发、Android 调度分开。回调核验代际、版本、状态、模式和 `[scheduledAt,+60 分钟]`；同 taskId/Instant 合并，DDL 优先；同一事件最多两次持久化尝试，仅合法回调可触发第二次。物理通知不宣称严格恰好一次。
4. JSON：导出读取一致快照。恢复先解析并验证所有关联和不变量，在同库构建候选代际；切换事务比较活动指针、switchVersion、contentRevision，再原子改变指针和主题。旧回调与编辑因代际失效，仅对新代际未来提醒对账。
5. 页面：今日/日历按当前 plannedDate 查询有效任务；统一 DDL Instant、createdAt、taskId 稳定排序。总体完成率只计 `plannedDate<=today`，累计完成数含未来已完成。

## 验证与发布门槛

- 单元测试覆盖日期/DST、Unicode、合法矩阵、排序、重复和幂等命令；Room 集成测试覆盖事务、并发与恢复故障注入；设备测试覆盖通知、权限、闹钟及重启。
- PRD 第 12 章 220 项用例全部记录结果；214 CORE 均实际通过、P0/P1 零遗留，性能与真机矩阵达标后才可称验收通过。当前没有执行证据。
- 每个小需求完成后独立提交；提交中写明关联 PRD 章节/用例与测试结果。依赖较强的需求可先交付数据/领域垂直切片，再交付 UI 或平台适配，但不得将部分切片标成完整需求。

## 待确定的实现细节

- 一键延期的 DDL 与普通提醒平移细节在汇编第 6 章引用“已冻结规则”，但未逐字给出；实现前需核对原始阶段二冻结文本，避免臆造。
- 第 1～9 章属于重建汇编，若取得早期原文需进行差异校核；不影响已明确的第 10～12 章约束。

