/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript.api;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ngscript.ast.ArrayLiteral;
import org.ngscript.ast.AssignExpr;
import org.ngscript.ast.AwaitExpr;
import org.ngscript.ast.BinaryExpr;
import org.ngscript.ast.BlockStmt;
import org.ngscript.ast.CallExpr;
import org.ngscript.ast.CommaExpr;
import org.ngscript.ast.CondExpr;
import org.ngscript.ast.DestructureExpr;
import org.ngscript.ast.DoWhileStmt;
import org.ngscript.ast.Expr;
import org.ngscript.ast.ExprStmt;
import org.ngscript.ast.ForInStmt;
import org.ngscript.ast.ForOfStmt;
import org.ngscript.ast.ForStmt;
import org.ngscript.ast.FunctionDecl;
import org.ngscript.ast.IfStmt;
import org.ngscript.ast.ImportCallExpr;
import org.ngscript.ast.IndexExpr;
import org.ngscript.ast.Kvp;
import org.ngscript.ast.LabeledStmt;
import org.ngscript.ast.LambdaExpr;
import org.ngscript.ast.LetDeclExpr;
import org.ngscript.ast.MemberExpr;
import org.ngscript.ast.NewExpr;
import org.ngscript.ast.ObjectLiteral;
import org.ngscript.ast.OptionalChainExpr;
import org.ngscript.ast.Program;
import org.ngscript.ast.ReturnStmt;
import org.ngscript.ast.SpreadExpr;
import org.ngscript.ast.Stmt;
import org.ngscript.ast.SuperCallExpr;
import org.ngscript.ast.SwitchCase;
import org.ngscript.ast.SwitchStmt;
import org.ngscript.ast.TaggedTemplateExpr;
import org.ngscript.ast.TemplateExpr;
import org.ngscript.ast.ThrowStmt;
import org.ngscript.ast.TryStmt;
import org.ngscript.ast.UnaryExpr;
import org.ngscript.ast.VarDeclExpr;
import org.ngscript.ast.WhileStmt;
import org.ngscript.ast.WithStmt;
import org.ngscript.ast.YieldExpr;
import org.ngscript.bytecode.Instruction;
import org.ngscript.compiler.Compiler;
import org.ngscript.compiler.CompilerException;
import org.ngscript.lexer.Lexer;
import org.ngscript.lexer.LexerException;
import org.ngscript.parser.NgParser;
import org.ngscript.parser.ParserException;
import org.ngscript.parser.Token;
import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.opcache.OpBinding;

import lombok.extern.slf4j.Slf4j;

/**
 * @author wssccc
 */
@Slf4j
public class NgScriptEngine {

    Configuration configuration;
    NgParser parser;
    Compiler compiler;
    Machine vm;

    public NgScriptEngine() {
        this(Configuration.DEFAULT);
    }

    public NgScriptEngine(Configuration configuration) {
        this(configuration, new PrintWriter(System.out), new PrintWriter(System.err));
    }

    /**
     * Creates an engine writing script output to the given writers (used by
     * the test262 runner to discard per-case logging).
     */
    public NgScriptEngine(Configuration configuration, PrintWriter out, PrintWriter err) {
        this.configuration = configuration;
        this.vm = new Machine(out, err);
        this.compiler = new Compiler(configuration);
        this.parser = new NgParser();
        this.parser.setStatementListener(stmt -> compileAndRun(stmt, ""));
        // the global eval() builtin runs scripts against this engine; a
        // direct eval shares the caller's scope (the env passed by the
        // builtin), an indirect/global eval (null caller) gets a fresh
        // root scope on the baseline environment with ES global eval
        // semantics (sloppy var declarations bind globally and are
        // deletable, strict ones stay in the eval frame)
        Machine.evalHook = (code, callerEnv) -> {
            try {
                Object r = callerEnv == null ? evalInGlobal(code) : evalIn(code, callerEnv);
                // an empty completion (bare block, no expression) is the
                // undefined value, not the Java null used for empty eax
                return r == null ? org.ngscript.value.Undefined.value : r;
            } catch (NgScriptException e) {
                throw e;
            }
        };
    }

    /** Sets the per-execution instruction budget (see Machine.runBudget). */
    public void setRunBudget(long budget) {
        vm.runBudget = budget;
    }

    /** The underlying machine (used by the test runner for state snapshots). */
    public org.ngscript.runtime.Machine getMachine() {
        return vm;
    }

