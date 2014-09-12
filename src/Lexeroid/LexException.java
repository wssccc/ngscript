/*
 *  wssccc all rights reserved
 */
package Lexeroid;

import java.util.ArrayList;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class LexException extends Exception {

    SourceStream sourceStream;
    String buffered;

    public LexException(SourceStream sourceStream, String buffered, String message) {
        super(message);
        this.sourceStream = sourceStream;
        this.buffered = buffered;
    }

    @Override
    public String toString() {
        return "LexException: " + super.getMessage() + sourceStream.toString() + "\r\nrecognized:\r\n" + buffered;
    }

}
