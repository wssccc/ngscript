/*
 *  wssccc all rights reserved
 */
package org.ngscript.parser;

import java.io.FileNotFoundException;
import java.io.IOException;
import org.ngscript.parseroid.parser.LALRParser;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscStreamParser extends LALRParser {

    public WscStreamParser() throws IOException, FileNotFoundException, ClassNotFoundException {
        super(WscParserTableCache.getTable());
    }

}
