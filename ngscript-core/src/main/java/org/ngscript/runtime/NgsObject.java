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

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.ngscript.builtins.Builtins;
import org.ngscript.interop.JavaFieldRef;
import org.ngscript.interop.JavaMethod;
import org.ngscript.value.MemRef;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;

/**
 * A script object and lexical scope: a chainable map of member references.
 * Function and constructor frames are NgsObjects as well, so the instance
 * created by {@code new} is the constructor's active frame itself (invariant
 * preserved by {@code new_op}).
 *
 * @author wssccc
 */
public class NgsObject implements Scope {

    private Map<String, MemRef> data = new HashMap<>();
    private NgsObject parent;

    /**
     * The [[Prototype]] chain for property access (distinct from the lexical
     * parent chain used for variable lookup).
     */
    private NgsObject proto;

    /**
     * Accessor properties (Object.defineProperty with get/set): name to the
     * getter/setter functions. Reads and writes consult these before the
     * plain data map.
     */
    private java.util.Map<String, Object[]> accessors;

    public Object getAccessor(String name, String which) {
        if (accessors == null) {
            return null;
        }
        Object[] pair = accessors.get(name);
        return pair == null ? null : ("get".equals(which) ? pair[0] : pair[1]);
    }

    public void defineAccessor(String name, Object getter, Object setter) {
        if (accessors == null) {
            accessors = new java.util.HashMap<>();
        }
        accessors.put(name, new Object[]{getter, setter});
        // an accessor shadows the plain data slot
        data.remove(name);
    }

    /**
     * Finds an accessor property for {@code member} on this object or its
     * prototype chain. Returns {getter, setter, owner} or null.
     */
    private Object[] findAccessorOnChain(String member) {
        NgsObject cur = this;
        while (cur != null) {
            if (cur.accessors != null && cur.accessors.containsKey(member)) {
                Object[] pair = cur.accessors.get(member);
                return new Object[]{pair[0], pair[1], cur};
            }
            cur = cur.proto;
        }
        return null;
    }

    /** Invokes an accessor getter with this object as the receiver. */
    private Object invokeAccessor(Object getter, NgsObject owner) {
        try {
            return org.ngscript.runtime.OpUtils.callValue(
                    Machine.getCurrent(), getter, owner, new Object[0]);
        } catch (Exception e) {
            throw e instanceof RuntimeException
                    ? (RuntimeException) e
                    : new RuntimeException(e.getMessage() == null
                            ? e.toString() : e.getMessage());
        }
    }

    /** Invokes an accessor setter with this object as the receiver. */
    private void invokeAccessorWrite(Object setter, NgsObject owner, Object value, Machine vm) {
        try {
            org.ngscript.runtime.OpUtils.callValue(vm, setter, owner, new Object[]{value});
        } catch (Exception e) {
            throw e instanceof RuntimeException
                    ? (RuntimeException) e
                    : new RuntimeException(e.getMessage() == null
                            ? e.toString() : e.getMessage());
        }
    }

    /** Writes through an accessor's setter (called from the lookup MemRef). */
    private void setAccessor(String name, Object value, Machine vm) {
        Object setter = getAccessor(name, "set");
        if (setter != null) {
            try {
                org.ngscript.runtime.OpUtils.callValue(vm, setter, this, new Object[]{value});
            } catch (Exception e) {
                throw e instanceof RuntimeException
                        ? (RuntimeException) e
                        : new RuntimeException(e.getMessage() == null
                                ? e.toString() : e.getMessage());
            }
        }
        // a getter-only accessor ignores writes; a plain setter stores nothing
        // (the setter's side effects are the property's behaviour)
    }

    /**
     * The constructor function that created this object via {@code new},
     * used by the minimal instanceof support. Null for plain objects.
     */
    private ScriptFunction constructor;

    /**
     * Whether this frame is a function scope: {@code var} declarations and
     * parameters bind to the nearest enclosing frame with this flag set
     * (block frames introduced by let/const carry no flag).
     */
    public boolean functionScope;

    public NgsObject(NgsObject parent) {
        this.parent = parent;
    }

    /**
     * Snapshot of the own data map (used by the test runner to isolate
     * shared globals between cases: {@code eval = f} style assignments
     * must not leak across cases).
     */
    public java.util.Map<String, MemRef> snapshotData() {
        return new HashMap<>(data);
    }

