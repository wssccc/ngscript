/*
 *  wssccc all rights reserved
 */
package parseroid.util;

import parseroid.grammar.Symbol;
import parseroid.parser.Token;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class TokenLoader {

    public static Token[] load(String filename) throws FileNotFoundException {
        ArrayList<Token> tokens = new ArrayList<Token>();
        Scanner sc = new Scanner(new File(filename));
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            int pos = line.indexOf(' ');

            String type = line.substring(0, pos);
            String val = line.substring(pos);
            tokens.add(new Token(type, 0, val));
        }
        tokens.add(new Token(Symbol.EOF.identifier, -1));
        Token[] ta = new Token[tokens.size()];
        tokens.toArray(ta);
        return ta;
    }
}
