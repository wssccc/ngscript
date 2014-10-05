/*
 *  wssccc all rights reserved
 */
package ngscript.vm;

import java.lang.reflect.Array;
import ngscript.vm.structure.VmMemRef;
import ngscript.vm.structure.VmClosure;
import ngscript.vm.structure.NativeClosure;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import ngscript.vm.structure.BuiltinClosure;
import ngscript.vm.structure.undefined;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class VmCpu {

    public static void mov(WscVM vm, String param, String param_extend) throws WscVMException {

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

    public static void array_new(WscVM vm, String param, String param_extend) throws WscVMException {
        int length = Integer.parseInt(param);
        ScopeHash list = new ScopeHash((ScopeHash) vm.env.read());

        for (int i = 0; i < length; i++) {
            Object object = vm.stack.pop();
            list.put("" + i, new VmMemRef(object));
        }
        vm.eax.write(list);
    }

    public static void object_new(WscVM vm, String param, String param_extend) throws WscVMException {
        ScopeHash sh = new ScopeHash((ScopeHash) vm.env.read());
        int size = Integer.parseInt(param);
        for (int i = 0; i < size; i++) {
            Object v = vm.stack.pop();
            String k = (String) vm.stack.pop();
            VmMemRef vref = new VmMemRef(v);
            sh.put(k, vref);
        }
        vm.eax.write(sh);
    }

    public static void neg(WscVM vm, String param, String param_extend) throws WscVMException {
        Object eax = vm.eax.read();
        if (eax instanceof Integer) {
            vm.eax.write(-((Integer) eax));
        }
        if (eax instanceof Double) {
            vm.eax.write(-((Double) eax));
        }
    }

    public static void push(WscVM vm, String param, String param_extend) throws WscVMException {
        if (param.equals("%eax")) {
            vm.stack.push(vm.eax.read());
            return;
        }
        if (param.equals("%env")) {
            vm.stack.push(vm.env.read());
            return;
        }
        if (param.equals("%eip")) {
            vm.stack.push(vm.eip);
            return;
        }
        throw new WscVMException(vm, "unknown instruction push " + param + " " + param_extend);
    }

    public static void set_var(WscVM vm, String param, String param_extend) {
        ((ScopeHash) vm.env.read()).put(param, new VmMemRef(vm.eax.read()));
    }

    public static void member_ref(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        String member = (String) objs[1];
        if (objs[0] instanceof ScopeHash) {
            ScopeHash env = (ScopeHash) objs[0];
            VmMemRef ref = env.lookup(member, vm, true);
            vm.eax.write(ref);
            if (!(ref.read() instanceof undefined)) {
                return;
            }
        }
        Object ref = ScopeHash.lookupNative(objs[0], member, vm);

        if (ref != null) {
            vm.eax.write(ref);
            return;
        }
        throw new WscVMException(vm, member + " is not a member of " + _getObjInfo(objs[0]));
    }

    private static String _getObjInfo(Object obj) {
        if (obj != null) {
            return obj.getClass().getName() + "[" + obj.toString() + "]";
        } else {
            return "null";
        }
    }

    public static void array_ref(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        if (objs[0] instanceof ScopeHash) {
            ScopeHash env = (ScopeHash) objs[0];
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
        throw new WscVMException(vm, "not an array object");
    }

    public static void static_func(WscVM vm, String param, String param_extend) throws WscVMException {
        VmCpu.new_closure(vm, param_extend, null);
        VmCpu.set_var(vm, param, null);
    }

    public static void typeof(WscVM vm, String param, String param_extend) throws WscVMException {
        Object obj = vm.eax.read();
        if (obj.getClass().isAnonymousClass()) {
            vm.eax.write(obj.getClass().getSuperclass().getName());
        } else {
            if ("ngscript.vm.structure".equals(obj.getClass().getPackage().getName())) {
                vm.eax.write(obj.getClass().getSimpleName());
            } else {
                vm.eax.write(obj.getClass().getName());
            }
        }
    }

    public static void import_(WscVM vm, String param, String param_extend) throws WscVMException {
        String[] splitted = param.split("\\.");
        String shortName = splitted[splitted.length - 1];
        vm.imported.put(shortName, param);
    }

    public static void assign(WscVM vm, String param, String param_extend) {
        Object[] objs = get_op_params_2(vm);
        ((VmMemRef) objs[0]).write(objs[1]);
        vm.eax.write(objs[1]);
    }

    public static void lt(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        double d1 = getNumber(vm, objs[0]);
        double d2 = getNumber(vm, objs[1]);
        if (d1 < d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void gt(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        double d1 = getNumber(vm, objs[0]);
        double d2 = getNumber(vm, objs[1]);
        if (d1 > d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void ge(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        double d1 = getNumber(vm, objs[0]);
        double d2 = getNumber(vm, objs[1]);
        if (d1 >= d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    public static void le(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        double d1 = getNumber(vm, objs[0]);
        double d2 = getNumber(vm, objs[1]);
        if (d1 <= d2) {
            vm.eax.write(1);
        } else {
            vm.eax.write(0);
        }
    }

    private static void _addEaxObj(WscVM vm, int num, boolean retOrig) {
        VmMemRef addr = (VmMemRef) vm.eax.read();
        Object val = addr.read();
        if (val instanceof Integer) {
            addr.write(((Integer) val) + num);
        }
        if (val instanceof Double) {
            addr.write(((Double) val) + num);
        }
        if (retOrig) {
            vm.eax.write(val);
        } else {
            vm.eax.write(addr.read());
        }
    }

    public static void inc(WscVM vm, String param, String param_extend) {
        _addEaxObj(vm, 1, false);
    }

    public static void dec(WscVM vm, String param, String param_extend) throws WscVMException {
        _addEaxObj(vm, -1, false);
    }

    public static void post_inc(WscVM vm, String param, String param_extend) {
        _addEaxObj(vm, 1, true);
    }

    public static void post_dec(WscVM vm, String param, String param_extend) throws WscVMException {
        _addEaxObj(vm, -1, true);
    }

    public static void pop(WscVM vm, String param, String param_extend) throws WscVMException {
        if (param == null) {
            vm.stack.pop();
            return;
        }
        if (param.equals("%eax")) {
            vm.eax.write(vm.stack.pop());
            return;
        }
        if (param.equals("%env")) {
            vm.env.write(vm.stack.pop());
            return;
        }

        throw new WscVMException(vm, "unknown instruction push" + param + " " + param_extend);
    }

    public static void jmp(WscVM vm, String param, String param_extend) throws WscVMException {
        if ("offset".equals(param)) {
            int nip = (Integer) vm.eax.read();
            vm.eip = nip + Integer.parseInt(param_extend);
        } else {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.eip = nip;
            } else {
                throw new WscVMException(vm, "jump to no where " + param);
            }
        }
    }

    public static void jz(WscVM vm, String param, String param_extend) throws WscVMException {
        Object testObj = vm.eax.read();
        if (!testValue(testObj)) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.eip = nip;
            } else {
                throw new WscVMException(vm, "jump to no where");
            }
        }
    }

    public static void jnz(WscVM vm, String param, String param_extend) throws WscVMException {
        Object testObj = vm.eax.read();
        if (testValue(testObj)) {
            if (vm.labels.containsKey(param)) {
                int nip = vm.labels.get(param);
                vm.eip = nip;
            } else {
                throw new WscVMException(vm, "jump to no where");
            }
        }
    }

    static boolean testValue(Object testObj) {
        boolean val = false;
        if (testObj == null) {
            val = false;
        } else if (testObj instanceof Boolean) {
            val = ((Boolean) testObj);
        } else if (testObj instanceof Integer) {
            val = ((Integer) testObj) != 0;
        } else if (testObj instanceof Double) {
            val = Math.abs((Double) testObj) > Double.MIN_NORMAL;
        } else if (testObj instanceof undefined) {
            val = false;
        } else if (testObj instanceof Object) {
            val = true;
        }
        return val;
    }

    public static void label(WscVM vm, String param, String param_extend) {
        //
    }

    public static void new_closure(WscVM vm, String param, String param_extend) {
        VmClosure closure = new VmClosure(param, (ScopeHash) vm.env.read(), vm);
        vm.eax.write(closure);
    }

    public static void integer(WscVM vm, String param, String param_extend) {
        vm.eax.write(Integer.parseInt(param));
    }

    public static void double_(WscVM vm, String param, String param_extend) {
        vm.eax.write(Double.parseDouble(param));
    }

    public static void undefined(WscVM vm, String param, String param_extend) {
        vm.eax.write(undefined.value);
    }

    public static void string(WscVM vm, String param, String param_extend) {
        String str = param;
        if (str.startsWith("\"") && str.endsWith("\"")) {
            //quote string
            str = str.substring(1, str.length() - 1);
        }
        str = str.replace("\\n", "\n");
        vm.eax.write(str);
    }

    public static void dequeue(WscVM vm, String param, String param_extend) {
        int offset = Integer.parseInt(param_extend);
        LinkedList<Object> queue = (LinkedList<Object>) vm.stack.get(vm.stack.size() - 1 - offset);
        if (!queue.isEmpty()) {
            Object obj = queue.remove(0);
            vm.eax.write(obj);
        } else {
            vm.eax.write(undefined.value);
        }
    }

    public static void peek(WscVM vm, String param, String param_extend) {
        int offset = Integer.parseInt(param);
        Object obj = vm.stack.get(vm.stack.size() - 1 - offset);
        vm.eax.write(obj);

    }

    public static void clear_call_stack(WscVM vm, String param, String param_extend) {
        while (vm.callstack.size() > vm.call_stack_size) {
            vm.callstack.pop();
        }
    }

    public static void ret(WscVM vm, String param, String param_extend) {
        int nip = (Integer) vm.stack.pop();
        vm.callstack.pop();
        vm.eip = nip;
    }

    public static void add(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_ADD, objs[0], objs[1]));
    }

    public static void bit_and(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(getInteger(vm, objs[0]) & getInteger(vm, objs[1]));
    }

    public static void bit_or(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(getInteger(vm, objs[0]) | getInteger(vm, objs[1]));
    }

    public static void bit_xor(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(getInteger(vm, objs[0]) ^ getInteger(vm, objs[1]));
    }

    public static void sub(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_SUB, objs[0], objs[1]));
    }

    public static void mul(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_MUL, objs[0], objs[1]));
    }

    public static void mod(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_MOD, objs[0], objs[1]));
    }

    public static void div(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_DIV, objs[0], objs[1]));
    }

    static boolean testEq(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }
        return obj1.toString().equals(obj2.toString());
    }

    public static void eq(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        //vm.eax.write(new TypeOp(vm).eval("eq", objs[0], objs[1]));
        vm.eax.write(testEq(objs[0], objs[1]));
    }

    public static void veq(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(testEq(objs[0], objs[1]));
    }

    public static void neq(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(!testEq(objs[0], objs[1]));
    }

    public static void vneq(WscVM vm, String param, String param_extend) throws WscVMException {
        Object[] objs = get_op_params_2(vm);
        vm.eax.write(!testEq(objs[0], objs[1]));
    }
