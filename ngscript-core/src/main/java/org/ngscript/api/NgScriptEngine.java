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
        // root scope on the baseline environment
        Machine.evalHook = (code, callerEnv) -> {
            try {
                Object r = callerEnv == null ? eval(code) : evalIn(code, callerEnv);
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
        return new CompiledScript(this, ins, code);
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
        Program program = parser.parse(code);
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
        List<Instruction> ins = compiler.compileCode(program, code);
        OpBinding[] bound = vm.bindInstructions(ins);
        Machine.MachineState state = vm.captureState();
        vm.execNesting++;
        // isolate the exception-state stack like CompiledScript.execute:
        // an exception inside the eval must propagate to the caller's run
        // loop instead of consuming its try contexts (see CompiledScript)
        org.ngscript.util.FastStack<org.ngscript.runtime.Context> savedMss = vm.isolateMss();
        try {
            vm.installInstructions(bound);
            vm.env.write(new NgsObject(callerEnv));
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