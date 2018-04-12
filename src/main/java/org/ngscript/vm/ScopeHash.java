/*
 *  wssccc all rights reserved
 */
package org.ngscript.vm;

import org.ngscript.vm.structure.NativeClosure;
import org.ngscript.vm.structure.NativeMemref;
import org.ngscript.vm.structure.VmMemRef;
import org.ngscript.vm.structure.undefined;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class ScopeHash extends HashMap<String, VmMemRef> {

    HashMap<String, VmMemRef> cache = new HashMap<String, VmMemRef>();
    ScopeHash parent;

    public ScopeHash(ScopeHash parent) {
        this.parent = parent;
    }

    VmMemRef lookup(String member, WscVM vm) throws WscVMException {
        VmMemRef ref = cache.get(member);
        if (ref != null) {
            return ref;
        } else {
            ref = lookup(member, vm, false);
            cache.put(member, ref);
            return ref;
        }
    }

    VmMemRef lookup(String member, WscVM vm, boolean isMember) throws WscVMException {
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
        }
        //lookup hash
        return lookupHash(member, vm, isMember);
    }

    private VmMemRef lookupHash(String member, WscVM vm, boolean isMember) throws WscVMException {
        VmMemRef ref = this.get(member);
        if (ref != null) {
            return ref;
        }

        if (isMember) {
            //throw new Runtime-Exception("no member " + varName + " found.");
            VmMemRef mem = new VmMemRef(undefined.value);
            this.put(member, mem);
            return mem;
        } else {
            //find in env link
            ScopeHash env = parent;
            //
            while (env != null) {
                if (env.containsKey(member)) {
                    return env.get(member);
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
            throw new WscVMException(vm, member + " is not declared");
        }
    }

    public static Object lookupNative(Object nativeObj, String member, WscVM vm) {
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

    public static Object _lookupNative(Object nativeObj, Class cls, String member, WscVM vm) {
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
            return new VmMemRef(new NativeClosure(nativeObj, ms));
        }
        //throw new WscVMException(vm, "" + nativeObj + " does not have a member " + member);
        return null;
    }
}
