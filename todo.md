# ngscript TODO

## API 演进（决策 R16：本次包结构重构不实现，留待后续迭代）

- [ ] **compile/execute 分离**：`NgScriptEngine.compile(code)` 返回可复用的编译后脚本对象，`execute` 多次执行，避免每次 eval 重复 parse+compile
- [ ] **Source 对象**：脚本源封装（文件名/行号映射），错误诊断携带源码位置
- [ ] **可复用脚本对象**：`Script`/`CompiledScript` 概念，支持预编译脚本的重复调用与并发隔离
- [ ] 异常类型体系：`NgScriptException` 基类 + `ParserException`/`CompilerException`/`VmRuntimeException` 统一继承（当前 `VmRuntimeException` 名称仍带 Vm 前缀，与 R11 去前缀目标不一致）

## 独立提案（审阅决策：不默认执行，前置条件满足后单独立项）

- [ ] **(a) OpImpl 按域拆分 + opcache 改造**：68 操作码拆 `OpArithmetic`/`OpObject`/`OpArray`/`OpControlFlow`/`OpCall`/`OpMisc`。前置：opcode→宿主类映射表；`_pe` 变体按宿主类拼接；`getMethods()` 多类扫描防重复注册；跨包 `importPackage` 处理；先为每个操作码补单测
- [ ] **(b) Environment 拆分（Scope + NgsObject + NgsArray）**：前置条件必须书面讲清——`new_op`"活动作用域=实例"不变量保真方案（`new Foo()` 返回的实例就是构造函数活动帧）；`Coroutine` 的 `new Environment(closure.environment)` 复制构造与 resume/yield/status 注入迁移；`Builtins.CreateEnv` 路径；`data` 包级私有可见性方案；typeof 行为变更清单（`typeof(new Foo())` 将从 `org.ngscript.runtime.Environment` 变为 `NgsObject`）
- [ ] **(c) 真解环（依赖倒置）**：`Machine` 定义操作码 SPI / 值类型接口，让 value/interop 依赖 runtime 抽象而非具体类。仅当出现明确收益动机时立项
- [ ] **去 Vm 前缀（R11 可选项）**：`VirtualMachine`→`Machine`、`VmMemRef`→`MemRef`、`VmInvokable`→`Invokable`、`VmRuntimeException`→`NgScriptException`、`FunctionDef`→`ScriptFunction`。注意 `OpMap` javassist 源码串硬编码 `OpImpl.` 前缀与 `VirtualMachine` 裸名（依赖 `importPackage` 解析），改名必须同步字符串

## 已知缺陷（探索/测试发现，未修复）

- [ ] **generateDebugInfo 栈溢出**：`Configuration.generateDebugInfo=true` 时 `Program.toString()`（`AstNode.childrenToString` 无限递归）触发 StackOverflowError，调试信息功能不可用
- [ ] **`Math.max(3,7)` 重载解析选中 double 版**返回 `7.0`（`== 7` 因 testEq 走 toString 比较为假）；Integer 参数未优先匹配 int 重载
- [ ] **typeof 包名启发式脆弱**：`typeof` 对与 `Undefined` 同包的值输出 simpleName、异包输出全限定名（`OpImpl.typeof`），依赖包布局而非类型系统

## 杂项

- [ ] parseroid 模块未加入根 pom `<modules>`，CI 从不构建/测试它
- [ ] examples 模块 deploy 已 skip（有意为之）
