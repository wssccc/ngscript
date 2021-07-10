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
import org.ngscript.fastlexer.Lexer;
import org.ngscript.fastlexer.LexerException;
import org.ngscript.parseroid.parser.Token;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author wssccc
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
