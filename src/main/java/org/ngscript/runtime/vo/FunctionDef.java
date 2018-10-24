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
public class FunctionDef {

    public String functionLabel;
    public Environment environment;
    public VirtualMachine vm;

    public FunctionDef(String functionLabel, Environment environment, VirtualMachine vm) {
        this.functionLabel = functionLabel;
        this.environment = environment;
        this.vm = vm;
    }

}
