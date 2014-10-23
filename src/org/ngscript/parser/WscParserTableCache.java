/*
 *  wssccc all rights reserved
 */
package org.ngscript.parser;

import org.ngscript.parseroid.grammar.Grammar;
import org.ngscript.parseroid.grammar.GrammarLoader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.ngscript.parseroid.table.LALRTable;
import org.ngscript.parseroid.table.TableGenerator;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscParserTableCache {

    //cached field
    private static LALRTable table = null;

    public static synchronized LALRTable getTable() {
        if (table == null) {
            try {
                //loadBin();
                autoLoad();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
        return table;
    }

    static void loadBin() throws IOException, ClassNotFoundException {
        String bnffile = "grammar/ngscript-bnf.txt";
        String binCache = bnffile + "_cached_table.bin";
        ObjectInputStream ois = new ObjectInputStream(WscParserTableCache.class.getResourceAsStream(binCache));
        table = (LALRTable) ois.readObject();

    }

    static void autoLoad() throws FileNotFoundException, IOException, ClassNotFoundException {
        String bnffile = "grammar/ngscript-bnf.txt";
        String cache = bnffile + "_cached_table.bin";
        File bnf = new File(bnffile);
        File table_cache = new File(cache);

        if (!table_cache.exists() || bnf.lastModified() > table_cache.lastModified()) {
            Grammar g = GrammarLoader.loadBNF(new FileInputStream(bnffile));
            table = new TableGenerator(g).generate(false);
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cache));
            oos.writeObject(table);
            bnf.setLastModified(table_cache.lastModified());
        } else {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(table_cache));
            table = (LALRTable) ois.readObject();
        }
    }
}
