/*
 *  wssccc all rights reserved
 */
package org.ngscript.runtime;

import org.ngscript.runtime.vo.JavaMethod;
import org.ngscript.runtime.vo.NativeMemref;
import org.ngscript.runtime.vo.VmMemRef;
import org.ngscript.runtime.vo.undefined;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class Environment {

    HashMap<String, VmMemRef> data = new HashMap<>();
    Environment parent;

    public Environment(Environment parent) {
        this.parent = parent;
    }

    public VmMemRef lookup(String member, VirtualMachine vm, boolean isMember) throws VmRuntimeException {
        //registers
        switch (member) {
            case "this":
                return vm.env;
            case "%eax":
                return vm.eax;
            case "%env":
                return vm.env;
            case "%exception":
                return vm.exception;
            default:
                VmMemRef ref = data.get(member);
                if (ref != null) {
                    return ref;
                } else {
                    ref = lookupHash(member, vm, isMember);
                    data.put(member, ref);
                    return ref;
                }
        }
    }

    private VmMemRef lookupHash(String member, VirtualMachine vm, boolean isMember) throws VmRuntimeException {
        if (isMember) {
            //throw new Runtime-Exception("no member " + varName + " found.");
            VmMemRef mem = new VmMemRef(undefined.value);
            data.put(member, mem);
            return mem;
        } else {
            //find in env link
            Environment env = parent;
            //
            while (env != null) {
                if (env.data.containsKey(member)) {
                    return env.data.get(member);
                } else {
                    env = env.parent;
                }
            }
            //try java.util
            try {
                Class cls = Class.forName("java.util." + member);
                return new VmMemRef(cls);
            } catch (ClassNotFoundException ex) {
                //nothing happend...
            }
            //try java.lang
            try {
                Class cls = Class.forName("java.lang." + member);
                return new VmMemRef(cls);
            } catch (ClassNotFoundException ex) {
                //nothing happend...
            }
            //try java.io
            try {
                Class cls = Class.forName("java.io." + member);
                return new VmMemRef(cls);
            } catch (ClassNotFoundException ex) {
                //nothing happend...
            }
            //imports
            //try import
            if (vm.imported.containsKey(member)) {
                try {
                    Class cls = Class.forName(vm.imported.get(member));
                    return new VmMemRef(cls);
                } catch (ClassNotFoundException ex) {
                    //nothing happend...
                }
            }

            //try java.lang
            try {
                Class cls = Class.forName("java.lang." + member);
                return new VmMemRef(cls);
            } catch (ClassNotFoundException ex) {

            }
            throw new VmRuntimeException(vm, member + " is not declared");
        }
    }

    public static Object lookupNative(Object nativeObj, String member, VirtualMachine vm) {
        if (nativeObj instanceof Class) {
            //try obj as a class ref
            Object obj = _lookupNative(nativeObj, (Class) nativeObj, member, vm);
            if (obj != null) {
                return obj;
            }
        }
        //regards obj as an Object
        return _lookupNative(nativeObj, nativeObj.getClass(), member, vm);

    }

    public static Object _lookupNative(Object nativeObj, Class cls, String member, VirtualMachine vm) {
        //try field
        try {
            Field field = cls.getField(member);
            return new NativeMemref(nativeObj, field);
        } catch (NoSuchFieldException ex) {
        }
        //try method

        Method[] methods = cls.getMethods();

        ArrayList<Method> ms = new ArrayList<Method>();
        for (Method m : methods) {
            if (m.getName().equals(member)) {
                ms.add(m);
            }
        }
        if (!ms.isEmpty()) {
            return new VmMemRef(new JavaMethod(nativeObj, ms));
        }
        //throw new VmRuntimeException(runtime, "" + nativeObj + " does not have a member " + member);
        return null;
    }
}
