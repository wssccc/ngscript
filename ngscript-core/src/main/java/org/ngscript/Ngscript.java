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

package org.ngscript;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

import org.ngscript.compiler.Compiler;
import org.ngscript.compiler.CompilerException;
import org.ngscript.compiler.Instruction;
import org.ngscript.parser.NgParser;
import org.ngscript.parser.ParserException;
import org.ngscript.parser.Token;
import org.ngscript.parser.ast.Program;
import org.ngscript.parser.ast.Stmt;
import org.ngscript.parser.lexer.Lexer;
import org.ngscript.parser.lexer.LexerException;
import org.ngscript.runtime.VirtualMachine;

import lombok.extern.slf4j.Slf4j;

/**
 * @author wssccc
 */
@Slf4j
public class Ngscript {

    Configuration configuration;
    NgParser parser;
    Compiler compiler;
    VirtualMachine vm = new VirtualMachine(new PrintWriter(System.out), new PrintWriter(System.err));

    public Ngscript() {
        this(Configuration.DEFAULT);
    }

    public Ngscript(Configuration configuration) {
        this.configuration = configuration;
        this.compiler = new Compiler(configuration);
        this.parser = new NgParser();
        this.parser.setStatementListener(stmt -> compileAndRun(stmt, ""));
    }

    public Object eval(String code) throws ParserException, CompilerException {
        feed(code);
        return vm.eax;
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
