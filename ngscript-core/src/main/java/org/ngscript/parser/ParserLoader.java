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

package org.ngscript.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.ngscript.parseroid.grammar.Grammar;
import org.ngscript.parseroid.grammar.GrammarLoader;
import org.ngscript.parseroid.table.LALRTable;
import org.ngscript.parseroid.table.TableGenerator;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @author wssccc
 */
@Slf4j
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
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFile))) {
                table = (LALRTable) ois.readObject();
                return;
            } catch (Exception ex) {
                log.warn("load parser table cache failed", ex);
            }
        }
        Grammar g = GrammarLoader.loadBnfString(bnfString);
        table = new TableGenerator(g).generate(false);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
            oos.writeObject(table);
        }
    }
}
