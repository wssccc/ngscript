/*
 *  wssccc all rights reserved
 */
package org.ngscript.compiler;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class CompilerException extends Exception {

    Compiler compiler;

    public CompilerException(Compiler compiler, String message) {
        super(message + " at line " + compiler.printedLines);
        this.compiler = compiler;
    }

}