    /**
     * Compiles a script without executing it. The returned {@link Script}
     * can be executed any number of times; each execution is isolated from
     * the engine's environment.
     *
     * @param code the script source
     * @return the compiled script
     * @throws NgScriptException if the script cannot be parsed or compiled
     */
    public Script compile(String code) throws NgScriptException {
        Program program = parser.parse(code);
        List<Instruction> ins = compiler.compileCode(program, code);
        // ES: global script var declarations create non-configurable global
        // bindings (delete of a top-level var is false); collect the names
        // so the execution can pre-create them with the attribute
        java.util.Set<String> topVars = new HashSet<>();
        collectTopLevelVarNames(program.statements, topVars);
        return new CompiledScript(this, ins, code, topVars);
    }

    /** Top-level var declaration names (recursing into blocks, not bodies). */
    private static void collectTopLevelVarNames(List<Stmt> stmts, Set<String> out) {
        if (stmts == null) {
            return;
        }
        for (Stmt s : stmts) {
            if (s instanceof ExprStmt) {
                collectVarNamesInExpr(((ExprStmt) s).expr, out);
            } else if (s instanceof BlockStmt) {
                collectTopLevelVarNames(((BlockStmt) s).block.statements, out);
            } else if (s instanceof IfStmt) {
                IfStmt st = (IfStmt) s;
                collectTopLevelVarNames(singleton(st.thenBody), out);
                collectTopLevelVarNames(singleton(st.elseBody), out);
            } else if (s instanceof ForStmt) {
                collectVarNamesInExpr(((ForStmt) s).init, out);
                collectTopLevelVarNames(singleton(((ForStmt) s).body), out);
            } else if (s instanceof ForInStmt) {
                collectVarNamesInExpr(((ForInStmt) s).target, out);
                collectTopLevelVarNames(singleton(((ForInStmt) s).body), out);
            } else if (s instanceof ForOfStmt) {
                collectVarNamesInExpr(((ForOfStmt) s).target, out);
                collectTopLevelVarNames(singleton(((ForOfStmt) s).body), out);
            } else if (s instanceof WhileStmt) {
                collectTopLevelVarNames(singleton(((WhileStmt) s).body), out);
            } else if (s instanceof DoWhileStmt) {
                collectTopLevelVarNames(singleton(((DoWhileStmt) s).body), out);
            } else if (s instanceof TryStmt) {
                TryStmt st = (TryStmt) s;
                if (st.tryBody != null) {
                    collectTopLevelVarNames(st.tryBody.statements, out);
                }
                if (st.catchBody != null) {
                    collectTopLevelVarNames(st.catchBody.statements, out);
                }
                if (st.finallyBody != null) {
                    collectTopLevelVarNames(st.finallyBody.statements, out);
                }
            } else if (s instanceof SwitchStmt) {
                SwitchStmt st = (SwitchStmt) s;
                if (st.cases != null) {
                    for (SwitchCase c : st.cases) {
                        if (c.body != null) {
                            collectTopLevelVarNames(c.body.statements, out);
                        }
                    }
                }
                if (st.defaultBody != null) {
                    collectTopLevelVarNames(st.defaultBody.statements, out);
                }
            } else if (s instanceof LabeledStmt) {
                collectTopLevelVarNames(singleton(((LabeledStmt) s).statement), out);
            } else if (s instanceof WithStmt) {
                collectTopLevelVarNames(singleton(((WithStmt) s).body), out);
            }
        }
    }

    private static void collectVarNamesInExpr(Expr e, Set<String> out) {
        if (e instanceof VarDeclExpr) {
            VarDeclExpr v = (VarDeclExpr) e;
            if (v.name != null) {
                out.add(v.name);
            }
        } else if (e instanceof AssignExpr) {
            AssignExpr a = (AssignExpr) e;
            collectVarNamesInExpr(a.target, out);
            collectVarNamesInExpr(a.value, out);
        } else if (e instanceof CommaExpr) {
            for (Expr x : ((CommaExpr) e).operands) {
                collectVarNamesInExpr(x, out);
            }
        }
    }

    /**
     * Compiles and executes a script, returning the value of its last
     * expression. In interactive mode this feeds the code line by line.
     *
     * @param code the script source
     * @return the result value (unwrapped), or {@code null}
     * @throws NgScriptException if the script fails
     */
    public Object eval(String code) throws NgScriptException {
        if (configuration.isInteractive()) {
            feed(code);
            return vm.eax.read();
        }
        return compile(code).execute();
    }

