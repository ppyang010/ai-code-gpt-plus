# GPT Plus 项目规则

最后更新：2026-05-19

## 目的

本文件用于记录当前项目开发过程中的执行规则，确保后续开发、协作和任务推进有统一约定。

## 当前规则

### 1. 必须维护 `task.md`

- 仓库根目录的 [task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/task.md) 是当前项目的总任务清单。
- 后端详细任务维护在 [docs/backend/task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/task.md)。
- 前端详细任务维护在 [docs/frontend/task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/frontend/task.md)。
- 每次开始一项明确开发任务前，都应该先查看对应任务清单当前状态。
- 每次完成一个明确任务后，都应该同步更新对应任务清单。
- 每次发现新的阻塞项、依赖项、风险项时，都应该补充到对应任务清单；跨端阻塞项同步记录到总任务清单。
- 每次调整开发优先级时，都应该更新对应清单中的“当前优先级”或“建议的下一步”。
- 未记录到任务清单的重要任务，不应长期脱离跟踪。
- 新增任务时，应记录任务添加时间。
- 任务完成并打钩时，应记录任务完成时间。
- 已有的历史任务如果当前没有记录时间，保持原样，不要求补录历史时间。
- 后续新增或后续被修改状态的任务，按新规则执行时间记录。

建议格式如下：

- 待办任务：`- [ ] 任务内容（添加时间：2026-05-04）`
- 已完成任务：`- [x] 任务内容（添加时间：2026-05-04，完成时间：2026-05-06）`

### 2. 文档与代码保持同步

- 数据库结构调整后，要同步更新数据库文档。
- 接口设计调整后，要同步更新接口文档。
- 关键架构约定调整后，要同步更新相关规则或设计文档。

### 3. 先走最小可用路径

- 优先完成可联调、可验证、可运行的最小闭环。
- 不提前做过度抽象，不为了“以后可能用到”堆太多复杂设计。
- 当前阶段优先级高于“未来扩展准备”，除非扩展点已经明显阻塞当前实现。

### 4. 任务状态要真实

- 只有真正完成并验证过的任务，才能标记为 `[x]`
- 正在进行中的任务，应继续保留在待办区，必要时补充说明
- 阻塞项要明确写出原因，不要只写“待处理”
- 完成任务时，不仅要打钩，还要补充完成时间
- 如果任务是旧任务且原先没有时间记录，可以维持不变；但从本规则生效后新增的任务，应带时间信息

### 5. API 测试文档要独立维护

- 当前项目的接口联调规则独立维护在 [api-test-rules.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/rules/api-test-rules.md)
- 当前项目的具体接口测试用例维护在 [docs/backend/api-test-cases.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/api-test-cases.md)
- 当前项目的实际接口测试结果维护在 [docs/backend/api-test-results/README.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/api-test-results/README.md)
- 新增重要接口、修改接口行为、补充关键副作用校验后，应同步更新接口测试文档
- 新增一次真实联调、回归或验证后，应同步补对应的结果文件
- 接口测试状态必须真实，只有跑通过的内容才能写进结果记录

### 6. 写代码逻辑时必须补充注释

- 新增或修改核心业务逻辑时，必须补充必要注释，说明代码意图、业务边界、关键分支原因或副作用。
- 注释优先写在方法、复杂分支、异步流程、外部接口调用、数据落库副作用、前端状态流转等阅读成本高的位置。
- 不给显而易见的赋值、简单 getter/setter、纯字段声明添加机械注释，避免注释噪音。
- 当代码逻辑与当前阶段的临时约定有关时，注释要写清楚“为什么现在这样做”，例如 mock fallback、临时用户、前端本地 fallback 等。
- 修改已有逻辑时，如果原注释已经过期，必须同步更新或删除，不能留下和代码行为不一致的说明。

## 当前执行方式

当前项目按以下方式维护任务：

- 主任务清单： [task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/task.md)
- 后端任务清单： [docs/backend/task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/task.md)
- 前端任务清单： [docs/frontend/task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/frontend/task.md)
- 项目规则： [project-rules.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/rules/project-rules.md)
- API 测试规则： [api-test-rules.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/rules/api-test-rules.md)
- 目录结构说明： [project-directory-structure.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/project-directory-structure.md)
- 数据库文档： [mysql-schema.sql](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/db/mysql-schema.sql)
- 接口与后端设计文档：`docs/backend`

## 说明

- 如果后续项目规则增多，可以继续在 `docs/rules` 下拆分更多规则文件。
- 当前版本最重要的一条规则就是：`task.md` 必须持续维护，并作为项目推进的单一任务入口。
