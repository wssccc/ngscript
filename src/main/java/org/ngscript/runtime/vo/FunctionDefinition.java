/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime.vo;

import org.ngscript.runtime.Environment;
import org.ngscript.runtime.VirtualMachine;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class FunctionDefinition {

    public String functionLable;
    public Environment closure_env;
    public VirtualMachine vm;

    public FunctionDefinition(String functionLable, Environment closure_env, VirtualMachine vm) {
        this.functionLable = functionLable;
        this.closure_env = closure_env;
        this.vm = vm;
    }

}
