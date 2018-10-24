/*
 *  wssccc all rights reserved
 */
package org.ngscript.parser;

import org.ngscript.parseroid.parser.LALRParser;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class NgLalrParser extends LALRParser {

    public NgLalrParser() {
        super(ParserLoader.INSTANCE.getTable());
    }

}