//    public static void coroutine_return(WscVM vm, String param, String param_extend) {
//        Coroutine nativeClosure = (Coroutine) vm.stack.get(vm.stack.size() - 1 - 1);
//        nativeClosure.returned();
//        nativeClosure.switchBack();
//    }

    public static void new_queue(WscVM vm, String param, String param_extend) {
        int n = Integer.parseInt(param);
        LinkedList<Object> vq = new LinkedList<Object>();
        for (int i = 0; i < n; i++) {
            vq.add(vm.stack.remove(vm.stack.size() - n + i));
        }
        vm.eax.write(vq);
    }

    public static void call(WscVM vm, String param, String param_extend) throws Exception {
        Object callee = vm.stack.get(vm.stack.size() - 1 - 1);
        LinkedList<Object> vars = (LinkedList<Object>) vm.stack.get(vm.stack.size() - 1 - 2);

        if (callee instanceof VmClosure) {
            VmClosure c = (VmClosure) callee;
            vm.callstack.push(c);
            //prepare env
            ScopeHash env = new ScopeHash((ScopeHash) c.closure_env);
            vm.env.write(env);
            int nip = vm.labels.get(c.func_label);
            vm.stack.push(vm.eip);
            vm.eip = nip;
            return;
        }

        //call native things
        if (callee instanceof BuiltinClosure) {
            try {
                ((BuiltinClosure) callee).invoke(vm, vars);
            } catch (Exception ex) {
                Logger.getLogger(WscVM.class.getName()).log(Level.SEVERE, null, ex);
                Object e = ex.getCause();
                if (e == null) {
                    e = ex;
                }
                vm.exception.write(e);
                restore_machine_state(vm, null, null);
            }
            return;
        }
        if (callee instanceof NativeClosure) {
            NativeClosure closure = (NativeClosure) callee;
            Class[] types = vm.getParamTypes(2);
            ArrayList<Method> methods = closure.methods;
            Method properMethod = null;
            if (methods.size() == 1) {
                properMethod = methods.get(0);
            } else {
                for (Method m : methods) {
                    if (TypeCheck.typeAcceptable(types, m.getParameterTypes())) {
                        properMethod = m;
                        break;
                    }
                }
            }
            if (properMethod == null) {
                throw new WscVMException(vm, "no proper method found for " + closure.methods + "[" + Arrays.toString(types) + "]");
            }
            try {
                Object val = properMethod.invoke(closure.caller, vars.toArray());
                if (val instanceof Long) {
                    val = ((Long) val).intValue();
                }
                vm.eax.write(val);
            } catch (Exception ex) {
                vm.exception.write(new WscVMException(vm, ex.getCause() == null ? ex.toString() : ex.getCause().toString()));
                restore_machine_state(vm, null, null);
            }
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
                throw new WscVMException(vm, "no proper constructor found for " + Arrays.toString(types));
            }
            //adjust vars
            if (properCons.isVarArgs()) {
                Class[] argTypes = properCons.getParameterTypes();
                int fixed = argTypes.length - 1;
                Class varElemType = argTypes[argTypes.length - 1].getComponentType();
                LinkedList pack = new LinkedList();
                while (vars.size() > fixed) {
                    pack.addFirst(vars.removeLast());
                }
                Object vararg = Array.newInstance(varElemType, pack.size());
                pack.toArray((Object[]) vararg);
                vars.add(vararg);
            }
            //
            //prepare env with an instance
            vm.env.write(properCons.newInstance(vars.toArray()));
            return;
        }
        throw new WscVMException(vm, "unexpected callee");
    }

    public static void save_machine_state(WscVM vm, String param, String param_extend) {
        Context ms = new Context(vm);
        vm.machine_state_stack.push(ms);
    }

    public static void drop_machine_state(WscVM vm, String param, String param_extend) {
        vm.machine_state_stack.pop();
    }

    public static void restore_machine_state(WscVM vm, String param, String param_extend) throws WscVMException {
        if (vm.machine_state_stack.isEmpty()) {
            Object ex = vm.exception.read();
            if (ex instanceof WscVMException) {
                throw (WscVMException) ex;
            } else {
                throw new WscVMException(vm, ex.toString());
            }
        } else {
            Context ms = vm.machine_state_stack.pop();
            ms.restore(vm);
        }
    }

    public static void test(WscVM vm, String param, String param_extend) throws WscVMException {
        VmMemRef m = vm.lookup(param);
        if (m.read() == null) {
            vm.eax.write(0);
        } else {
            vm.eax.write(1);
        }
    }

    public static void clear(WscVM vm, String param, String param_extend) throws WscVMException {
        VmMemRef m = vm.lookup(param);
        m.write(undefined.value);
    }

    public static void clear_null(WscVM vm, String param, String param_extend) throws WscVMException {
        VmMemRef m = vm.lookup(param);
        m.write(null);
    }

    public static void new_op(WscVM vm, String param, String param_extend) {
        //identify natice object and script object
        Object envobj = vm.env.read();
        ScopeHash old_env = (ScopeHash) vm.stack.pop();
        Object cons = vm.stack.pop();

        vm.eax.write(envobj);

        vm.env.write(old_env);
        vm.stack.pop(); //params
    }

    //helper functions
    static Object[] get_op_params_2(WscVM vm) {
        Object[] objs = new Object[2];
        objs[0] = vm.stack.pop();
        objs[1] = vm.eax.read();
        return objs;
    }

    static double getNumber(WscVM vm, Object obj) throws WscVMException {
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        if (obj instanceof Double) {
            return (Double) obj;
        }
        if (obj instanceof Long) {
            Long l = (Long) obj;
            return l.intValue();
        }
        throw new WscVMException(vm, "invalid type");
    }

    static int getInteger(WscVM vm, Object obj) throws WscVMException {
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        if (obj instanceof Double) {
            return ((Double) obj).intValue();
        }
        if (obj instanceof Long) {
            Long l = (Long) obj;
            return l.intValue();
        }
        throw new WscVMException(vm, "invalid type");
    }
}
