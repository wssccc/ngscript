/*
 *  wssccc all rights reserved
 */
package org.ngscript.parser;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.ngscript.parseroid.grammar.Grammar;
import org.ngscript.parseroid.grammar.GrammarLoader;
import org.ngscript.parseroid.table.LALRTable;
import org.ngscript.parseroid.table.TableGenerator;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class ParserLoader {

    private LALRTable table;

    public static ParserLoader INSTANCE = new ParserLoader();

    private ParserLoader() {
        try {
            init();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public LALRTable getTable() {
        return table;
    }

    private void init() throws Exception {
        String bnfString = IOUtils.toString(Thread.currentThread().getContextClassLoader().getResourceAsStream("grammar/ngscript-bnf.txt"), StandardCharsets.UTF_8);
        String hash = DigestUtils.sha1Hex(bnfString);
        String cacheFilePath = System.getProperty("java.io.tmpdir") + File.separator + "ng_bnf_cache_" + hash + ".classdump";
        File cacheFile = new File(cacheFilePath);
        if (cacheFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFile));) {
                table = (LALRTable) ois.readObject();
            }
        } else {
            Grammar g = GrammarLoader.loadBnfString(bnfString);
            table = new TableGenerator(g).generate(false);
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
                oos.writeObject(table);
            }
        }
    }
}
