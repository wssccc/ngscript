/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime;

import org.ngscript.runtime.utils.TypeCheck;
import org.ngscript.runtime.utils.TypeOp;
import org.ngscript.runtime.vo.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class Op {

    public static void deref(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        VmMemRef v1;
        if (param.equals("%eax")) {
            v1 = (VmMemRef) vm.eax.read();
        } else {
            v1 = vm.lookup(param);
        }
        vm.eax.write(v1.read());
    }

    public static void mov_eax(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        VmMemRef v1 = vm.lookup(param);
        vm.eax.write(v1);
    }

    public static void mov_exception_eax(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        vm.exception.write(vm.eax.read());
    }

    public static void mov_eax_exception(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        vm.eax.write(vm.exception.read());
    }

    public static void mov(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {

        VmMemRef v1 = vm.lookup(param);
        Object val;
        if (param_extend.charAt(0) == '[') {
            //value
            String varName = param_extend.substring(1, param_extend.length() - 1);
            VmMemRef v2 = vm.lookup(varName);
            //is register
            if (varName.equals("%eax") || varName.equals("%exception") || varName.equals("%env")) {
                v2 = (VmMemRef) v2.read();
            }
            val = v2.read();
        } else {
            //ref
            VmMemRef v2 = vm.lookup(param_extend);
            if (param_extend.equals("%eax") || param_extend.equals("%exception") || param_extend.equals("%env")) {
                val = v2.read();
            } else {
                val = v2;
            }
        }
        v1.write(val);
    }

    public static void array_new(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        int length = Integer.parseInt(param);
        Environment list = new Environment((Environment) vm.env.read());

        for (int i = 0; i < length; i++) {
            Object object = vm.stack.pop();
            list.data.put("" + i, new VmMemRef(object));
        }
        vm.eax.write(list);
    }

    public static void object_new(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Environment sh = new Environment((Environment) vm.env.read());
        int size = Integer.parseInt(param);
        for (int i = 0; i < size; i++) {
            Object v = vm.stack.pop();
            String k = (String) vm.stack.pop();
            VmMemRef vref = new VmMemRef(v);
            sh.data.put(k, vref);
        }
        vm.eax.write(sh);
    }

    public static void neg(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object eax = vm.eax.read();
        if (eax instanceof Integer) {
            vm.eax.write(-((Integer) eax));
        }
        if (eax instanceof Double) {
            vm.eax.write(-((Double) eax));
        }
    }

    public static void push_eax(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        vm.stack.push(vm.eax.read());
    }

    public static void push_env(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        vm.stack.push(vm.env.read());
    }

    public static void push_eip(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        vm.stack.push(vm.eip);
    }

    public static void set(VirtualMachine vm, String param, String param_extend) {
        ((Environment) vm.env.read()).data.put(param, new VmMemRef(vm.eax.read()));
    }

    public static void member_ref(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        String member = (String) objs[1];
        if (objs[0] instanceof Environment) {
            Environment env = (Environment) objs[0];
            VmMemRef ref = env.lookup(member, vm, true);
            vm.eax.write(ref);
            return;
        }
        //member of native
        Object ref = Environment.lookupNative(objs[0], member, vm);

        if (ref != null) {
            vm.eax.write(ref);
            return;
        }
        throw new VmRuntimeException(vm, member + " is not a member of " + OpUtils.getObjInfo(objs[0]));
    }

    public static void array_ref(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (objs[0] instanceof Environment) {
            Environment env = (Environment) objs[0];
            String member = "" + objs[1];
            VmMemRef ref = env.lookup(member, vm, true);
            vm.eax.write(ref);
            return;
        }
        if (objs[0] instanceof ArrayList) {
            ArrayList env = (ArrayList) objs[0];
            int member = (int) Double.parseDouble("" + objs[1]);
            VmMemRef ref = (VmMemRef) env.get(member);
            vm.eax.write(ref);
            return;
        }
        throw new VmRuntimeException(vm, "not an array object");
    }

    public static void static_func(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Op.new_closure(vm, param_extend, null);
        Op.set(vm, param, null);
    }

    public static void typeof(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object obj = vm.eax.read();
        //common types
        if (obj == undefined.value) {
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
        } else {
            if (undefined.class.getPackage().getName().equals(obj.getClass().getPackage().getName())) {
                vm.eax.write(obj.getClass().getSimpleName());
            } else {
                vm.eax.write(obj.getClass().getName());
            }
        }
    }

    public static void import_(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        String[] splitted = param.split("\\.");
        String shortName = splitted[splitted.length - 1];
        vm.imported.put(shortName, param);
    }

    public static void assign(VirtualMachine vm, String param, String param_extend) {
        Object[] objs = OpUtils.get2OpParam(vm);
        ((VmMemRef) objs[0]).write(objs[1]);
        vm.eax.write(objs[1]);
    }

    public static void lt(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        double d1 = OpUtils.getNumber(vm, objs[0]);
        double d2 = OpUtils.getNumber(vm, objs[1]);
        if (d1 < d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void gt(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        double d1 = OpUtils.getNumber(vm, objs[0]);
        double d2 = OpUtils.getNumber(vm, objs[1]);
        if (d1 > d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void ge(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        double d1 = OpUtils.getNumber(vm, objs[0]);
        double d2 = OpUtils.getNumber(vm, objs[1]);
        if (d1 >= d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void le(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        double d1 = OpUtils.getNumber(vm, objs[0]);
        double d2 = OpUtils.getNumber(vm, objs[1]);
        if (d1 <= d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void inc(VirtualMachine vm, String param, String param_extend) {
        OpUtils.addEax(vm, 1, false);
    }

    public static void dec(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        OpUtils.addEax(vm, -1, false);
    }

    public static void post_inc(VirtualMachine vm, String param, String param_extend) {
        OpUtils.addEax(vm, 1, true);
    }

    public static void post_dec(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        OpUtils.addEax(vm, -1, true);
    }

    public static void pop_eax(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        vm.eax.write(vm.stack.pop());
    }

    public static void pop_env(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        vm.env.write((Environment) vm.stack.pop());
    }

    public static void pop(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        vm.stack.pop();
    }

    public static void jmp(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        if ("offset".equals(param)) {
            int nip = (Integer) vm.eax.read();
            vm.eip = nip + Integer.parseInt(param_extend);
        } else {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.eip = nip;
            } else {
                throw new VmRuntimeException(vm, "jump to no where " + param);
            }
        }
    }

    public static void jz(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object testObj = vm.eax.read();
        if (!OpUtils.testValue(testObj)) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.eip = nip;
            } else {
                throw new VmRuntimeException(vm, "jump to no where");
            }
        }
    }

    public static void jnz(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object testObj = vm.eax.read();
        if (OpUtils.testValue(testObj)) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.eip = nip;
            } else {
                throw new VmRuntimeException(vm, "jump to no where");
            }
        }
    }

    public static void label(VirtualMachine vm, String param, String param_extend) {
        //
    }

    public static void new_closure(VirtualMachine vm, String param, String param_extend) {
        FunctionDef closure = new FunctionDef(param, (Environment) vm.env.read(), vm);
        vm.eax.write(closure);
    }

    public static void integer(VirtualMachine vm, String param, String param_extend) {
        vm.eax.write(Integer.parseInt(param));
    }

    public static void double_(VirtualMachine vm, String param, String param_extend) {
        vm.eax.write(Double.parseDouble(param));
    }

    public static void undefined(VirtualMachine vm, String param, String param_extend) {
        vm.eax.write(undefined.value);
    }

    public static void string(VirtualMachine vm, String param, String param_extend) {
        String str = param;
        if (str.startsWith("\"") && str.endsWith("\"")) {
            //quote string
            str = str.substring(1, str.length() - 1);
        }
        str = str.replace("\\n", "\n");
        vm.eax.write(str);
    }

    public static void pickarg(VirtualMachine vm, String param, String param_extend) {
        int offset = Integer.parseInt(param);
        Object[] args = (Object[]) vm.stack.peek(3);
        if (offset < args.length) {
            vm.eax.write(args[offset]);
        } else {
            vm.eax.write(undefined.value);
        }
    }

    public static void peek(VirtualMachine vm, String param, String param_extend) {
        int offset = Integer.parseInt(param);
        Object obj = vm.stack.peek(offset);
        vm.eax.write(obj);

    }

    public static void clear_call_stack(VirtualMachine vm, String param, String param_extend) {
        while (vm.callstack.size() > vm.call_stack_size) {
            vm.callstack.pop();
        }
    }

    public static void ret(VirtualMachine vm, String param, String param_extend) {
        int nip = (Integer) vm.stack.pop();
        vm.callstack.pop();
        vm.eip = nip;
    }

    public static void add(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_ADD, objs[0], objs[1]));
    }

    public static void bit_and(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.getInteger(vm, objs[0]) & OpUtils.getInteger(vm, objs[1]));
    }

    public static void bit_or(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.getInteger(vm, objs[0]) | OpUtils.getInteger(vm, objs[1]));
    }

    public static void bit_xor(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.getInteger(vm, objs[0]) ^ OpUtils.getInteger(vm, objs[1]));
    }

    public static void sub(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_SUB, objs[0], objs[1]));
    }

    public static void mul(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_MUL, objs[0], objs[1]));
    }

    public static void mod(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_MOD, objs[0], objs[1]));
    }

    public static void div(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_DIV, objs[0], objs[1]));
    }

    public static void eq(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        //runtime.eax.value=(new TypeOp(runtime).eval("eq", objs[0], objs[1]));
        vm.eax.write(OpUtils.testEq(objs[0], objs[1]));
    }

    public static void veq(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.testEq(objs[0], objs[1]));
    }

    public static void neq(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(!OpUtils.testEq(objs[0], objs[1]));
    }

    public static void vneq(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(!OpUtils.testEq(objs[0], objs[1]));
    }
