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

package org.ngscript.parseroid.grammar;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

/**
 * @author wssccc
 */
public class GrammarLoader {

    public static Grammar loadBnfString(String bnfString) throws FileNotFoundException {

        Queue<String> q = new LinkedList<>();
        Scanner sc = new Scanner(bnfString);

        sc.useDelimiter(";|//.*");
        Grammar g = new Grammar();
        String lineno;
        while (sc.hasNext()) {
            String line = sc.next();
            lineno = line;
            Scanner linesc = new Scanner(line);
            //notation
            if (linesc.hasNext()) {
                String header = linesc.next();
                if (header.equals("%filter")) {
                    while (linesc.hasNext()) {
                        String keywordNotation = linesc.next();
                        g.getFilterNotations().add(keywordNotation);
                    }
                } else if (header.equals("%array")) {
                    while (linesc.hasNext()) {
                        String arrayNotation = linesc.next();
                        String name = getVnName(arrayNotation, "array notation starts with non-terminal symbol, at line" + lineno);
                        g.getArrayNotations().add(name);
                    }
                } else if (header.equals("%equiv")) {
                    String classNotationHeader = linesc.next();
                    classNotationHeader = getVnName(classNotationHeader, "class notation starts with non-terminal symbol, at line" + lineno);

                    while (linesc.hasNext()) {
                        String notationClass = linesc.next();
                        notationClass = getVnName(notationClass, "class notation starts with non-terminal symbol, at line" + lineno);
                        g.getClassNotations().put(notationClass, classNotationHeader);
                    }
                } else if (header.equals("%start")) {
                    String rootNotationHeader = linesc.next();
                    if (g.getRootSymbol() != null) {
                        throw new RuntimeException("dunplicated root symbol notation");
                    } else {
                        rootNotationHeader = getVnName(rootNotationHeader, "class notation starts with non-terminal symbol, at line" + lineno);
                        g.setRootSymbol(rootNotationHeader);
                    }
                } else if (line.startsWith("#")) {
                    //nothing
                } else {
                    int produceLength = -1;
                    int pos = 0;
                    linesc = new Scanner(line);
                    while (linesc.hasNext()) {
                        String token = linesc.next();
                        if (!token.equals("::=")) {
                            q.offer(token);
                            ++pos;
                        }
                        if (token.equals("=>")) {
                            //delete 2 items
                            produceLength = pos - 2;
                        }
                    }
                    String sym = q.poll();
                    sym = getVnName(sym, "production begins with non-terminal symbol, at line" + lineno);
                    if (produceLength == -1) {
                        //uninitialized
                        produceLength = q.size();
                    } else {
                    }
                    Symbol[] produces = new Symbol[produceLength];

                    for (int i = 0; i < produceLength; i++) {
                        String tk = q.poll();
                        if (tk.startsWith("<") && tk.endsWith(">")) {
                            tk = tk.substring(1, tk.length() - 1);
                            produces[i] = g.createSymbol(tk, false);
                        } else {
                            produces[i] = g.createSymbol(tk, true);
                        }
                    }
                    int proid = g.createProduction(sym, produces);

                    if (!q.isEmpty()) {
                        q.poll(); //delete separator
                        Symbol[] produce_alias = new Symbol[q.size()];
                        for (int i = 0; i < produce_alias.length; i++) {
                            String tk = q.poll();
                            if (tk.startsWith("<") && tk.endsWith(">")) {
                                tk = tk.substring(1, tk.length() - 1);
                                produce_alias[i] = g.createSymbol(tk, false);
                            } else {
                                produce_alias[i] = g.createSymbol(tk, true);
                            }
                        }
                        g.createProductionAlias(proid, produce_alias);
                    }

                }
            } else {
                //no next
            }
        }
        g.checkProduction();
        return g;
    }

    static String getVnName(String vn, String errorMsg) {
        if (!(vn.startsWith("<") && vn.endsWith(">"))) {
            throw new RuntimeException(errorMsg);
        } else {
            vn = vn.substring(1, vn.length() - 1);
            return vn;
        }

    }
}
