/*
 *  wssccc all rights reserved
 */
package ngscript;

import org.ngscript.fastlexer.Lexer;
import java.io.File;
import ngscript.vm.WscVM;
import ngscript.compiler.WscCompiler;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import ngscript.common.Instruction;
import ngscript.parser.WscStreamParser;
import parseroid.grammar.Symbol;
import parseroid.parser.AstNode;
import parseroid.parser.Token;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscLang {

    /**
     * @param args the command line_no arguments
     * @throws java.io.IOException
     * @throws java.lang.ClassNotFoundException
     */
    public static void main(String[] args) throws Exception {
        //init
        //interactive(System.in);
        //long time = System.currentTimeMillis();
        //test("deprecated/rose.txt");
        //System.out.println("execute time " + (System.currentTimeMillis() - time) + " ms");
        //test("examples/example9.txt");
        testExamples();
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

    public static void test(String filename) throws FileNotFoundException, Exception {
        //String code = readFile("testwl.txt");
        ArrayList<Instruction> ins = staticCompile(readFile(filename));
        WscVM vm = new WscVM(new PrintWriter(System.out), new PrintWriter(System.err));
        vm.loadInstructions(ins);
        vm.run();
    }

    public static void testExamples() throws Exception {
        //
        File folder = new File("examples");
        File[] files = folder.listFiles();
        for (File file : files) {
            if (file.isFile()) {
                System.out.println("Testing " + file);
                try {
                    WscLang.test(file.getPath());
                } catch (Exception ex) {
                    System.out.println("Failed while testing " + file);
                    throw ex;
                }
            }
        }
    }

    public static ArrayList<Instruction> staticCompile(String code) throws Exception {
        WscCompiler defaultCompiler = new WscCompiler();
        final ArrayList<Token> tokens = Lexer.scan(code);
        //System.out.println(tokens);
        WscStreamParser streamParser = new WscStreamParser();
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
        WscStreamParser.removeNULL(ast);
        //System.out.println(ast);
        defaultCompiler.compileCode(ast, code);
        ins.addAll(defaultCompiler.getCompiledInstructions());
        System.out.println(defaultCompiler.getAssembler().getInfoString(0));
        return ins;

    }
}