    /** Restores the own data map from a snapshot taken by {@link #snapshotData}. */
    public void restoreData(java.util.Map<String, MemRef> snapshot) {
        // deep-copy the values: a case may have written through a shared
        // MemRef (e.g. `eval = f` on the global object), which a shallow
        // map copy would keep polluted
        java.util.Map<String, MemRef> fresh = new HashMap<>();
        for (java.util.Map.Entry<String, MemRef> e : snapshot.entrySet()) {
            MemRef ref = e.getValue();
            fresh.put(e.getKey(), new MemRef(ref.read(), ref.isImmutable()));
        }
        data = fresh;
    }

    public NgsObject getPrototype() {
        return proto;
    }

    public void setPrototype(NgsObject proto) {
        this.proto = proto;
    }

    public ScriptFunction getConstructor() {
        return constructor;
    }

    public void setConstructor(ScriptFunction constructor) {
        this.constructor = constructor;
    }

    /**
     * Whether the constructor's prototype appears on this object's
     * prototype chain (ES instanceof semantics).
     */
    public boolean isInstanceOf(ScriptFunction ctor) {
        NgsObject target = ctor.prototype;
        if (target == null) {
            return false;
        }
        NgsObject p = proto;
        while (p != null) {
            if (p == target) {
                return true;
            }
            p = p.proto;
        }
        return false;
    }

    /**
     * Removes an own property; returns false when it did not exist.
     */
    public boolean remove(String key) {
        return data.remove(key) != null;
    }

    /**
     * Whether an own property exists (data or accessor).
     */
    public boolean has(String key) {
        if (accessors != null && accessors.containsKey(key)) {
            return true;
        }
        return data.containsKey(key);
    }

    /**
     * Looks up a member: registers, own data, the parent chain, then Java
     * class fallbacks. With {@code isMember=true} an unset member is created
     * as an undefined reference on this object; variable lookups create the
     * binding in the root (global) frame instead (sloppy-mode implicit
     * globals).
     */
    public MemRef lookup(String member, Machine vm, boolean isMember) throws NgScriptRuntimeException {
        //registers
        switch (member) {
            case "this":
                return vm.thisRef;
            case "%eax":
                return vm.eax;
            case "%env":
                return vm.env;
            case "%exception":
                return vm.exception;
            default:
                // accessor properties live on the prototype chain too:
                // { get v() } on a class prototype makes obj.v go through
                // the getter (and obj.v = x through the setter) for any
                // instance without an own data property
                Object[] chainAccessor = findAccessorOnChain(member);
                if (chainAccessor != null) {
                    final NgsObject owner = chainAccessor[2] instanceof NgsObject
                            ? (NgsObject) chainAccessor[2] : this;
                    final Object[] acc = chainAccessor;
                    return new MemRef(acc[0] != null ? invokeAccessor(acc[0], owner) : Undefined.value) {
                        @Override
                        public void write(Object v) {
                            if (acc[1] != null) {
                                invokeAccessorWrite(acc[1], owner, v, vm);
                            }
                        }
                    };
                }
                MemRef ref = data.get(member);
                if (ref != null) {
                    return ref;
                } else {
                    ref = lookupEnv(member, vm, isMember);
                    if (ref == null) {
                        // an unset member read as undefined (kept from the legacy
                        // behavior: reading a member creates the reference).
                        // A found binding is returned as-is: copying it into
                        // the root frame would leak local bindings into the
                        // global object (and shadow them there forever)
                        ref = new MemRef(Undefined.value);
                        if (isMember) {
                            data.put(member, ref);
                        } else {
                            rootFrame().data.put(member, ref);
                        }
                    }
                    return ref;
                }
        }
    }

    /**
     * The outermost frame of the environment chain (the implicit global).
     */
    public NgsObject rootFrame() {
        NgsObject p = this;
        while (p.parent != null) {
            p = p.parent;
        }
        return p;
    }

    /**
     * Binds a {@code var} declaration (or a parameter): the binding goes to
     * the nearest enclosing function scope, skipping any block frames.
     */
    public void setVar(String name, Object value) {
        NgsObject f = this;
        while (f != null && !f.functionScope) {
            f = f.parent;
        }
        if (f == null) {
            f = this;
        }
        f.data.put(name, value instanceof MemRef ? (MemRef) value : new MemRef(value));
    }

