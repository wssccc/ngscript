# ngscript TODO

## API 演进（决策 R16 ✅ 已完成）

- [x] **compile/execute 分离**：`NgScriptEngine.compile(code)` 返回可复用的 `Script` 对象（`Script` 接口 + `CompiledScript` 实现），`execute` 多次执行，避免每次 eval 重复 parse+compile
- [x] **Source 对象**：`org.ngscript.api.Source`（文件名/行号），`NgScriptException`/`ParserException`/`CompilerException`/`NgScriptRuntimeException` 统一携带 `getSource()`/`getLine()`
- [x] **可复用脚本对象**：`Script`/`CompiledScript`，快照/恢复式隔离执行（每次 execute 在基线环境之上新建根作用域，跑完恢复调用方 Machine 状态）；`Script` 非线程安全，多线程需多 engine
- [x] **异常类型体系**：`NgScriptException extends RuntimeException` 基类 + `ParserException`/`LexerException`/`CompilerException`/`NgScriptRuntimeException` 统一继承（原 `VmRuntimeException` 已改名，R11 去前缀目标达成）

## 独立提案

- [x] **(a) OpImpl 拆分 + opcache 改造** ✅ 已完成：67 操作码拆为 9 域类（`OpArithmetic`/`OpObject`/`OpArray`/`OpControlFlow`/`OpCall`/`OpMove`/`OpStack`/`OpConstant`/`OpScope`）；`OpMap` 宿主类列表 + fail-fast 重名检测；javassist 生成串全限定名；逐 opcode 指令级单测（83 例）+ `OpMapStructureTest` 结构测试
- [x] **(b) Environment 拆分（Scope + NgsObject + NgsArray）** ✅ 已完成：`Environment` → `NgsObject`（私有 data/parent + 公开访问器 get/set/define/getRef/keys/parent/registerBuiltins）；`NgsArray extends NgsObject`（数组字面量）；`Scope` 标记接口；**new_op 不变量保真方案**：函数/构造帧就是 NgsObject，活动帧=实例（构造器内 `this` 与闭包捕获一致）；Coroutine/CreateEnv/Machine/Context/ScriptFunction 全部迁移；typeof 变更清单：`typeof(new Foo())` → `NgsObject`、数组 → `NgsArray`
- [ ] **(c) 真解环（依赖倒置）**：**deferred**（Machine 定义操作码 SPI / 值类型接口，让 value/interop 依赖 runtime 抽象而非具体类）。触发条件：出现明确收益动机（如外部宿主接入需求）时立项
- [x] **去 Vm 前缀（R11）** ✅ 已完成：`VirtualMachine`→`Machine`、`VmMemRef`→`MemRef`、`VmInvokable`→`Invokable`、`VmRuntimeException`→`NgScriptRuntimeException`、`FunctionDef`→`ScriptFunction`；OpMap javassist 生成串已全限定名化（与拆分衔接）；`MemRef.toString` 文案同步

## 已知缺陷

- [x] **generateDebugInfo 栈溢出** ✅ 已修复：根因是 `childrenToString` 首行虚调用 `toString(int)`，而 `Program`/`Block` 等 30 个节点覆写它去调 `childrenToString` → 自递归；提取非虚 `nodeToString` 帮助方法；新增 `GenerateDebugInfoTest`（batch + interactive 双模式回归）
- [x] **Math.max(3,7) 重载解析** ⚠️ 部分修复：缓存键已加固（显式含声明类，跨类同名方法不再共享条目——测试实证 `StrictMath.max` 曾被解析成 `Math.max`）；**first-match-wins 匹配优先级保留并文档化**（`Math.max(3,7)` 可能仍返回 7.0，example16 断言 `== 7.0` 不变）；testEq 的 toString 比较（`7.0==7` 为 false）按决策保留
- [x] **typeof 包名启发式脆弱** ✅ 已修复：改为显式类型注册表（`Undefined`/`ScriptFunction`/`MemRef`/`Invokable`/`NgsObject`/`NgsArray` → simpleName，外来 Java 类 → 全限定名），去掉包布局依赖；`TypeOfTest` 覆盖

## 杂项

- [x] parseroid 模块已加入根 pom `<modules>`，CI 一并构建测试（与 README 模块表一致）
- [x] examples 模块 deploy 已 skip（有意为之，保持）

## 后续 deferred 清单

- 提案 (c) 真解环（触发条件：明确收益动机）
- first-match-wins 精度匹配（best-match 评分，待有真实误匹配案例）
- testEq 数值比较（`7.0 == 7` 目前为 false，JS 语义为 true）
- Source 列号支持（当前仅行号）
- Script 线程安全 / 多 VM 并发（文档已注明单线程限制）
- test262 接入 CI（当前为手动参考套件）