    /**
     * Indirect/global eval: runs in a fresh root scope on the baseline
     * environment. ES EvalDeclarationInstantiation: a function declaration
     * colliding with a non-configurable global property is a TypeError
     * before any var binding is created; sloppy var declarations bind on
     * the global object (deletable), strict eval vars stay in the eval
     * frame.
     */
    private Object evalInGlobal(String code) throws NgScriptException {
        if (configuration.isInteractive()) {
            return eval(code);
        }
        Program program = parser.parse(code);
        checkGlobalFunctionDeclarations(program);
        boolean strictCode = parser.strictMode;
        List<Instruction> ins = compiler.compileCode(program, code);
        return runProgram(program, code, ins,
                vm.getInitialEnvironment(), strictCode);
    }

    /** The shared execution core of evalIn/evalInGlobal. */
    private Object runProgram(Program program, String code, List<Instruction> ins,
                              NgsObject parentEnv, boolean frameIsFunctionScope)
            throws NgScriptException {
        OpBinding[] bound = vm.bindInstructions(ins);
        Machine.MachineState state = vm.captureState();
        vm.execNesting++;
        // isolate the exception-state stack like CompiledScript.execute:
        // an exception inside the eval must propagate to the caller's run
        // loop instead of consuming its try contexts (see CompiledScript)
        org.ngscript.util.FastStack<org.ngscript.runtime.Context> savedMss = vm.isolateMss();
        try {
            vm.installInstructions(bound);
            NgsObject frame = new NgsObject(parentEnv);
            // a strict eval frame keeps its var declarations (ES binds
            // strict eval vars in the eval's declarative environment);
            // sloppy eval vars hoist into the caller's function/global scope
            frame.functionScope = frameIsFunctionScope;
            vm.env.write(frame);
            vm.resetForExecution();
            vm.run();
            return vm.eax.read();
        } finally {
            vm.restoreMss(savedMss);
            vm.execNesting--;
            vm.restoreState(state);
        }
    }

    /**
     * Direct-eval execution: runs the code in a fresh declarative
     * environment whose outer is the caller's environment, so the eval
     * code can read (and with {@code var}, declare into) the caller's
     * scope. {@code let}/{@code const} bindings stay in the fresh frame
     * and are discarded when the eval completes.
     *
     * @param code the eval source
     * @param callerEnv the caller's current environment
     * @return the eval completion value
     * @throws NgScriptException if the script fails
     */
    public Object evalIn(String code, NgsObject callerEnv) throws NgScriptException {
        if (configuration.isInteractive()) {
            return eval(code);
        }
        Program program = parser.parseEval(code);
        // ES EvalDeclarationInstantiation: a non-strict direct eval that
        // declares "arguments" (via var or a function declaration) throws a
        // SyntaxError when the caller's environment chain carries an
        // "arguments" binding outside the global scope: the caller is inside
        // a function invocation, so its arguments object, an "arguments"
        // parameter, or a var/lexical/function declaration named "arguments"
        // in the enclosing scopes makes the declaration illegal. In global
        // (or arrow-parameter) contexts the eval creates a global var
        // binding instead.
        if (declaresArguments(program) && callerChainHasArguments(callerEnv)) {
            throw new ParserException(
                    "SyntaxError: Identifier 'arguments' has already been declared");
        }
        // ES: a direct eval containing super(...) is always a SyntaxError
        // (the eval code is not a constructor method); super.x is only legal
        // inside a method context — a class frame on the caller's chain
        // carries %superproto (a class field initializer, a method body)
        if (containsSuperCall(program)
                || (containsSuperProperty(program) && !callerChainHasSuperProto(callerEnv))) {
            throw new ParserException("invalid use of super");
        }
        // a global-scope direct eval declares global functions: a name
        // colliding with a non-configurable global property is a TypeError
        if (!callerChainHasFunctionScope(callerEnv)) {
            checkGlobalFunctionDeclarations(program);
        }
        List<Instruction> ins = compiler.compileCode(program, code);
        // sloppy direct eval vars hoist into the caller's scope
        return runProgram(program, code, ins, callerEnv, false);
    }

    /**
     * Line number of the first line of the next interactive input batch;
     * lets ASI detect line breaks across REPL lines.
     */
    private int fedLineStart = 1;

