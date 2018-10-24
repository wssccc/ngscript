/*
 *  wssccc all rights reserved
 */
package org.ngscript;

import org.apache.commons.io.IOUtils;
import org.ngscript.compiler.Instruction;
import org.ngscript.compiler.Compiler;
import org.ngscript.fastlexer.Lexer;
import org.ngscript.runtime.VirtualMachine;
import org.ngscript.parser.NgLalrParser;
import org.ngscript.parseroid.grammar.Symbol;
import org.ngscript.parseroid.parser.AstNode;
import org.ngscript.parseroid.parser.Token;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class WscLang {

    public static void main(String[] args) throws Exception {
        //init
        //interactive(System.in);
        long time = System.currentTimeMillis();
        test(Thread.currentThread().getContextClassLoader().getResourceAsStream("rose.txt"));
        //test("examples/example11-fibrec.txt");
        System.out.println("execute time " + (System.currentTimeMillis() - time) + " ms");
        //test("examples/example9.txt");
        //testExamples();
        //test();
        //testbean();
    }

    static String readFile(String filepath) throws FileNotFoundException, IOException {
        FileReader fileReader = new FileReader(filepath);
        char[] buffer = new char[1024];
        int n;
        StringBuilder stringBuilder = new StringBuilder();
        while ((n = fileReader.read(buffer)) != -1) {
            stringBuilder.append(buffer, 0, n);
        }
        fileReader.close();
        return stringBuilder.toString();
    }

    public static void test(InputStream inputStream) throws FileNotFoundException, Exception {
        //String code = readFile("testwl.txt");
        ArrayList<Instruction> ins = staticCompile(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
        VirtualMachine vm = new VirtualMachine(new PrintWriter(System.out), new PrintWriter(System.err));
        vm.loadInstructions(ins);
        vm.run();
    }

    public static ArrayList<Instruction> staticCompile(String code) throws Exception {
        Compiler defaultCompiler = new Compiler();
        final ArrayList<Token> tokens = Lexer.scan(code);
        //System.out.println(tokens);
        NgLalrParser streamParser = new NgLalrParser();
        //hold some
        ArrayList<Token> tokensa = new ArrayList<Token>();
        for (Token lt : tokens) {
            tokensa.add(new Token(lt.type, lt.line_no, lt.value));
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
