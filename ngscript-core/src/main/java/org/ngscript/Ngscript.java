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

import org.ngscript.compiler.Compiler;
import org.ngscript.compiler.CompilerException;
import org.ngscript.compiler.Instruction;
import org.ngscript.parser.lexer.Lexer;
import org.ngscript.parser.lexer.LexerException;
import org.ngscript.parser.NgLalrParser;
import org.ngscript.parseroid.grammar.Symbol;
import org.ngscript.parseroid.parser.AstNode;
import org.ngscript.parseroid.parser.ParserException;
import org.ngscript.parseroid.parser.Token;
import org.ngscript.runtime.VirtualMachine;

import java.io.PrintWriter;
import java.util.List;

/**
 * @author wssccc
 */
public class Ngscript {

    Configuration configuration;
    NgLalrParser parser;
    Compiler compiler;
    VirtualMachine vm = new VirtualMachine(new PrintWriter(System.out), new PrintWriter(System.err));

    public Ngscript() {
        this(Configuration.DEFAULT);
    }

    public Ngscript(Configuration configuration) {
        this.configuration = configuration;
        this.compiler = new Compiler(configuration);
        this.parser = new NgLalrParser(configuration);
    }

    public Object eval(String code) throws ParserException, CompilerException {
        feed(code);
        return vm.eax;
    }

    public boolean feed(String code) throws CompilerException, LexerException, ParserException {
        List<Token> tokens = Lexer.scan(code);
        if (!configuration.isInteractive()) {
            tokens.add(new Token(Symbol.EOF.identifier));
        }
        Token[] ts = tokens.toArray(new Token[0]);
        boolean compiled = parser.feed(ts);
        if (compiled) {
            AstNode ast = parser.getResult();
            parser.reduce(ast);
            NgLalrParser.removeNULL(ast);
            if (configuration.isGenerateDebugInfo()) {
                System.out.println(ast);
            }
            //
            List<Instruction> ins = compiler.compileCode(ast, code);
            if (configuration.isGenerateDebugInfo()) {
                System.out.println(ins);
            }
            vm.loadInstructions(ins);
            vm.run();
        }
        return compiled;
    }
}
