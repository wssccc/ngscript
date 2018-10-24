package org.ngscript.runtime.utils;

/**
 * @author wssccc
 */
public class VarArgHelper {

    public static Object[] packVarArgs(Object[] args) {
        int nonVarsCount = args.length - 1;
        Object[] newArgs = new Object[nonVarsCount + 1];
        Object[] varArgs = new Object[args.length - nonVarsCount];
        System.arraycopy(args, nonVarsCount, varArgs, 0, varArgs.length);
        System.arraycopy(args, 0, newArgs, 0, newArgs.length);
        newArgs[newArgs.length - 1] = varArgs;
        return newArgs;
    }
}
