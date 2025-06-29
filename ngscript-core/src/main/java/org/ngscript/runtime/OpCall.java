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

package org.ngscript.runtime;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ngscript.interop.JavaMethod;
import org.ngscript.value.Invokable;
import org.ngscript.value.ScriptFunction;

/**
 * Call-related opcodes: closure creation, function calls, native imports and
 * machine-state save/restore for exception handling.
 *
 * @author wssccc
 */
public class OpCall {

    public static void new_closure(Machine vm, String param, String param_extend) {
        ScriptFunction closure = new ScriptFunction(param, (Environment) vm.env.read(), vm);
        vm.eax.write(closure);
    }

    public static void import_(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        String[] split = param.split("\\.");
        String shortName = split[split.length - 1];
        vm.imported.put(shortName, param);
    }

    public static void call(Machine vm, String param, String param_extend) throws Exception {
        Object callee = vm.stack.peek(1);
        Object[] args = (Object[]) vm.stack.peek(2);

        if (callee instanceof ScriptFunction) {
            ScriptFunction c = (ScriptFunction) callee;
            vm.callstack.push(c);
            //prepare env
            Environment env = new Environment((Environment) c.environment);
            vm.env.write(env);
            int nip = vm.labels.get(c.functionLabel);
            vm.stack.push(vm.getEip());
            vm.setEip(nip);
            return;
        }

        //call native things
        if (callee instanceof Invokable) {
            try {
                ((Invokable) callee).invoke(vm, args);
            } catch (Exception ex) {
                Logger.getLogger(Machine.class.getName()).log(Level.SEVERE, null, ex);
                Object e = ex.getCause();
                if (e == null) {
                    e = ex;
                }
                vm.exception.write(e);
                restore_machine_state(vm, null, null);
            }
            return;
        }
        if (callee instanceof JavaMethod) {
            JavaMethod closure = (JavaMethod) callee;
            closure.invoke(vm, args);
            return;
        }

        //constructor
        if (callee instanceof Class) {
            Class cls = (Class) callee;
            Class[] types = vm.getParamTypes(2);
            Constructor[] conses = cls.getConstructors();
            Constructor properCons = null;
            if (conses.length == 1) {
                properCons = conses[0];
            } else {
                for (Constructor cons : conses) {
                    if (TypeCheckUtils.typeAcceptable(types, cons.getParameterTypes())) {
                        properCons = cons;
                        break;
                    }
                }
            }
            if (properCons == null) {
                throw new NgScriptRuntimeException(vm, "no constructor found for " + Arrays.toString(types));
            }
            //adjust args
            if (properCons.isVarArgs()) {
                Class[] argTypes = properCons.getParameterTypes();
                int nonVarsCount = argTypes.length - 1;
                Object[] newargs = new Object[nonVarsCount + 1];
                Object[] varargs = new Object[args.length - nonVarsCount];
                System.arraycopy(args, nonVarsCount, varargs, 0, varargs.length);
                System.arraycopy(args, 0, newargs, 0, newargs.length);
                newargs[newargs.length - 1] = varargs;
                args = newargs;
            }
            //prepare env
            vm.stack.push(properCons.newInstance(args));
            vm.env.write(null);
            return;
        }
        throw new NgScriptRuntimeException(vm, "unexpected callee");
    }

    public static void save_machine_state(Machine vm, String param, String param_extend) {
        Context ms = new Context(vm);
        vm.machineStateStack.push(ms);
    }

    public static void drop_machine_state(Machine vm, String param, String param_extend) {
        vm.machineStateStack.pop();
    }

    public static void restore_machine_state(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        if (vm.machineStateStack.isEmpty()) {
            Object ex = vm.exception.read();
            if (ex instanceof NgScriptRuntimeException) {
                throw (NgScriptRuntimeException) ex;
            } else {
                throw new NgScriptRuntimeException(vm, ex.toString());
            }
        } else {
            Context ms = vm.machineStateStack.pop();
            ms.restore(vm);
        }
    }
}
