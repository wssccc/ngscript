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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ngscript.interop.JavaMethod;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.Undefined;

/**
 * @author wssccc
 */
public class OpImpl {

    public static void deref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        MemRef v1;
        if (param.equals("%eax")) {
            v1 = (MemRef) vm.eax.read();
        } else {
            v1 = vm.lookup(param);
        }
        vm.eax.write(v1.read());
    }

    public static void mov_eax(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        MemRef v1 = vm.lookup(param);
        vm.eax.write(v1);
    }

    public static void mov_exception_eax(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.exception.write(vm.eax.read());
    }

    public static void mov_eax_exception(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(vm.exception.read());
    }

    public static void mov(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {

        MemRef v1 = vm.lookup(param);
        Object val;
        if (param_extend.charAt(0) == '[') {
            //value
            String varName = param_extend.substring(1, param_extend.length() - 1);
            MemRef v2 = vm.lookup(varName);
            //is register
            if (varName.equals("%eax") || varName.equals("%exception") || varName.equals("%env")) {
                v2 = (MemRef) v2.read();
            }
            val = v2.read();
        } else {
            //ref
            MemRef v2 = vm.lookup(param_extend);
            if (param_extend.equals("%eax") || param_extend.equals("%exception") || param_extend.equals("%env")) {
                val = v2.read();
            } else {
                val = v2;
            }
        }
        v1.write(val);
    }

    public static void array_new(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        int length = Integer.parseInt(param);
        Environment list = new Environment((Environment) vm.env.read());

        for (int i = 0; i < length; i++) {
            Object object = vm.stack.pop();
            list.data.put("" + i, new MemRef(object));
        }
        vm.eax.write(list);
    }

    public static void object_new(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Environment sh = new Environment((Environment) vm.env.read());
        int size = Integer.parseInt(param);
        for (int i = 0; i < size; i++) {
            Object v = vm.stack.pop();
            String k = (String) vm.stack.pop();
            MemRef vref = new MemRef(v);
            sh.data.put(k, vref);
        }
        vm.eax.write(sh);
    }

    public static void neg(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object eax = vm.eax.read();
        if (eax instanceof Integer) {
            vm.eax.write(-((Integer) eax));
        }
        if (eax instanceof Double) {
            vm.eax.write(-((Double) eax));
        }
    }

    public static void push_eax(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.stack.push(vm.eax.read());
    }

    public static void push_env(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.stack.push(vm.env.read());
    }

    public static void push_eip(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.stack.push(vm.getEip());
    }

    public static void set(Machine vm, String param, String param_extend) {
        ((Environment) vm.env.read()).data.put(param, new MemRef(vm.eax.read()));
    }

    public static void val(Machine vm, String param, String param_extend) {
        ((Environment) vm.env.read()).data.put(param, new MemRef(null, true));
    }

    public static void member_ref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        String member = (String) objs[1];
        if (objs[0] instanceof Environment) {
            Environment env = (Environment) objs[0];
            MemRef ref = env.lookup(member, vm, true);
            vm.eax.write(ref);
            return;
        }
        //member of native
        Object ref = Environment.lookupNative(objs[0], member, vm);

        if (ref != null) {
            vm.eax.write(ref);
            return;
        }
        throw new NgScriptRuntimeException(vm, member + " is not a member of " + OpUtils.getObjInfo(objs[0]));
    }

    public static void array_ref(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (objs[0] instanceof Environment) {
            Environment env = (Environment) objs[0];
            String member = "" + objs[1];
            MemRef ref = env.lookup(member, vm, true);
            vm.eax.write(ref);
            return;
        }
        if (objs[0] instanceof ArrayList) {
            ArrayList env = (ArrayList) objs[0];
            int member = (int) Double.parseDouble("" + objs[1]);
            MemRef ref = (MemRef) env.get(member);
            vm.eax.write(ref);
            return;
        }
        throw new NgScriptRuntimeException(vm, "not an array object");
    }

    public static void static_func(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        OpImpl.new_closure(vm, param_extend, null);
        OpImpl.set(vm, param, null);
    }

    /**
     * Script-visible value types whose {@code typeof} is reported as their
     * simple class name. Foreign Java classes always report their fully
     * qualified name.
     */
    private static final java.util.Set<Class<?>> TYPE_OF_SHORT_NAMES = new java.util.HashSet<>(Arrays.asList(
            Undefined.class, ScriptFunction.class, MemRef.class, Invokable.class));

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

    public static void import_(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        String[] split = param.split("\\.");
        String shortName = split[split.length - 1];
        vm.imported.put(shortName, param);
    }

    public static void assign(Machine vm, String param, String param_extend) {
        Object[] objs = OpUtils.get2OpParam(vm);
        ((MemRef) objs[0]).write(objs[1]);
        vm.eax.write(objs[1]);
    }

    public static void lt(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        double d1 = OpUtils.getNumber(vm, objs[0]);
        double d2 = OpUtils.getNumber(vm, objs[1]);
        if (d1 < d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void gt(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        double d1 = OpUtils.getNumber(vm, objs[0]);
        double d2 = OpUtils.getNumber(vm, objs[1]);
        if (d1 > d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void ge(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        double d1 = OpUtils.getNumber(vm, objs[0]);
        double d2 = OpUtils.getNumber(vm, objs[1]);
        if (d1 >= d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void le(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        double d1 = OpUtils.getNumber(vm, objs[0]);
        double d2 = OpUtils.getNumber(vm, objs[1]);
        if (d1 <= d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void inc(Machine vm, String param, String param_extend) {
        OpUtils.addEax(vm, 1, false);
    }

    public static void dec(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        OpUtils.addEax(vm, -1, false);
    }

    public static void post_inc(Machine vm, String param, String param_extend) {
        OpUtils.addEax(vm, 1, true);
    }

    public static void post_dec(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        OpUtils.addEax(vm, -1, true);
    }

    public static void pop_eax(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(vm.stack.pop());
    }

    public static void pop_env(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.env.write(vm.stack.pop());
    }

    public static void pop(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.stack.pop();
    }

    public static void jmp(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        if ("offset".equals(param)) {
            int nip = (Integer) vm.eax.read();
            vm.setEip(nip + Integer.parseInt(param_extend));
        } else {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.setEip(nip);
            } else {
                throw new NgScriptRuntimeException(vm, "jump to no where " + param);
            }
        }
    }

    public static void jz(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object testObj = vm.eax.read();
        if (!OpUtils.testValue(testObj)) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.setEip(nip);
            } else {
                throw new NgScriptRuntimeException(vm, "jump to no where");
            }
        }
    }

    public static void jnz(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object testObj = vm.eax.read();
        if (OpUtils.testValue(testObj)) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.setEip(nip);
            } else {
                throw new NgScriptRuntimeException(vm, "jump to no where");
            }
        }
    }

    public static void label(Machine vm, String param, String param_extend) {
        //
    }

    public static void new_closure(Machine vm, String param, String param_extend) {
        ScriptFunction closure = new ScriptFunction(param, (Environment) vm.env.read(), vm);
        vm.eax.write(closure);
    }

    public static void integer(Machine vm, String param, String param_extend) {
        vm.eax.write(Integer.parseInt(param));
    }

    public static void bool(Machine vm, String param, String param_extend) {
        vm.eax.write(Boolean.parseBoolean(param));
    }

    public static void double_(Machine vm, String param, String param_extend) {
        vm.eax.write(Double.parseDouble(param));
    }

    public static void undefined(Machine vm, String param, String param_extend) {
        vm.eax.write(Undefined.value);
    }

    public static void string(Machine vm, String param, String param_extend) {
        String str = param;
        if (str.startsWith("\"") && str.endsWith("\"")) {
            //quote string
            str = str.substring(1, str.length() - 1);
        }
        str = str.replace("\\n", "\n");
        vm.eax.write(str);
    }

    public static void pickarg(Machine vm, String param, String param_extend) {
        int offset = Integer.parseInt(param);
        Object[] args = (Object[]) vm.stack.peek(3);
        if (offset < args.length) {
            vm.eax.write(args[offset]);
        } else {
            vm.eax.write(Undefined.value);
        }
    }

    public static void peek(Machine vm, String param, String param_extend) {
        int offset = Integer.parseInt(param);
        Object obj = vm.stack.peek(offset);
        vm.eax.write(obj);

    }

    public static void clear_call_stack(Machine vm, String param, String param_extend) {
        while (vm.callstack.size() > vm.callStackSize) {
            vm.callstack.pop();
        }
    }

    public static void ret(Machine vm, String param, String param_extend) {
        int nip = (Integer) vm.stack.pop();
        vm.callstack.pop();
        vm.setEip(nip);
    }

    public static void add(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_ADD, objs[0], objs[1]));
    }

    public static void bit_and(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.getInteger(vm, objs[0]) & OpUtils.getInteger(vm, objs[1]));
    }

    public static void bit_or(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.getInteger(vm, objs[0]) | OpUtils.getInteger(vm, objs[1]));
    }

    public static void bit_xor(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.getInteger(vm, objs[0]) ^ OpUtils.getInteger(vm, objs[1]));
    }

    public static void sub(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_SUB, objs[0], objs[1]));
    }

    public static void mul(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_MUL, objs[0], objs[1]));
    }

    public static void mod(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_MOD, objs[0], objs[1]));
    }

    public static void div(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_DIV, objs[0], objs[1]));
    }

    public static void eq(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        //runtime.eax.value=(new TypeOp(runtime).eval("eq", objs[0], objs[1]));
        vm.eax.write(OpUtils.testEq(objs[0], objs[1]));
    }

    public static void veq(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.testEq(objs[0], objs[1]));
    }

    public static void neq(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(!OpUtils.testEq(objs[0], objs[1]));
    }

    public static void vneq(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(!OpUtils.testEq(objs[0], objs[1]));
    }
//    public static void coroutine_return(Machine runtime, String param, String paramExtended) {
//        Coroutine nativeClosure = (Coroutine) runtime.stack.get(runtime.stack.size() - 1 - 1);
//        nativeClosure.returned();
//        nativeClosure.switchBack();
//    }

    public static void packargs(Machine vm, String param, String param_extend) {
        int n = Integer.parseInt(param);
        Object[] args = new Object[n];
        for (int i = 0; i < n; i++) {
            args[i] = vm.stack.pop();
        }
        vm.eax.write(args);
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
        Environment oldEnv = (Environment) vm.stack.pop();
        Object cons = vm.stack.pop();

        vm.eax.write(newObject);
        vm.env.write(oldEnv);
        vm.stack.pop(); //params
    }

    public static void go(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        // the go body shares the vm stack, so it must run synchronously on the current thread:
        // save the current position, jump into the go body and run until it halts, then resume.
        int savedEip = vm.getEip();
        jmp(vm, param, param_extend);
        vm.run();
        vm.setEip(savedEip);
    }

    public static void halt(Machine vm, String param, String param_extend) {
        vm.setEip(-1);
    }
}