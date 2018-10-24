/*
 *  wssccc all rights reserved
 */
package org.ngscript.parser;

import org.ngscript.parseroid.parser.LalrParser;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class NgLalrParser extends LalrParser {

    public NgLalrParser() {
        super(ParserLoader.INSTANCE.getTable());
    }

}
