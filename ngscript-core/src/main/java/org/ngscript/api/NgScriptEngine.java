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
import java.util.List;

import org.ngscript.ast.Program;
import org.ngscript.ast.Stmt;
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
        List<Instruction> ins = compiler.compileCode(program, code);
        OpBinding[] bound = vm.bindInstructions(ins);
        Machine.MachineState state = vm.captureState();
        vm.execNesting++;
        try {
            vm.installInstructions(bound);
            vm.env.write(new NgsObject(callerEnv));
            vm.resetForExecution();
            vm.run();
            return vm.eax.read();
        } finally {
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

}
