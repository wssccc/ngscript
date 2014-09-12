/*
 *  wssccc all rights reserved
 */
package parseroid.grammar;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class GrammarLoader {

    public static Grammar loadBNF(InputStream inputStream) throws FileNotFoundException {

        Queue<String> q = new LinkedList<String>();
        Scanner sc = new Scanner(inputStream);

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
                        g.filterNotations.add(keywordNotation);
                    }
                } else if (header.equals("%array")) {
                    while (linesc.hasNext()) {
                        String arrayNotation = linesc.next();
                        String name = getVnName(arrayNotation, "array notation starts with non-terminal symbol, at line" + lineno);
                        g.arrayNotations.add(name);
                    }
                } else if (header.equals("%equiv")) {
                    String classNotationHeader = linesc.next();
                    classNotationHeader = getVnName(classNotationHeader, "class notation starts with non-terminal symbol, at line" + lineno);

                    while (linesc.hasNext()) {
                        String notationClass = linesc.next();
                        notationClass = getVnName(notationClass, "class notation starts with non-terminal symbol, at line" + lineno);
                        g.classNotations.put(notationClass, classNotationHeader);
                    }
                } else if (header.equals("%start")) {
                    String rootNotationHeader = linesc.next();
                    if (g.rootSymbol != null) {
                        throw new RuntimeException("dunplicated root symbol notation");
                    } else {
                        rootNotationHeader = getVnName(rootNotationHeader, "class notation starts with non-terminal symbol, at line" + lineno);
                        g.rootSymbol = rootNotationHeader;
                    }
                } else if (line.startsWith("#")) {
                    //nothing
                } else {
                    int produce_length = -1;
                    int pos = 0;
                    linesc = new Scanner(line);
                    while (linesc.hasNext()) {
                        String token = linesc.next();
                        if (!token.equals("::=")) {
                            q.offer(token);
                            ++pos;
                        }
                        if (token.equals("=>")) {
                            produce_length = pos - 2; //delete 2 items
                        }
                    }
                    String sym = q.poll();
                    sym = getVnName(sym, "production begins with non-terminal symbol, at line" + lineno);
                    if (produce_length == -1) {
                        //uninitialized
                        produce_length = q.size();
                    } else {
                        //System.out.println("");
                    }
                    Symbol[] produces = new Symbol[produce_length];

                    for (int i = 0; i < produce_length; i++) {
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