    public boolean feed(String code) throws CompilerException, LexerException, ParserException {
        if (configuration.isInteractive()) {
            List<Token> tokens = Lexer.scan(code, fedLineStart);
            fedLineStart += (int) code.chars().filter(c -> c == '\n').count() + (code.isEmpty() ? 0 : 1);
            return parser.feedInteractive(tokens);
        }
        Program program = parser.parse(code);
        compileAndRun(program, code);
        return true;
    }

    private void compileAndRun(Stmt stmt, String sourceCode) throws CompilerException {
        Program program = new Program(Collections.singletonList(stmt), stmt.line);
        compileAndRun(program, sourceCode);
    }

    private void compileAndRun(Program program, String sourceCode) throws CompilerException {
        if (configuration.isGenerateDebugInfo()) {
            log.info(program.toString());
        }
        List<Instruction> ins = compiler.compileCode(program, sourceCode);
        if (configuration.isGenerateDebugInfo()) {
            log.info(ins.toString());
        }
        vm.loadInstructions(ins);
        vm.run();
    }

    /**
     * Whether the eval source declares {@code arguments} via var or a
     * function declaration (ES VarDeclaredNames, recursing into nested
     * function bodies).
     */
    private static boolean declaresArguments(Program program) {
        Set<String> names = new HashSet<>();
        collectVarDeclaredNames(program.statements, names);
        return names.contains("arguments");
    }

    /**
     * Whether any environment frame between the caller and the global scope
     * (exclusive) carries an "arguments" binding: a function frame binds the
     * arguments object, and a parameter named "arguments" or a var/lexical/
     * function declaration in an enclosing scope also binds the name.
     */
    private static boolean callerChainHasArguments(NgsObject env) {
        NgsObject f = env;
        while (f != null && f.getParent() != null) {
            if (f.has("arguments")) {
                return true;
            }
            f = f.getParent();
        }
        return false;
    }

    /** Whether the caller is inside a method context (a class frame carries %superproto). */
    private static boolean callerChainHasSuperProto(NgsObject env) {
        NgsObject f = env;
        while (f != null && f.getParent() != null) {
            if (f.has("%superproto")) {
                return true;
            }
            f = f.getParent();
        }
        return false;
    }

    /** Whether any frame on the caller's chain is a function scope. */
    private static boolean callerChainHasFunctionScope(NgsObject env) {
        NgsObject f = env;
        while (f != null && f.getParent() != null) {
            if (f.functionScope) {
                return true;
            }
            f = f.getParent();
        }
        return false;
    }

    /**
     * ES EvalDeclarationInstantiation: a global function declaration whose
     * name collides with a non-configurable global property (NaN,
     * Infinity, undefined, or a defineProperty configurable:false binding)
     * throws a TypeError before any var binding is created.
     */
    private static void checkGlobalFunctionDeclarations(Program program) {
        Set<String> names = new HashSet<>();
        collectFunctionDeclNames(program.statements, names);
        for (String name : names) {
            NgsObject g = Machine.globalThis;
            if (g.has(name)
                    && (g.isNonConfigurable(name)
                            || "NaN".equals(name) || "Infinity".equals(name)
                            || "undefined".equals(name))) {
                throw new IllegalArgumentException(
                        "Cannot declare global function '" + name + "'");
            }
        }
    }

    /** Collects function-declaration names in the statement tree (not into bodies). */
    private static void collectFunctionDeclNames(List<Stmt> stmts, Set<String> out) {
        if (stmts == null) {
            return;
        }
        for (Stmt s : stmts) {
            if (s instanceof FunctionDecl) {
                FunctionDecl f = (FunctionDecl) s;
                if (f.name != null) {
                    out.add(f.name);
                }
            } else if (s instanceof BlockStmt) {
                collectFunctionDeclNames(((BlockStmt) s).block.statements, out);
            } else if (s instanceof IfStmt) {
                IfStmt st = (IfStmt) s;
                collectFunctionDeclNames(singleton(st.thenBody), out);
                collectFunctionDeclNames(singleton(st.elseBody), out);
            } else if (s instanceof ForStmt) {
                collectFunctionDeclNames(singleton(((ForStmt) s).body), out);
            } else if (s instanceof ForInStmt) {
                collectFunctionDeclNames(singleton(((ForInStmt) s).body), out);
            } else if (s instanceof ForOfStmt) {
                collectFunctionDeclNames(singleton(((ForOfStmt) s).body), out);
            } else if (s instanceof WhileStmt) {
                collectFunctionDeclNames(singleton(((WhileStmt) s).body), out);
            } else if (s instanceof DoWhileStmt) {
                collectFunctionDeclNames(singleton(((DoWhileStmt) s).body), out);
            } else if (s instanceof TryStmt) {
                TryStmt st = (TryStmt) s;
                if (st.tryBody != null) {
                    collectFunctionDeclNames(st.tryBody.statements, out);
                }
                if (st.catchBody != null) {
                    collectFunctionDeclNames(st.catchBody.statements, out);
                }
                if (st.finallyBody != null) {
                    collectFunctionDeclNames(st.finallyBody.statements, out);
                }
            } else if (s instanceof SwitchStmt) {
                SwitchStmt st = (SwitchStmt) s;
                if (st.cases != null) {
                    for (SwitchCase c : st.cases) {
                        if (c.body != null) {
                            collectFunctionDeclNames(c.body.statements, out);
                        }
                    }
                }
                if (st.defaultBody != null) {
                    collectFunctionDeclNames(st.defaultBody.statements, out);
                }
            } else if (s instanceof LabeledStmt) {
                collectFunctionDeclNames(singleton(((LabeledStmt) s).statement), out);
            } else if (s instanceof WithStmt) {
                collectFunctionDeclNames(singleton(((WithStmt) s).body), out);
            }
        }
    }

