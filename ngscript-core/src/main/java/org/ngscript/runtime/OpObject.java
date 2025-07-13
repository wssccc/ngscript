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

import java.util.Arrays;

import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.RegExpValue;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;

/**
 * Object opcodes: literal construction, member access, closures,
 * {@code typeof} and the {@code new} operator.
 *
 * @author wssccc
 */
public class OpObject {

    public static void object_new(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        NgsObject sh = new NgsObject((NgsObject) vm.env.read());
        int size = Integer.parseInt(param);
        for (int i = 0; i < size; i++) {
            Object v = vm.stack.pop();
            String k = (String) vm.stack.pop();
            sh.set(k, v);
        }
        vm.eax.write(sh);
    }

    public static void member_ref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        String member = (String) objs[1];
        if (objs[0] instanceof NgsObject) {
            NgsObject env = (NgsObject) objs[0];
            MemRef ref = env.lookup(member, vm, true);
            vm.eax.write(ref);
            return;
        }
        //member of native
        Object ref = NgsObject.lookupNative(objs[0], member, vm);

        if (ref != null) {
            vm.eax.write(ref);
            return;
        }
        throw new NgScriptRuntimeException(vm, member + " is not a member of " + OpUtils.getObjInfo(objs[0]));
    }

    public static void static_func(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        OpCall.new_closure(vm, param_extend, null);
        OpScope.set(vm, param, null);
    }

    /**
     * Script-visible value types whose {@code typeof} is reported as their
     * simple class name. Foreign Java classes always report their fully
     * qualified name.
     */
    private static final java.util.Set<Class<?>> TYPE_OF_SHORT_NAMES = new java.util.HashSet<>(Arrays.asList(
            Undefined.class, ScriptFunction.class, MemRef.class, Invokable.class,
            NgsObject.class, NgsArray.class, RegExpValue.class));

    public static void regex_new(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(new RegExpValue(param, param_extend));
    }

    public static void delete_op(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        Object obj = objs[0];
        String key = objs[1].toString();
        if (obj instanceof NgsObject) {
            vm.eax.write(((NgsObject) obj).remove(key) ? 1 : 0);
        } else if (obj instanceof NgsArray) {
            vm.eax.write(((NgsArray) obj).remove(key) ? 1 : 0);
        } else {
            vm.eax.write(1); // deleting a property of a host object: treat as success
        }
    }

    public static void void_op(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(Undefined.value);
    }

    public static void in(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        Object obj = objs[1];
        String key = objs[0].toString();
        if (obj instanceof NgsObject) {
            vm.eax.write(((NgsObject) obj).has(key) ? 1 : 0);
        } else if (obj instanceof String) {
            try {
                int idx = Integer.parseInt(key);
                vm.eax.write(idx >= 0 && idx < ((String) obj).length() ? 1 : 0);
            } catch (NumberFormatException e) {
                vm.eax.write(0);
            }
        } else {
            vm.eax.write(0);
        }
    }

    public static void instanceof_op(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        Object obj = objs[0];
        Object ctor = objs[1];
        if (ctor instanceof ScriptFunction) {
            if (obj instanceof NgsObject) {
                vm.eax.write(((NgsObject) obj).isInstanceOf((ScriptFunction) ctor) ? 1 : 0);
            } else {
                vm.eax.write(0);
            }
        } else {
            throw new NgScriptRuntimeException(vm, "right-hand side of instanceof is not callable");
        }
    }

    /**
     * Prepares a for-in loop: converts the current value to an array of keys
     * and pushes it with the loop index on the stack.
     */
    public static void forin_init(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object obj = vm.eax.read();
        String[] keys;
        if (obj instanceof NgsObject) {
            java.util.Set<String> set = ((NgsObject) obj).keys();
            keys = set.toArray(new String[0]);
        } else if (obj instanceof String) {
            String s = (String) obj;
            keys = new String[s.length()];
            for (int i = 0; i < s.length(); i++) {
                keys[i] = i + "";
            }
        } else {
            keys = new String[0];
        }
        vm.stack.push(keys);
        vm.stack.push(0);
    }

    /**
     * Advances a for-in loop: yields the next key in eax, or a falsy value
     * (0) when the iteration is done (and pops the iteration state).
     */
    public static void forin_next(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        int i = (Integer) vm.stack.pop();
        String[] keys = (String[]) vm.stack.peek(0);
        if (i < keys.length) {
            vm.stack.push(i + 1);
            vm.eax.write(keys[i]);
        } else {
            vm.stack.pop();
            vm.eax.write(0);
        }
    }

    public static void typeof(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object obj = vm.eax.read();
        //common types
        if (obj == Undefined.value) {
            vm.eax.write("undefined");
            return;
        }
        if (obj == null) {
            vm.eax.write("object");
            return;
        }
        if (obj instanceof Integer) {
            vm.eax.write("number");
            return;
        }
        if (obj instanceof Double) {
            vm.eax.write("number");
            return;
        }
        if (obj instanceof String) {
            vm.eax.write("string");
            return;
        }
        if (obj.getClass().isAnonymousClass()) {
            vm.eax.write(obj.getClass().getSuperclass().getName());
        } else if (TYPE_OF_SHORT_NAMES.contains(obj.getClass())) {
            vm.eax.write(obj.getClass().getSimpleName());
        } else {
            vm.eax.write(obj.getClass().getName());
        }
    }

    public static void test(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        MemRef m = vm.lookup(param);
        if (m.read() == null) {
            vm.eax.write(0);
        } else {
            vm.eax.write(1);
        }
    }

    public static void clear(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        MemRef m = vm.lookup(param);
        m.write(Undefined.value);
    }

    public static void clear_null(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        MemRef m = vm.lookup(param);
        m.write(null);
    }

    public static void new_op(Machine vm, String param, String param_extend) {
        //identify native object and script object
        Object newObject = vm.env.read();
        if (newObject == null) {
            //native object
            newObject = vm.stack.pop();
        }
        NgsObject oldEnv = (NgsObject) vm.stack.pop();
        Object cons = vm.stack.pop();
        if (newObject instanceof NgsObject && cons instanceof ScriptFunction) {
            ((NgsObject) newObject).setConstructor((ScriptFunction) cons);
        }

        vm.eax.write(newObject);
        vm.env.write(oldEnv);
        vm.stack.pop(); //params
    }
}
