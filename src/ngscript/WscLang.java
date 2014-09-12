/*
 *  wssccc all rights reserved
 */
package ngscript;

import Lexeroid.LexToken;
import parseroid.grammar.Symbol;
import java.io.File;
import ngscript.vm.WscVM;
import ngscript.compiler.WscCompiler;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import ngscript.common.Instruction;
import ngscript.parser.WscLexer;
import ngscript.parser.WscStepParser;
import ngscript.parser.WscStreamParser;
import parseroid.parser.AstNode;
import parseroid.parser.Token;
import parseroid.parser.TokenStream;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscLang {

    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     * @throws java.lang.ClassNotFoundException
     * @throws parser.ParserException
     * @throws Lexeroid.LexException
     */
    public static void main(String[] args) throws Exception {
        //init
        //interactive(System.in);
        testStep();
        //test();
        //testbean();
    }

    static void testbean() throws FileNotFoundException, Exception {
//        NgscriptBean nb = new NgscriptBean();
//        Scanner sc = new Scanner(new File("testwl.txt"));
//        while (sc.hasNextLine()) {
//            nb.write(sc.nextLine());
//            System.out.println(nb.readJson().replace("\\n", "\n"));
//        }
    }

    static void testStep() throws FileNotFoundException, Exception {
        WscCompiler defaultCompiler = new WscCompiler("SCS");
        final WscLexer lex = new WscLexer();

        WscStepParser stepParser = new WscStepParser();
        WscVM vm = new WscVM(new PrintWriter(System.out), new PrintWriter(System.err));

        Scanner sc = new Scanner(new File("examples/example2-native_feature.txt"));
        String codeBuffer = "";
        while (sc.hasNextLine()) {
            String code = sc.nextLine();
            codeBuffer += code + "\n";
            ArrayList<LexToken> tokens = lex.scanLine(code);
            for (LexToken lt : tokens) {
                stepParser.feed(new Token(lt.type, lt.line, lt.value));
                if (stepParser.compilable) {
                    AstNode ast = stepParser.getAst();
                    System.out.println(ast.toString());
                    defaultCompiler.compileCode(ast, codeBuffer);
                    ArrayList<Instruction> ins = defaultCompiler.getCompiledInstructions();
                    System.out.println(defaultCompiler.getAssembler().getInfoString(vm.getSize()));
                    //
                    vm.loadInstructions(ins);
                    vm.run();
                    vm.printEax(false);
                    stepParser.resetParser();
                    //reset
                    lex.reset();
                    codeBuffer = "";
                }
            }
        }
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

    static void test() throws FileNotFoundException, Exception {

        //String code = readFile("testwl.txt");
        ArrayList<Instruction> ins = staticCompile(readFile("testwl.txt"), "MAIN");
        try {
            WscVM vm = new WscVM(new PrintWriter(System.out), new PrintWriter(System.err));
            vm.loadInstructions(ins);
            vm.run();
        } catch (InvocationTargetException ex) {
            Logger.getLogger(WscLang.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static ArrayList<Instruction> staticCompile(String code, String namespace) throws Exception {

        WscCompiler defaultCompiler = new WscCompiler(namespace);
        final WscLexer lex = new WscLexer();
        final ArrayList<LexToken> tokens = lex.scanLine(code);
        WscStreamParser streamParser = new WscStreamParser();
        AstNode ast = streamParser.parse(new TokenStream() {
            int i = 0;

            @Override
            public Token next() {
                if (i < tokens.size()) {
                    LexToken lt = tokens.get(i++);
                    return new Token(lt.type, lt.line, lt.value);
                } else {
                    return new Token(Symbol.EOF.identifier);
                }
            }
        });

        ArrayList<Instruction> ins = new ArrayList<Instruction>();
        streamParser.reduce(ast);
        WscStreamParser.removeNULL(ast);
        defaultCompiler.compileCode(ast, code);
        ins.addAll(defaultCompiler.getCompiledInstructions());
        System.out.println(defaultCompiler.getAssembler().getInfoString(0));
        return ins;

    }
}
