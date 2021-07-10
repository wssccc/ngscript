/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript.runtime.vo;

import org.ngscript.runtime.Op;
import org.ngscript.runtime.VirtualMachine;
import org.ngscript.runtime.VmRuntimeException;
import org.ngscript.runtime.utils.VarArgHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author wssccc
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
            Op.restore_machine_state(vm, null, null);
        }
    }
}
