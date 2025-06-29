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

import org.ngscript.compiler.Compiler;
import org.ngscript.compiler.CompilerException;
import org.ngscript.bytecode.Instruction;
import org.ngscript.parser.NgParser;
import org.ngscript.parser.ParserException;
import org.ngscript.parser.Token;
import org.ngscript.ast.Program;
import org.ngscript.ast.Stmt;
import org.ngscript.lexer.Lexer;
import org.ngscript.lexer.LexerException;
import org.ngscript.runtime.Machine;

import lombok.extern.slf4j.Slf4j;

/**
 * @author wssccc
 */
@Slf4j
public class NgScriptEngine {

    Configuration configuration;
    NgParser parser;
    Compiler compiler;
    Machine vm = new Machine(new PrintWriter(System.out), new PrintWriter(System.err));

    public NgScriptEngine() {
        this(Configuration.DEFAULT);
    }

    public NgScriptEngine(Configuration configuration) {
        this.configuration = configuration;
        this.compiler = new Compiler(configuration);
        this.parser = new NgParser();
        this.parser.setStatementListener(stmt -> compileAndRun(stmt, ""));
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
        List<Token> tokens = Lexer.scan(code);
        Program program = parser.parse(tokens);
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

    public boolean feed(String code) throws CompilerException, LexerException, ParserException {
        List<Token> tokens = Lexer.scan(code);
        if (configuration.isInteractive()) {
            return parser.feedInteractive(tokens);
        }
        Program program = parser.parse(tokens);
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
