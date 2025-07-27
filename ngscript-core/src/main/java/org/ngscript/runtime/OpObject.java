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
        sh.setPrototype(vm.objectPrototype);
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
        if (objs[0] instanceof ScriptFunction) {
            MemRef ref = functionMember((ScriptFunction) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.ObjectCtor) {
            MemRef ref = org.ngscript.builtins.ObjectCtor.lookup(
                    (org.ngscript.builtins.ObjectCtor) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
        }
        if (objs[0] instanceof org.ngscript.builtins.ArrayCtor) {
            MemRef ref = org.ngscript.builtins.ArrayCtor.lookup(
                    (org.ngscript.builtins.ArrayCtor) objs[0], member, vm);
            if (ref != null) {
                vm.eax.write(ref);
                return;
            }
        }
        // script-created properties on host objects (e.g. list.customFn = f)
        java.util.Map<String, MemRef> props = vm.hostProps.get(objs[0]);
        if (props != null) {
            MemRef dynamic = props.get(member);
            if (dynamic != null) {
                vm.eax.write(dynamic);
                return;
            }
        }
        Object ref = NgsObject.lookupNative(objs[0], member, vm);

        if (ref != null) {
            vm.eax.write(ref);
            return;
        }
        // an unset property of a host object reads/writes as undefined
        java.util.Map<String, MemRef> created = vm.hostProps.computeIfAbsent(objs[0],
                k -> new java.util.HashMap<>());
        MemRef slot = created.get(member);
        if (slot == null) {
            slot = new MemRef(Undefined.value);
            created.put(member, slot);
        }
        vm.eax.write(slot);
    }

    /**
     * ES function object properties: prototype, name, length.
     */
    private static MemRef functionMember(ScriptFunction fn, String member, Machine vm) {
        switch (member) {
            case "prototype":
                return new MemRef(fn.getPrototypeObject());
            case "name":
                return new MemRef(fn.name == null ? "" : fn.name);
            case "length":
                return new MemRef(fn.params);
            default:
                return null;
        }
    }

    public static void static_func(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        OpCall.new_closure(vm, param_extend, null);
        ((ScriptFunction) vm.eax.read()).name = param;
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
            vm.eax.write(((NgsObject) obj).has(key) ? Boolean.TRUE : Boolean.FALSE);
        } else if (obj instanceof String) {
            try {
                int idx = Integer.parseInt(key);
                vm.eax.write(idx >= 0 && idx < ((String) obj).length() ? Boolean.TRUE : Boolean.FALSE);
            } catch (NumberFormatException e) {
                vm.eax.write(Boolean.FALSE);
            }
        } else {
            vm.eax.write(Boolean.FALSE);
        }
    }

    public static void instanceof_op(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        Object obj = objs[0];
        Object ctor = objs[1];
        if (ctor instanceof ScriptFunction) {
            if (obj instanceof NgsObject) {
                vm.eax.write(((NgsObject) obj).isInstanceOf((ScriptFunction) ctor) ? Boolean.TRUE : Boolean.FALSE);
            } else {
                vm.eax.write(Boolean.FALSE);
            }
        } else if (ctor instanceof Class) {
            // Java classes: ordinary instanceof (e.g. e instanceof Error)
            vm.eax.write(((Class) ctor).isInstance(obj) ? Boolean.TRUE : Boolean.FALSE);
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
        if (obj instanceof Boolean) {
            vm.eax.write("boolean");
            return;
        }
        if (obj instanceof ScriptFunction) {
            vm.eax.write("function");
            return;
        }
        if (obj instanceof Invokable) {
            vm.eax.write("function");
            return;
        }
        if (obj instanceof NgsObject || obj instanceof NgsArray || obj instanceof RegExpValue) {
            // ES: objects report "object" (foreign Java classes keep their
            // qualified name for the interop extension)
            vm.eax.write("object");
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
        NgsObject oldEnv;
        Object cons;
        if (newObject == null) {
            //native constructor: stack (top first): instance, env, args, func, receiver
            newObject = vm.stack.pop();
            oldEnv = (NgsObject) vm.stack.pop();
            vm.stack.pop(); //params
            cons = vm.stack.pop();
        } else {
            //script constructor: stack (top first): env, args, func, receiver
            oldEnv = (NgsObject) vm.stack.pop();
            vm.stack.pop(); //params
            cons = vm.stack.pop();
        }
        vm.stack.pop(); //receiver
        if (cons instanceof ScriptFunction) {
            ScriptFunction ctor = (ScriptFunction) cons;
            if (newObject instanceof NgsObject) {
                NgsObject instance = (NgsObject) newObject;
                instance.setConstructor(ctor);
                // link the instance to the constructor's prototype (ES [[Construct]])
                instance.setPrototype(ctor.getPrototypeObject());
                // a script constructor's frame is the instance: bind this
                vm.thisRef.write(instance);
            }
        }

        vm.eax.write(newObject);
        vm.env.write(oldEnv);
    }
}