    private static List<Stmt> singleton(Stmt s) {
        return s == null ? Collections.<Stmt>emptyList() : Collections.singletonList(s);
    }

    private static boolean containsSuperCall(Program program) {
        for (Stmt s : program.statements) {
            if (stmtContainsSuper(s, true)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSuperProperty(Program program) {
        for (Stmt s : program.statements) {
            if (stmtContainsSuper(s, false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean stmtContainsSuper(Stmt s, boolean wantCall) {
        if (s instanceof BlockStmt) {
            for (Stmt c : ((BlockStmt) s).block.statements) {
                if (stmtContainsSuper(c, wantCall)) {
                    return true;
                }
            }
        } else if (s instanceof IfStmt) {
            IfStmt st = (IfStmt) s;
            return stmtContainsSuper(st.thenBody, wantCall) || stmtContainsSuper(st.elseBody, wantCall);
        } else if (s instanceof ForStmt) {
            ForStmt st = (ForStmt) s;
            return exprContainsSuper(st.init, wantCall) || exprContainsSuper(st.test, wantCall)
                    || exprContainsSuper(st.step, wantCall) || stmtContainsSuper(st.body, wantCall);
        } else if (s instanceof ForInStmt) {
            ForInStmt st = (ForInStmt) s;
            return exprContainsSuper(st.target, wantCall) || exprContainsSuper(st.object, wantCall)
                    || stmtContainsSuper(st.body, wantCall);
        } else if (s instanceof ForOfStmt) {
            ForOfStmt st = (ForOfStmt) s;
            return exprContainsSuper(st.target, wantCall) || exprContainsSuper(st.object, wantCall)
                    || stmtContainsSuper(st.body, wantCall);
        } else if (s instanceof WhileStmt) {
            WhileStmt st = (WhileStmt) s;
            return exprContainsSuper(st.condition, wantCall) || stmtContainsSuper(st.body, wantCall);
        } else if (s instanceof DoWhileStmt) {
            DoWhileStmt st = (DoWhileStmt) s;
            return stmtContainsSuper(st.body, wantCall) || exprContainsSuper(st.condition, wantCall);
        } else if (s instanceof TryStmt) {
            TryStmt st = (TryStmt) s;
            return (st.tryBody != null && stmtContainsSuper(new BlockStmt(st.tryBody, st.line), wantCall))
                    || (st.catchBody != null && stmtContainsSuper(new BlockStmt(st.catchBody, st.line), wantCall))
                    || (st.finallyBody != null && stmtContainsSuper(new BlockStmt(st.finallyBody, st.line), wantCall));
        } else if (s instanceof SwitchStmt) {
            SwitchStmt st = (SwitchStmt) s;
            if (exprContainsSuper(st.value, wantCall)) {
                return true;
            }
            if (st.cases != null) {
                for (SwitchCase c : st.cases) {
                    if ((c.value != null && exprContainsSuper(c.value, wantCall))
                            || (c.body != null && stmtContainsSuper(new BlockStmt(c.body, st.line), wantCall))) {
                        return true;
                    }
                }
            }
            return st.defaultBody != null
                    && stmtContainsSuper(new BlockStmt(st.defaultBody, st.line), wantCall);
        } else if (s instanceof LabeledStmt) {
            return stmtContainsSuper(((LabeledStmt) s).statement, wantCall);
        } else if (s instanceof WithStmt) {
            WithStmt st = (WithStmt) s;
            return exprContainsSuper(st.object, wantCall) || stmtContainsSuper(st.body, wantCall);
        } else if (s instanceof FunctionDecl) {
            // nested plain functions reset the super context at parse time,
            // so nothing to scan here
            return false;
        } else if (s instanceof ExprStmt) {
            return exprContainsSuper(((ExprStmt) s).expr, wantCall);
        } else if (s instanceof ReturnStmt) {
            return exprContainsSuper(((ReturnStmt) s).expr, wantCall);
        } else if (s instanceof ThrowStmt) {
            return exprContainsSuper(((ThrowStmt) s).expr, wantCall);
        }
        return false;
    }

    private static boolean exprContainsSuper(Expr e, boolean wantCall) {
        if (e == null) {
            return false;
        }
        if (e instanceof org.ngscript.ast.SuperCallExpr) {
            return wantCall;
        }
        if (e instanceof org.ngscript.ast.SuperMemberExpr) {
            return !wantCall;
        }
        if (e instanceof AssignExpr) {
            AssignExpr a = (AssignExpr) e;
            return exprContainsSuper(a.target, wantCall) || exprContainsSuper(a.value, wantCall);
        } else if (e instanceof LambdaExpr) {
            // arrows inherit the super context: scan their bodies too
            LambdaExpr l = (LambdaExpr) e;
            if (l.body != null) {
                for (Stmt c : l.body.statements) {
                    if (stmtContainsSuper(c, wantCall)) {
                        return true;
                    }
                }
            }
            return exprContainsSuper(l.bodyExpr, wantCall);
        } else if (e instanceof CallExpr) {
            CallExpr c = (CallExpr) e;
            if (exprContainsSuper(c.callee, wantCall)) {
                return true;
            }
            for (Expr a : c.args) {
                if (exprContainsSuper(a, wantCall)) {
                    return true;
                }
            }
            return false;
        } else if (e instanceof NewExpr) {
            NewExpr n = (NewExpr) e;
            if (exprContainsSuper(n.callee, wantCall)) {
                return true;
            }
            for (Expr a : n.args) {
                if (exprContainsSuper(a, wantCall)) {
                    return true;
                }
            }
            return false;
        } else if (e instanceof CommaExpr) {
            for (Expr x : ((CommaExpr) e).operands) {
                if (exprContainsSuper(x, wantCall)) {
                    return true;
                }
            }
            return false;
        } else if (e instanceof CondExpr) {
            CondExpr c = (CondExpr) e;
            return exprContainsSuper(c.condition, wantCall) || exprContainsSuper(c.thenExpr, wantCall)
                    || exprContainsSuper(c.elseExpr, wantCall);
        } else if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            return exprContainsSuper(b.left, wantCall) || exprContainsSuper(b.right, wantCall);
        } else if (e instanceof UnaryExpr) {
            return exprContainsSuper(((UnaryExpr) e).operand, wantCall);
        } else if (e instanceof MemberExpr) {
            return exprContainsSuper(((MemberExpr) e).object, wantCall);
        } else if (e instanceof IndexExpr) {
            IndexExpr x = (IndexExpr) e;
            return exprContainsSuper(x.object, wantCall) || exprContainsSuper(x.index, wantCall);
        } else if (e instanceof ObjectLiteral) {
            for (Object item : ((ObjectLiteral) e).items) {
                if (item instanceof Kvp) {
                    Kvp k = (Kvp) item;
                    if (exprContainsSuper(k.keyExpr, wantCall) || exprContainsSuper(k.value, wantCall)) {
                        return true;
                    }
                } else if (item instanceof SpreadExpr
                        && exprContainsSuper(((SpreadExpr) item).expr, wantCall)) {
                    return true;
                }
            }
            return false;
        } else if (e instanceof ArrayLiteral) {
            for (Expr x : ((ArrayLiteral) e).items) {
                if (exprContainsSuper(x, wantCall)) {
                    return true;
                }
            }
            return false;
        } else if (e instanceof SpreadExpr) {
            return exprContainsSuper(((SpreadExpr) e).expr, wantCall);
        } else if (e instanceof TemplateExpr) {
            for (Expr x : ((TemplateExpr) e).exprs) {
                if (exprContainsSuper(x, wantCall)) {
                    return true;
                }
            }
            return false;
        } else if (e instanceof TaggedTemplateExpr) {
            TaggedTemplateExpr t = (TaggedTemplateExpr) e;
            return exprContainsSuper(t.tag, wantCall) || exprContainsSuper(t.template, wantCall);
        } else if (e instanceof AwaitExpr) {
            return exprContainsSuper(((AwaitExpr) e).operand, wantCall);
        } else if (e instanceof YieldExpr) {
            return exprContainsSuper(((YieldExpr) e).operand, wantCall);
        } else if (e instanceof DestructureExpr) {
            return false;
        }
        return false;
    }

    private static void collectVarDeclaredNames(List<Stmt> stmts, Set<String> out) {
        if (stmts == null) {
            return;
        }
        for (Stmt s : stmts) {
            collectVarDeclaredNames(s, out);
        }
    }

    private static void collectVarDeclaredNames(Stmt s, Set<String> out) {
        if (s instanceof BlockStmt) {
            collectVarDeclaredNames(((BlockStmt) s).block.statements, out);
        } else if (s instanceof IfStmt) {
            IfStmt st = (IfStmt) s;
            collectVarDeclaredNames(st.thenBody, out);
            collectVarDeclaredNames(st.elseBody, out);
        } else if (s instanceof ForStmt) {
            ForStmt st = (ForStmt) s;
            collectExprVarDeclaredNames(st.init, out);
            collectVarDeclaredNames(st.body, out);
        } else if (s instanceof ForInStmt) {
            ForInStmt st = (ForInStmt) s;
            collectExprVarDeclaredNames(st.target, out);
            collectVarDeclaredNames(st.body, out);
        } else if (s instanceof ForOfStmt) {
            ForOfStmt st = (ForOfStmt) s;
            collectExprVarDeclaredNames(st.target, out);
            collectVarDeclaredNames(st.body, out);
        } else if (s instanceof WhileStmt) {
            collectVarDeclaredNames(((WhileStmt) s).body, out);
        } else if (s instanceof DoWhileStmt) {
            collectVarDeclaredNames(((DoWhileStmt) s).body, out);
        } else if (s instanceof TryStmt) {
            TryStmt st = (TryStmt) s;
            collectVarDeclaredNames(st.tryBody == null ? null : st.tryBody.statements, out);
            collectVarDeclaredNames(st.catchBody == null ? null : st.catchBody.statements, out);
            collectVarDeclaredNames(st.finallyBody == null ? null : st.finallyBody.statements, out);
        } else if (s instanceof SwitchStmt) {
            SwitchStmt st = (SwitchStmt) s;
            if (st.cases != null) {
                for (SwitchCase c : st.cases) {
                    collectVarDeclaredNames(c.body == null ? null : c.body.statements, out);
                }
            }
            collectVarDeclaredNames(st.defaultBody == null ? null : st.defaultBody.statements, out);
        } else if (s instanceof LabeledStmt) {
            collectVarDeclaredNames(((LabeledStmt) s).statement, out);
        } else if (s instanceof WithStmt) {
            collectVarDeclaredNames(((WithStmt) s).body, out);
        } else if (s instanceof FunctionDecl) {
            FunctionDecl f = (FunctionDecl) s;
            if (f.name != null) {
                out.add(f.name);
            }
            // nested function bodies contribute their own var declarations
            collectVarDeclaredNames(f.body == null ? null : f.body.statements, out);
        } else if (s instanceof ExprStmt) {
            collectExprVarDeclaredNames(((ExprStmt) s).expr, out);
        } else if (s instanceof ReturnStmt) {
            collectExprVarDeclaredNames(((ReturnStmt) s).expr, out);
        } else if (s instanceof ThrowStmt) {
            collectExprVarDeclaredNames(((ThrowStmt) s).expr, out);
        }
    }

    private static void collectExprVarDeclaredNames(Expr e, Set<String> out) {
        if (e == null) {
            return;
        }
        if (e instanceof VarDeclExpr) {
            collectVarDeclExpr((VarDeclExpr) e, out);
        } else if (e instanceof AssignExpr) {
            AssignExpr a = (AssignExpr) e;
            collectExprVarDeclaredNames(a.target, out);
            collectExprVarDeclaredNames(a.value, out);
        } else if (e instanceof LambdaExpr) {
            // function expressions and arrows: their bodies' var
            // declarations count (ES VarDeclaredNames recurses into them)
            LambdaExpr l = (LambdaExpr) e;
            collectVarDeclaredNames(l.body == null ? null : l.body.statements, out);
            collectExprVarDeclaredNames(l.bodyExpr, out);
        } else if (e instanceof LetDeclExpr) {
            // let/const bindings produce no var names, but their
            // initializers may contain function expressions
            LetDeclExpr l = (LetDeclExpr) e;
            if (l.inits != null) {
                for (Expr init : l.inits) {
                    collectExprVarDeclaredNames(init, out);
                }
            }
        } else if (e instanceof CallExpr) {
            CallExpr c = (CallExpr) e;
            collectExprVarDeclaredNames(c.callee, out);
            for (Expr a : c.args) {
                collectExprVarDeclaredNames(a, out);
            }
        } else if (e instanceof NewExpr) {
            NewExpr n = (NewExpr) e;
            collectExprVarDeclaredNames(n.callee, out);
            for (Expr a : n.args) {
                collectExprVarDeclaredNames(a, out);
            }
        } else if (e instanceof ImportCallExpr) {
            for (Expr a : ((ImportCallExpr) e).args) {
                collectExprVarDeclaredNames(a, out);
            }
        } else if (e instanceof SuperCallExpr) {
            for (Expr a : ((SuperCallExpr) e).args) {
                collectExprVarDeclaredNames(a, out);
            }
        } else if (e instanceof CommaExpr) {
            for (Expr x : ((CommaExpr) e).operands) {
                collectExprVarDeclaredNames(x, out);
            }
        } else if (e instanceof CondExpr) {
            CondExpr c = (CondExpr) e;
            collectExprVarDeclaredNames(c.condition, out);
            collectExprVarDeclaredNames(c.thenExpr, out);
            collectExprVarDeclaredNames(c.elseExpr, out);
        } else if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            collectExprVarDeclaredNames(b.left, out);
            collectExprVarDeclaredNames(b.right, out);
        } else if (e instanceof UnaryExpr) {
            collectExprVarDeclaredNames(((UnaryExpr) e).operand, out);
        } else if (e instanceof AwaitExpr) {
            collectExprVarDeclaredNames(((AwaitExpr) e).operand, out);
        } else if (e instanceof YieldExpr) {
            collectExprVarDeclaredNames(((YieldExpr) e).operand, out);
        } else if (e instanceof MemberExpr) {
            collectExprVarDeclaredNames(((MemberExpr) e).object, out);
        } else if (e instanceof IndexExpr) {
            IndexExpr x = (IndexExpr) e;
            collectExprVarDeclaredNames(x.object, out);
            collectExprVarDeclaredNames(x.index, out);
        } else if (e instanceof ObjectLiteral) {
            for (Object item : ((ObjectLiteral) e).items) {
                if (item instanceof Kvp) {
                    Kvp k = (Kvp) item;
                    collectExprVarDeclaredNames(k.keyExpr, out);
                    collectExprVarDeclaredNames(k.value, out);
                } else if (item instanceof SpreadExpr) {
                    collectExprVarDeclaredNames(((SpreadExpr) item).expr, out);
                }
            }
        } else if (e instanceof ArrayLiteral) {
            for (Expr x : ((ArrayLiteral) e).items) {
                collectExprVarDeclaredNames(x, out);
            }
        } else if (e instanceof SpreadExpr) {
            collectExprVarDeclaredNames(((SpreadExpr) e).expr, out);
        } else if (e instanceof TemplateExpr) {
            for (Expr x : ((TemplateExpr) e).exprs) {
                collectExprVarDeclaredNames(x, out);
            }
        } else if (e instanceof TaggedTemplateExpr) {
            TaggedTemplateExpr t = (TaggedTemplateExpr) e;
            collectExprVarDeclaredNames(t.tag, out);
            collectExprVarDeclaredNames(t.template, out);
        } else if (e instanceof OptionalChainExpr) {
            OptionalChainExpr o = (OptionalChainExpr) e;
            collectExprVarDeclaredNames(o.object, out);
            if (o instanceof OptionalChainExpr.Index) {
                collectExprVarDeclaredNames(((OptionalChainExpr.Index) o).index, out);
            } else if (o instanceof OptionalChainExpr.Call) {
                for (Expr a : ((OptionalChainExpr.Call) o).args) {
                    collectExprVarDeclaredNames(a, out);
                }
            }
        } else if (e instanceof DestructureExpr) {
            // the assignment target pattern binds names, no function bodies
        }
    }

    private static void collectVarDeclExpr(VarDeclExpr v, Set<String> out) {
        if (v.name != null) {
            out.add(v.name);
        }
    }

}