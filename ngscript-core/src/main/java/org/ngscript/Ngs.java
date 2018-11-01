package org.ngscript;

import org.apache.commons.io.IOUtils;
import org.ngscript.compiler.Compiler;
import org.ngscript.compiler.Instruction;
import org.ngscript.fastlexer.Lexer;
import org.ngscript.parser.NgLalrParser;
import org.ngscript.parseroid.grammar.Symbol;
import org.ngscript.parseroid.parser.AstNode;
import org.ngscript.parseroid.parser.Token;
import org.ngscript.runtime.VirtualMachine;

import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * @author wssccc
 */
public class Ngs {

    public void run(InputStream inputStream) throws Exception {
        ArrayList<Instruction> ins = staticCompile(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
        VirtualMachine vm = new VirtualMachine(new PrintWriter(System.out), new PrintWriter(System.err));
        vm.loadInstructions(ins);
        vm.run();
    }

    public ArrayList<Instruction> staticCompile(String code) throws Exception {
        Compiler defaultCompiler = new Compiler();
        final ArrayList<Token> tokens = Lexer.scan(code);
        //System.out.println(tokens);
        NgLalrParser streamParser = new NgLalrParser();
        //hold some
        ArrayList<Token> tokensa = new ArrayList<Token>();
        for (Token lt : tokens) {
            tokensa.add(new Token(lt.type, lt.line, lt.value));
        }
        tokensa.add(new Token(Symbol.EOF.identifier));
        //
        Token[] ts = new Token[tokensa.size()];
        tokensa.toArray(ts);
        AstNode ast = streamParser.parse(ts);
        ArrayList<Instruction> ins = new ArrayList<Instruction>();
        streamParser.reduce(ast);
        NgLalrParser.removeNULL(ast);
        System.out.println(ast);
        defaultCompiler.compileCode(ast, code);
        defaultCompiler.getAssembler().doOptimize();
        ins.addAll(defaultCompiler.getCompiledInstructions());
        System.out.println(defaultCompiler.getAssembler().getInfoString(0));
        return ins;

    }
}
