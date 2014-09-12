/*
 *  wssccc all rights reserved
 */
package ngscript.compiler;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscCompilerException extends Exception {

    WscCompiler compiler;

    public WscCompilerException(WscCompiler compiler, String message) {
        super(message + " at line " + compiler.printedLines);
        this.compiler = compiler;
    }

}
