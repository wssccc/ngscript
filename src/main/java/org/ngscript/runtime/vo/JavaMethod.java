/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime.vo;

import org.ngscript.runtime.InterpreterUtils;
import org.ngscript.runtime.VirtualMachine;
import org.ngscript.runtime.VmRuntimeException;
import org.ngscript.runtime.utils.VarArgHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class JavaMethod implements VmMethod {

    public Object caller;
    public ArrayList<Method> methods;
    String methodName;

    public JavaMethod(Object caller, ArrayList<Method> methods) {
        this.caller = caller;
        this.methods = methods;
        init();
    }

    public JavaMethod(Object caller, Method method) {
        this.caller = caller;
        this.methods = new ArrayList<>();
        this.methods.add(method);
        init();
    }

    void init() {
        Method m1 = methods.get(0);
        methodName = m1.getDeclaringClass().getName() + "#" + m1.getName();
    }

    @Override
    public void invoke(VirtualMachine vm, Object[] args) throws Exception {
        Class[] types = vm.getParamTypes(2);
        Method properMethod = MethodTypeMap.INSTANCE.getProperMethod(methodName, methods, types);
        if (properMethod == null) {
            throw new VmRuntimeException(vm, "no proper method found for " + methods + "[" + Arrays.toString(types) + "]");
        }
        if (properMethod.isVarArgs()) {
            args = VarArgHelper.packVarArgs(args);
        }
        try {
            Object val = properMethod.invoke(caller, args);
            if (val instanceof Long) {
                val = ((Long) val).intValue();
            }
            vm.eax.write(val);
        } catch (Exception ex) {
            vm.exception.write(new VmRuntimeException(vm, ex.getCause() == null ? ex.toString() : ex.getCause().toString()));
            InterpreterUtils.restore_machine_state(vm, null, null);
        }
    }
}