    /**
     * Stores a raw binding reference (used by let/const declarations, which
     * install {@link org.ngscript.value.LetBinding} references).
     */
    public void putRef(String name, MemRef ref) {
        data.put(name, ref);
    }

    private MemRef lookupEnv(String member, Machine vm, boolean isMember) throws NgScriptRuntimeException {
        if (isMember) {
            // property access: own data, then the [[Prototype]] chain
            NgsObject p = proto;
            while (p != null) {
                MemRef ref = p.data.get(member);
                if (ref != null) {
                    return ref;
                }
                p = p.proto;
            }
            return null;
        } else {
            //find in env link
            NgsObject env = parent;
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
                return new MemRef(cls);
            } catch (ClassNotFoundException ignored) {
                //ignored
            }
            //try java.lang
            try {
                Class cls = Class.forName("java.lang." + member);
                return new MemRef(cls);
            } catch (ClassNotFoundException ignored) {
                //ignored
            }
            //try java.io
            try {
                Class cls = Class.forName("java.io." + member);
                return new MemRef(cls);
            } catch (ClassNotFoundException ignored) {
                //ignored
            }
            //imports
            if (vm.imported.containsKey(member)) {
                try {
                    Class cls = Class.forName(vm.imported.get(member));
                    return new MemRef(cls);
                } catch (ClassNotFoundException ignored) {
                    //ignored
                }
            }

            //try java.lang
            try {
                Class cls = Class.forName("java.lang." + member);
                return new MemRef(cls);
            } catch (ClassNotFoundException ignored) {
                //ignored
            }
            throw new NgScriptRuntimeException(vm, member + " is not declared");
        }
    }

    /** Returns the value of a member, or {@code null} if unset. */
    public Object get(String member) {
        Object g = getAccessor(member, "get");
        if (g != null) {
            try {
                return org.ngscript.runtime.OpUtils.callValue(
                        Machine.getCurrent(), g, this, new Object[0]);
            } catch (Exception e) {
                throw e instanceof RuntimeException
                        ? (RuntimeException) e
                        : new RuntimeException(e.getMessage() == null
                                ? e.toString() : e.getMessage());
            }
        }
        MemRef ref = data.get(member);
        return ref == null ? null : ref.read();
    }

    /**
     * Returns the raw reference stored for a member, or {@code null} if
     * unset. Unlike {@link #get} this does not unwrap the reference.
     */
    public MemRef getRef(String member) {
        if (accessors != null && accessors.containsKey(member)) {
            return new MemRef(get(member));
        }
        return data.get(member);
    }

    /** Sets a member to a fresh reference holding the given value. */
    public void set(String member, Object value) {
        // a MemRef value is stored as-is so callers can install a shared
        // reference (mapped arguments, dynamic references)
        data.put(member, value instanceof MemRef ? (MemRef) value : new MemRef(value));
    }

    /** Creates a member reference without a value. */
    public void define(String member) {
        data.put(member, new MemRef(null, true));
    }

    /** Returns the member names of this scope (not the parent chain). */
    public java.util.Set<String> keys() {
        return data.keySet();
    }

    public NgsObject getParent() {
        return parent;
    }

    /**
     * Registers all built-in functions and objects into this scope, as done
     * for the machine's baseline environment at construction time.
     */
    public void registerBuiltins(PrintWriter out) {
        Builtins.register(data, out);
    }

    public static Object lookupNative(Object nativeObj, String member, Machine vm) {
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

    public static Object _lookupNative(Object nativeObj, Class cls, String member, Machine vm) {
        //try field
        try {
            Field field = cls.getField(member);
            return new JavaFieldRef(nativeObj, field);
        } catch (NoSuchFieldException ex) {
        }
        //try method

        Method[] methods = cls.getMethods();
        ArrayList<Method> ms = new ArrayList<>();
        for (Method method : methods) {
            if (method.getName().equals(member)) {
                ms.add(method);
            }
        }
        if (!ms.isEmpty()) {
            return new MemRef(new JavaMethod(nativeObj, ms));
        }
        return null;
    }
}