//    public static void coroutine_return(VirtualMachine runtime, String param, String paramExtended) {
//        Coroutine nativeClosure = (Coroutine) runtime.stack.get(runtime.stack.size() - 1 - 1);
//        nativeClosure.returned();
//        nativeClosure.switchBack();
//    }

    public static void packargs(VirtualMachine vm, String param, String param_extend) {
        int n = Integer.parseInt(param);
        Object[] args = new Object[n];
        for (int i = 0; i < n; i++) {
            args[i] = vm.stack.pop();
        }
        vm.eax.write(args);
    }

    public static void call(VirtualMachine vm, String param, String param_extend) throws Exception {
        Object callee = vm.stack.peek(1);
        Object[] args = (Object[]) vm.stack.peek(2);

        if (callee instanceof FunctionDef) {
            FunctionDef c = (FunctionDef) callee;
            vm.callstack.push(c);
            //prepare env
            Environment env = new Environment((Environment) c.environment);
            vm.env.write(env);
            int nip = vm.labels.get(c.functionLabel);
            vm.stack.push(vm.eip);
            vm.eip = nip;
            return;
        }

        //call native things
        if (callee instanceof VmMethod) {
            try {
                ((VmMethod) callee).invoke(vm, args);
            } catch (Exception ex) {
                Logger.getLogger(VirtualMachine.class.getName()).log(Level.SEVERE, null, ex);
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
                    if (TypeCheck.typeAcceptable(types, cons.getParameterTypes())) {
                        properCons = cons;
                        break;
                    }
                }
            }
            if (properCons == null) {
                throw new VmRuntimeException(vm, "no proper constructor found for " + Arrays.toString(types));
            }
            //adjust args
            if (properCons.isVarArgs()) {
                Class[] argTypes = properCons.getParameterTypes();
                int nonVarsCount = argTypes.length - 1;
                Class varElemType = argTypes[argTypes.length - 1].getComponentType();
                Object[] newargs = new Object[nonVarsCount + 1];
                Object[] varargs = new Object[args.length - nonVarsCount];
                System.arraycopy(args, nonVarsCount, varargs, 0, varargs.length);
                System.arraycopy(args, 0, newargs, 0, newargs.length);
                newargs[newargs.length - 1] = varargs;
                args = newargs;
            }
            //
            //prepare env with an instance
            vm.stack.push(properCons.newInstance(args));
            vm.env.write(null);
            return;
        }
        throw new VmRuntimeException(vm, "unexpected callee");
    }

    public static void save_machine_state(VirtualMachine vm, String param, String param_extend) {
        Context ms = new Context(vm);
        vm.machine_state_stack.push(ms);
    }

    public static void drop_machine_state(VirtualMachine vm, String param, String param_extend) {
        vm.machine_state_stack.pop();
    }

    public static void restore_machine_state(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        if (vm.machine_state_stack.isEmpty()) {
            Object ex = vm.exception.read();
            if (ex instanceof VmRuntimeException) {
                throw (VmRuntimeException) ex;
            } else {
                throw new VmRuntimeException(vm, ex.toString());
            }
        } else {
            Context ms = vm.machine_state_stack.pop();
            ms.restore(vm);
        }
    }

    public static void test(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        VmMemRef m = vm.lookup(param);
        if (m.read() == null) {
            vm.eax.write(0);
        } else {
            vm.eax.write(1);
        }
    }

    public static void clear(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        VmMemRef m = vm.lookup(param);
        m.write(undefined.value);
    }

    public static void clear_null(VirtualMachine vm, String param, String param_extend) throws VmRuntimeException {
        VmMemRef m = vm.lookup(param);
        m.write(null);
    }

    public static void new_op(VirtualMachine vm, String param, String param_extend) {
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

}