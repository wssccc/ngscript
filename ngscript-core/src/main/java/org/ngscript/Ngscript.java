package org.ngscript;

import org.ngscript.compiler.Compiler;
import org.ngscript.compiler.Instruction;
import org.ngscript.fastlexer.Lexer;
import org.ngscript.parser.NgLalrParser;
import org.ngscript.parseroid.grammar.Symbol;
import org.ngscript.parseroid.parser.AstNode;
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

    public Object eval(String code) throws Exception {
        feed(code);
        return vm.eax;
    }

    public boolean feed(String code) throws Exception {
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
            System.out.println(ast);
            compiler.compileCode(ast, code);
            //
            List<Instruction> ins = compiler.getCompiledInstructions();
            if (configuration.isGenerateDebugInfo()) {
                System.out.println(ins);
            }
            vm.loadInstructions(ins);
            vm.run();
        }
        return compiled;
    }
}
