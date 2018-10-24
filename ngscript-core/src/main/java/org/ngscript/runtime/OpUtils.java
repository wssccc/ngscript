package org.ngscript.runtime;

import org.ngscript.runtime.vo.VmMemRef;
import org.ngscript.runtime.vo.undefined;

/**
 * @author wssccc
 */
class OpUtils {

    static Object[] get2OpParam(VirtualMachine vm) {
        Object[] objects = new Object[2];
        objects[0] = vm.stack.pop();
        objects[1] = vm.eax.read();
        return objects;
    }

    static double getNumber(VirtualMachine vm, Object obj) throws VmRuntimeException {
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
        throw new VmRuntimeException(vm, "invalid type");
    }

    static int getInteger(VirtualMachine vm, Object obj) throws VmRuntimeException {
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
        throw new VmRuntimeException(vm, "invalid type");
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
        } else {
            //is an object
            val = true;
        }
        return val;
    }

    static void addEax(VirtualMachine vm, int num, boolean rewrite) {
        VmMemRef addr = (VmMemRef) vm.eax.read();
        Object val = addr.read();
        if (val instanceof Integer) {
            addr.write(((Integer) val) + num);
        }
        if (val instanceof Double) {
            addr.write(((Double) val) + num);
        }
        if (rewrite) {
            vm.eax.write(val);
        } else {
            vm.eax.write(addr.read());
        }
    }

    static String getObjInfo(Object obj) {
        if (obj != null) {
            return obj.getClass().getName() + "[" + obj.toString() + "]";
        } else {
            return "null";
        }
    }
}
