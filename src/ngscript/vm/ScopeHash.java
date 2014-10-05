/*
 *  wssccc all rights reserved
 */
package ngscript.vm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import ngscript.vm.structure.NativeClosure;
import ngscript.vm.structure.NativeMemref;
import ngscript.vm.structure.VmMemRef;
import ngscript.vm.structure.undefined;

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
        if (cache.containsKey(member)) {
            return cache.get(member);
        } else {
            VmMemRef ref = lookup(member, vm, false);
            cache.put(member, ref);
            return ref;
        }
    }

    VmMemRef lookup(String member, WscVM vm, boolean isMember) throws WscVMException {
        //registers
        if (member.equals("this")) {
            return vm.env;
        }
        if (member.equals("%eax")) {
            return vm.eax;
        }
        if (member.equals("%env")) {
            return vm.env;
        }
        if (member.equals("%exception")) {
            return vm.exception;
        }
        //lookup hash
        return lookupHash(member, vm, isMember);
    }

    private VmMemRef lookupHash(String member, WscVM vm, boolean isMember) throws WscVMException {
        if (this.containsKey(member)) {
            return this.get(member);
        } else {
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
