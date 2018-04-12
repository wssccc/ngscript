/*
 *  wssccc all rights reserved
 */
package org.ngscript.fastlexer;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import org.ngscript.parseroid.parser.Token;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class LexerTest {

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

    public static void main(String[] args) throws LexerException, IOException {
        ArrayList<Token> tokens = Lexer.scan(readFile("rose.js"));
        System.out.println(tokens);
    }
}
