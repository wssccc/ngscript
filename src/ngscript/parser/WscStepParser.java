/*
 *  wssccc all rights reserved
 */
package ngscript.parser;

import parseroid.parser.StepParser;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscStepParser extends StepParser {

    public WscStepParser() {
        super(WscParserTableCache.getTable());
    }
}
