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
     * Property names made non-configurable via Object.defineProperty
     * ({configurable: false}): delete returns false for them.
     */
    private java.util.Set<String> nonConfigurable;

    /**
     * Property names made non-writable via Object.defineProperty
     * ({writable: false}): script writes are silently ignored (sloppy).
     */
    private java.util.Set<String> nonWritable;

    /**
     * Private-method keys: ES PrivateSet throws a TypeError on assignment
     * (a private method cannot be written through a compound assignment).
     */
    private java.util.Set<String> writeThrows;

    /**
     * Property names made non-enumerable via Object.defineProperty
     * ({enumerable: false}): Object.keys, for-in and object-rest skip
     * them. Literal properties default to enumerable.
     */
    private java.util.Set<String> nonEnumerable;

    /** Marks an own property as non-enumerable. */
    public void markNonEnumerable(String name) {
        if (nonEnumerable == null) {
            nonEnumerable = new java.util.HashSet<>();
        }
        nonEnumerable.add(name);
    }

    /** Whether an own property is enumerable (default true). */
    public boolean isEnumerable(String name) {
        return nonEnumerable == null || !nonEnumerable.contains(name);
    }

    /** Marks an own property as non-configurable (delete returns false). */
    public void markNonConfigurable(String name) {
        if (nonConfigurable == null) {
            nonConfigurable = new java.util.HashSet<>();
        }
        nonConfigurable.add(name);
    }

    /** Whether an own property was marked non-configurable. */
    public boolean isNonConfigurable(String name) {
        return nonConfigurable != null && nonConfigurable.contains(name);
    }

    /** Marks an own property as non-writable (writes are ignored). */
    public void markNonWritable(String name) {
        if (nonWritable == null) {
            nonWritable = new java.util.HashSet<>();
        }
        nonWritable.add(name);
    }

    /** Whether a script write to this own property is ignored. */
    public boolean isNonWritable(String name) {
        return nonWritable != null && nonWritable.contains(name);
    }

    public void markWriteThrows(String name) {
        if (writeThrows == null) {
            writeThrows = new java.util.HashSet<>();
        }
        writeThrows.add(name);
    }

    public boolean isWriteThrows(String name) {
        return writeThrows != null && writeThrows.contains(name);
    }

    /** Whether a write to {@code member} throws anywhere on the prototype chain. */
    private boolean writeThrowsOnChain(String member) {
        NgsObject p = this;
        while (p != null) {
            if (p.isWriteThrows(member)) {
                return true;
            }
            p = p.proto;
        }
        return false;
    }

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
     * must not leak across cases). Accessor properties are captured too
     * (a case that defines a getter on Object.prototype would otherwise
     * redirect every property lookup in later cases).
     */
    public java.util.Map<String, MemRef> snapshotData() {
        java.util.Map<String, MemRef> snap = new HashMap<>(data);
        if (accessors != null) {
            for (java.util.Map.Entry<String, Object[]> e : accessors.entrySet()) {
                snap.put("%accessor:" + e.getKey(), new MemRef(e.getValue()));
            }
        }
        return snap;
    }

    /** Restores the own data map from a snapshot taken by {@link #snapshotData}. */
    public void restoreData(java.util.Map<String, MemRef> snapshot) {
        // deep-copy the values: a case may have written through a shared
        // MemRef (e.g. `eval = f` on the global object), which a shallow
        // map copy would keep polluted
        java.util.Map<String, MemRef> fresh = new HashMap<>();
        java.util.Map<String, Object[]> freshAccessors = null;
        for (java.util.Map.Entry<String, MemRef> e : snapshot.entrySet()) {
            if (e.getKey().startsWith("%accessor:")) {
                MemRef ref = e.getValue();
                if (freshAccessors == null) {
                    freshAccessors = new java.util.LinkedHashMap<>();
                }
                freshAccessors.put(e.getKey().substring(10), (Object[]) ref.read());
                continue;
            }
            MemRef ref = e.getValue();
            fresh.put(e.getKey(), new MemRef(ref.read(), ref.isImmutable()));
        }
        data = fresh;
        accessors = freshAccessors;
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
     * Removes an own property (data or accessor); returns false when it did
     * not exist or when the property is non-configurable.
     */
    public boolean remove(String key) {
        if (nonConfigurable != null && nonConfigurable.contains(key)) {
            return false;
        }
        boolean removed = data.remove(key) != null;
        if (accessors != null && accessors.remove(key) != null) {
            removed = true;
        }
        return removed;
    }

    /**
     * Whether an own property exists (data or accessor).
     */
    public boolean has(String key) {
        if (isPrivateKey(key)) {
            // private members are not ordinary properties
            return false;
        }
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
                // an own accessor or data property shadows prototype
                // accessors: check own data first, then the own accessor,
                // then the [[Prototype]] chain (accessor or data)
                if (accessors != null && accessors.containsKey(member)) {
                    // own accessor: the getter/setter runs with this object
                    // as its this
                    return accessorRef(member, this, this, vm);
                }
                MemRef ref = data.get(member);
                if (ref != null) {
                    if (isWriteThrows(member)) {
                        // ES PrivateSet: writing a private method is a TypeError
                        final MemRef inner = ref;
                        final String key = member;
                        return new MemRef(inner.read()) {
                            @Override
                            public void write(Object v) {
                                throw new IllegalArgumentException(
                                        "Cannot write to private method '" + key + "'");
                            }
                        };
                    }
                    if (isNonWritable(member)) {
                        // non-writable property (defineProperty writable:
                        // false): script writes are silently ignored
                        // (sloppy mode); reads pass through
                        final MemRef inner = ref;
                        return new MemRef(inner.read()) {
                            @Override
                            public void write(Object v) {
                                // ignore the write
                            }
                        };
                    }
                    return ref;
                }
                Object[] chainAccessor = findAccessorOnChain(member);
                if (chainAccessor != null) {
                    // ES: a prototype getter/setter runs with the *accessing
                    // object* as this (obj.v reads through a prototype
                    // accessor with this=obj), not with the prototype that
                    // owns it
                    return accessorRef(member, this, (NgsObject) chainAccessor[2], vm);
                }
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
                } else if (isMember) {
                    // the reference lives on the [[Prototype]] chain (or
                    // an outer frame): a script write must create an own
                    // property here, not rewrite the inherited slot
                    // (obj.x = v must not mutate Object.prototype.x)
                    final MemRef inherited = ref;
                    final String key = member;
                    if (writeThrowsOnChain(member)) {
                        // ES PrivateSet: a private method cannot be assigned
                        return new MemRef(inherited.read()) {
                            @Override
                            public void write(Object v) {
                                throw new IllegalArgumentException(
                                        "Cannot write to private method '" + key + "'");
                            }
                        };
                    }
                    return new MemRef(inherited.read()) {
                        @Override
                        public void write(Object v) {
                            data.put(key, new MemRef(v));
                        }
                    };
                }
                return ref;
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
     * A reference through an accessor property. The getter is invoked at
     * read time with {@code receiver} as this; a write re-checks the
     * accessor: ES PutValue semantics — a getter that deleted its own
     * accessor ({@code delete this.x}) turns the write into a plain data
     * property creation, a still-present accessor routes through its setter
     * (getter-only properties ignore writes in sloppy mode).
     */
    private static MemRef accessorRef(final String member, final NgsObject receiver,
                                      final NgsObject owner, final Machine vm) {
        final Object[] acc = owner.accessors.get(member);
        Object read;
        try {
            read = acc[0] != null ? org.ngscript.runtime.OpUtils.callValue(
                    Machine.getCurrent(), acc[0], receiver, new Object[0])
                    : Undefined.value;
        } catch (Exception e) {
            throw e instanceof RuntimeException
                    ? (RuntimeException) e
                    : new RuntimeException(e.getMessage() == null
                            ? e.toString() : e.getMessage());
        }
        final Object initial = read;
        return new MemRef(initial) {
            @Override
            public void write(Object v) {
                if (owner.accessors != null && owner.accessors.containsKey(member)) {
                    Object[] cur = owner.accessors.get(member);
                    if (cur[1] != null) {
                        try {
                            org.ngscript.runtime.OpUtils.callValue(vm, cur[1], receiver,
                                    new Object[]{v});
                        } catch (Exception e) {
                            throw e instanceof RuntimeException
                                    ? (RuntimeException) e
                                    : new RuntimeException(e.getMessage() == null
                                            ? e.toString() : e.getMessage());
                        }
                    }
                } else {
                    receiver.data.put(member, new MemRef(v));
                }
            }
        };
    }

    /**
     * A {@code with (obj)} scope frame: variable lookups consult the
     * object's own data and its [[Prototype]] chain first, then fall
     * through to the outer environment. The frame itself stays empty, so
     * assignments to absent properties never write into the object.
     */
    public static class WithFrame extends NgsObject {

        private final NgsObject object;

        public WithFrame(NgsObject parent, NgsObject object) {
            super(parent);
            this.object = object;
        }

        @Override
        public MemRef lookup(String member, Machine vm, boolean isMember) throws NgScriptRuntimeException {
            NgsObject p = object;
            while (p != null) {
                if (p.accessors != null && p.accessors.containsKey(member)) {
                    // ES: the accessor runs with the with-object as this
                    // (the object environment record reads bindingObject.name)
                    return accessorRef(member, object, p, vm);
                }
                MemRef ref = p.getRef(member);
                if (ref != null) {
                    return ref;
                }
                p = p.getPrototype();
            }
            return super.lookup(member, vm, isMember);
        }
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

    /**
     * Reads a property by walking the own data and the [[Prototype]] chain
     * without triggering accessors or running any user code: used to format
     * thrown script objects into exception messages (an escaping exception
     * must not invoke getters as a side effect). Returns {@code null} when
     * no plain data binding exists.
     */
    public Object plainGet(String member) {
        NgsObject p = this;
        while (p != null) {
            MemRef ref = p.data.get(member);
            if (ref != null) {
                return ref.read();
            }
            p = p.proto;
        }
        return null;
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
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        for (String k : data.keySet()) {
            if (!isPrivateKey(k)) {
                all.add(k);
            }
        }
        if (accessors != null && !accessors.isEmpty()) {
            for (String k : accessors.keySet()) {
                if (!isPrivateKey(k)) {
                    all.add(k);
                }
            }
        }
        return esOrderedKeys(all);
    }

    /**
     * Private class members ({@code #x}) are stored as ordinary data keys
     * but must never surface as properties: ES private names live in a
     * separate private environment, invisible to hasOwnProperty, ownKeys,
     * Object.keys, for-in and delete.
     */
    private static boolean isPrivateKey(String k) {
        return k.length() > 0 && k.charAt(0) == '#';
    }

    /**
     * The enumerable own property names (ES [[OwnPropertyKeys]] filtered
     * by enumerability): used by Object.keys, for-in and object rest.
     */
    public java.util.Set<String> enumerableKeys() {
        java.util.Set<String> all = keys();
        if (nonEnumerable == null || nonEnumerable.isEmpty()) {
            return all;
        }
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String k : all) {
            if (!nonEnumerable.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    /**
     * ES [[OwnPropertyKeys]] order: canonical array-index keys (0 ..
     * 2^32−2 without leading zeros) ascending first, then the remaining
     * string keys in insertion order.
     */
    static java.util.Set<String> esOrderedKeys(java.util.Collection<String> keys) {
        java.util.List<String> ints = new java.util.ArrayList<>();
        java.util.List<String> rest = new java.util.ArrayList<>();
        for (String k : keys) {
            if (isArrayIndexKey(k)) {
                ints.add(k);
            } else {
                rest.add(k);
            }
        }
        ints.sort((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)));
        java.util.Set<String> out = new java.util.LinkedHashSet<>(ints.size() + rest.size());
        out.addAll(ints);
        out.addAll(rest);
        return out;
    }

    private static boolean isArrayIndexKey(String k) {
        if (k.isEmpty() || k.length() > 10) {
            return false;
        }
        if (k.equals("0")) {
            return true;
        }
        if (k.charAt(0) == '0') {
            return false; // leading zeros are not canonical
        }
        long v = 0;
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            v = v * 10 + (c - '0');
            if (v >= 4294967295L) {
                return false;
            }
        }
        return true;
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
