package parseroid.util;

import parseroid.grammar.GrammarLoader;
import parseroid.parser.Token;
import parseroid.table.LALRTable;
import parseroid.grammar.Grammar;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import parseroid.parser.AstNode;
import parseroid.parser.LALRParser;
import parseroid.parser.ParserException;
import parseroid.parser.TokenStream;
import parseroid.table.TableGenerator;

/*
 *  wssccc all rights reserved
 */
/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class ParseroidTest {

    /**
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     * @throws parser.ParserException
     */
    public static void main(String[] args) throws FileNotFoundException, ParserException {
//        // TODO code application logic here
//        Grammar g2 = GrammarLoader.loadBNF(new FileInputStream("wbnf.txt"));
//        //test
//        TableGenerator generator = new TableGenerator(g2);
//        LALRTable lt = generator.generate(true);
//
//        final Token[] tokens = TokenLoader.load("tokens.txt");
//
//        LALRParser a = new LALRParser(lt);
//
//        AstNode ast = a.parse(new TokenStream() {
//            int i = 0;
//
//            @Override
//            public Token next() {
//                if (i < tokens.length) {
//                    return tokens[i++];
//                } else {
//                    return null;
//                }
//            }
//        });
//        //LALRParser.reduce(ast, g2);
//        System.out.println(ast);
//        System.out.println("5*(1+2)+5*3");
    }

}
