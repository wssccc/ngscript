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

package org.ngscript.interop;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgScriptRuntimeException;
import org.ngscript.runtime.OpCall;
import org.ngscript.runtime.VarArgUtils;
import org.ngscript.value.Invokable;

/**
 * @author wssccc
 */
public class JavaMethod implements Invokable {

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
    public void invoke(Machine vm, Object[] args) throws Exception {
        Class[] types = vm.getParamTypes(1);
        Method properMethod = MethodTypeMap.INSTANCE.getProperMethod(methodName, methods, types);
        if (properMethod == null) {
            throw new NgScriptRuntimeException(vm,
                    String.format("No matching method found for %s#%s with parameter types %s. Available methods: %s",
                            methods.get(0).getDeclaringClass().getSimpleName(),
                            methods.get(0).getName(),
                            Arrays.toString(types),
                            methods.stream()
                                    .map(m -> m.getDeclaringClass().getSimpleName() + "#" + m.getName() + "("
                                            + Arrays.toString(m.getParameterTypes()) + ")")
                                    .collect(Collectors.joining(", "))));
        }
        properMethod.setAccessible(true);
        if (properMethod.isVarArgs()) {
            args = VarArgUtils.packVarArgs(args, properMethod.getParameterTypes().length - 1);
        }
        try {
            Object val = properMethod.invoke(caller, args);
            if (val instanceof Long) {
                val = ((Long) val).intValue();
            }
            if (val instanceof Object[]) {
                // Java array results become script arrays
                Object[] src = (Object[]) val;
                org.ngscript.runtime.NgsArray out = new org.ngscript.runtime.NgsArray(null);
                for (int i = 0; i < src.length; i++) {
                    out.set("" + i, src[i]);
                }
                out.setPrototype(vm.arrayPrototype);
                val = out;
            } else if (val instanceof int[]) {
                int[] src = (int[]) val;
                org.ngscript.runtime.NgsArray out = new org.ngscript.runtime.NgsArray(null);
                for (int i = 0; i < src.length; i++) {
                    out.set("" + i, src[i]);
                }
                out.setPrototype(vm.arrayPrototype);
                val = out;
            } else if (val instanceof double[]) {
                double[] src = (double[]) val;
                org.ngscript.runtime.NgsArray out = new org.ngscript.runtime.NgsArray(null);
                for (int i = 0; i < src.length; i++) {
                    out.set("" + i, src[i]);
                }
                out.setPrototype(vm.arrayPrototype);
                val = out;
            }
            vm.eax.write(val);
        } catch (Exception ex) {
            vm.exception.write(
                    new NgScriptRuntimeException(vm, ex.getCause() == null ? ex.toString() : ex.getCause().toString()));
            OpCall.restore_machine_state(vm, null, null);
        }
    }
}
