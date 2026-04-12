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

package org.ngscript.builtins;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgScriptRuntimeException;
import org.ngscript.runtime.NgsArray;
import org.ngscript.runtime.NgsObject;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.Undefined;

/**
 * The ES {@code Object} constructor: callable ({@code Object()} / {@code new
 * Object()}) and carrying the static methods keys/create/getPrototypeOf/
 * defineProperty/assign. Static methods are dispatched from
 * {@link org.ngscript.runtime.OpObject#member_ref} via {@link #lookup}.
 *
 * @author wssccc
 */
public class ObjectCtor implements Invokable {

    /** Shared instance: the ES Object constructor object itself. */
    public static final ObjectCtor INSTANCE = new ObjectCtor();

    /** The shared Object.prototype (for instanceof and prototype access). */
    public NgsObject getPrototype() {
        return Builtins.getObjectPrototype();
    }

    @Override
    public void invoke(Machine vm, Object[] args) {
        NgsObject obj = new NgsObject((NgsObject) vm.env.read());
        obj.setPrototype(vm.objectPrototype);
        vm.eax.write(obj);
    }

    /**
     * Static Object methods; null when the member does not exist.
     */
    public static MemRef lookup(ObjectCtor ctor, String member, Machine vm) {
        switch (member) {
            case "is":
                // Object.is: the SameValue comparison (no type coercion,
                // NaN equals NaN, +0 differs from -0)
                return new MemRef((Invokable) (vm2, args) -> {
                    vm2.eax.write(org.ngscript.runtime.OpUtils.strictEquals(
                            args.length > 0 ? args[0] : null,
                            args.length > 1 ? args[1] : null));
                });
            case "keys":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject o = objectArg(vm2, args, 0);
                    NgsArray out = new NgsArray(null);
                    int i = 0;
                    for (String k : o.enumerableKeys()) {
                        out.set("" + i++, k);
                    }
                    out.setPrototype(vm2.arrayPrototype);
                    vm2.eax.write(out);
                });
            case "create": {
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject proto = args.length > 0 && args[0] instanceof NgsObject
                            ? (NgsObject) args[0] : null;
                    NgsObject obj = new NgsObject((NgsObject) vm2.env.read());
                    obj.setPrototype(proto);
                    vm2.eax.write(obj);
                });
            }
            case "getPrototypeOf":
                return new MemRef((Invokable) (vm2, args) -> {
                    if (args.length > 0 && args[0] instanceof org.ngscript.value.ScriptFunction) {
                        // function objects: the shared Function.prototype is
                        // not modelled; report the placeholder (undefined)
                        vm2.eax.write(Undefined.value);
                        return;
                    }
                    NgsObject o = objectArg(vm2, args, 0);
                    NgsObject p = o.getPrototype();
                    vm2.eax.write(p == null ? Undefined.value : p);
                });
            case "setPrototypeOf":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject o = objectArg(vm2, args, 0);
                    Object proto = args.length > 1 ? args[1] : null;
                    o.setPrototype(proto instanceof NgsObject ? (NgsObject) proto : null);
                    vm2.eax.write(o);
                });
            case "getOwnPropertyDescriptor":
                return new MemRef((Invokable) (vm2, args) -> {
                    String name = args.length > 1
                            ? org.ngscript.runtime.OpArray.keyString(args[1]) : "undefined";
                    if (args.length > 0 && args[0] instanceof org.ngscript.value.ScriptFunction) {
                        org.ngscript.value.ScriptFunction f =
                                (org.ngscript.value.ScriptFunction) args[0];
                        Object v = null;
                        if ("name".equals(name)) {
                            v = f.name == null ? "" : f.name;
                        } else if ("length".equals(name)) {
                            v = f.params;
                        } else if ("prototype".equals(name)) {
                            v = f.getPrototypeObject();
                        }
                        if (v == null) {
                            // class-defined static members live in hostProps
                            java.util.Map<String, MemRef> own = vm2.hostProps.get(f);
                            if (own == null || !own.containsKey(name)
                                    || (name.length() > 0 && name.charAt(0) == '#')) {
                                // private names are not ordinary properties
                                vm2.eax.write(Undefined.value);
                                return;
                            }
                            NgsObject desc = new NgsObject((NgsObject) vm2.env.read());
                            desc.setPrototype(vm2.objectPrototype);
                            desc.set("value", own.get(name).read());
                            desc.set("writable", Boolean.TRUE);
                            java.util.Set<String> nonEnum = vm2.hostNonEnumerable.get(f);
                            desc.set("enumerable", nonEnum == null || !nonEnum.contains(name));
                            desc.set("configurable", Boolean.TRUE);
                            vm2.eax.write(desc);
                            return;
                        }
                        NgsObject desc = new NgsObject((NgsObject) vm2.env.read());
                        desc.setPrototype(vm2.objectPrototype);
                        desc.set("value", v);
                        desc.set("writable", Boolean.TRUE);
                        desc.set("enumerable", Boolean.FALSE);
                        desc.set("configurable", Boolean.TRUE);
                        if (("name".equals(name) || "prototype".equals(name))
                                && f.deletedProps != null
                                && f.deletedProps.contains(name)) {
                            // the property was deleted: no own descriptor
                            vm2.eax.write(Undefined.value);
                            return;
                        }
                        if ("name".equals(name) || "length".equals(name)) {
                            // ES: function name/length are non-writable
                            desc.set("writable", Boolean.FALSE);
                        }
                        if ("prototype".equals(name)) {
                            // ES: F.prototype is non-configurable
                            desc.set("configurable", Boolean.FALSE);
                        }
                        vm2.eax.write(desc);
                        return;
                    }
                    NgsObject o = objectArg(vm2, args, 0);
                    Object v = o.get(name);
                    if (o instanceof NgsArray && "length".equals(name) && v == null) {
                        // the virtual length of an array-like (arguments):
                        // one past the highest numeric index
                        v = ((NgsArray) o).length();
                    }
                    if (v == null && !o.has(name)) {
                        vm2.eax.write(Undefined.value);
                        return;
                    }
                    NgsObject desc = new NgsObject((NgsObject) vm2.env.read());
                    desc.setPrototype(vm2.objectPrototype);
                    desc.set("value", v == null ? Undefined.value : v);
                    desc.set("writable", o.isNonWritable(name) ? Boolean.FALSE : Boolean.TRUE);
                    // callee/length (arrays and arguments) are not enumerable
                    desc.set("enumerable", !("callee".equals(name) || "length".equals(name))
                            && o.isEnumerable(name));
                    desc.set("configurable", o.isNonConfigurable(name) ? Boolean.FALSE : Boolean.TRUE);
                    vm2.eax.write(desc);
                });
            case "getOwnPropertyNames":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsArray out = new NgsArray(null);
                    int i = 0;
                    if (args.length > 0 && args[0] instanceof org.ngscript.value.ScriptFunction) {
                        org.ngscript.value.ScriptFunction f = (org.ngscript.value.ScriptFunction) args[0];
                        // intrinsic function properties first, then the
                        // class-defined static members (hostProps)
                        for (String k : functionPropertyNames()) {
                            out.set("" + i++, k);
                        }
                        java.util.Map<String, MemRef> own = vm2.hostProps.get(f);
                        if (own != null) {
                            for (String k : own.keySet()) {
                                out.set("" + i++, k);
                            }
                        }
                        out.setPrototype(vm2.arrayPrototype);
                        vm2.eax.write(out);
                        return;
                    }
                    NgsObject o = objectArg(vm2, args, 0);
                    for (String k : o.keys()) {
                        out.set("" + i++, k);
                    }
                    if (o instanceof org.ngscript.runtime.NgsArray
                            && !o.has("length")) {
                        out.set("" + i++, "length");
                    }
                    out.setPrototype(vm2.arrayPrototype);
                    vm2.eax.write(out);
                });
            case "defineProperty":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject o = objectArg(vm2, args, 0);
                    String name = args.length > 1
                            ? org.ngscript.runtime.OpArray.keyString(args[1]) : "undefined";
                    Object desc = args.length > 2 ? args[2] : null;
                    if (desc instanceof NgsObject) {
                        NgsObject d = (NgsObject) desc;
                        Object getter = d.get("get");
                        Object setter = d.get("set");
                        if (getter != null || setter != null) {
                            // accessor property: getter/setter functions
                            o.defineAccessor(name,
                                    getter instanceof org.ngscript.value.Invokable
                                            || getter instanceof org.ngscript.value.ScriptFunction ? getter : null,
                                    setter instanceof org.ngscript.value.Invokable
                                            || setter instanceof org.ngscript.value.ScriptFunction ? setter : null);
                        } else if (d.get("value") != null) {
                            // writing a value keeps the arguments mapping
                            // (ES: DefineOwnProperty on a mapped property
                            // writes through to the parameter); only
                            // writable:false removes the mapping
                            MemRef existing = o.getRef(name);
                            if (existing != null) {
                                existing.write(d.get("value"));
                            } else {
                                o.set(name, d.get("value"));
                            }
                        } else if (d.has("writable") && Boolean.FALSE.equals(d.get("writable"))) {
                            // making the property non-writable removes the
                            // arguments mapping (configurable:false alone
                            // keeps it): copy the current value so later
                            // parameter writes no longer alias this property
                            MemRef existing = o.getRef(name);
                            if (existing != null) {
                                o.set(name, existing.read());
                            }
                            o.markNonWritable(name);
                        }
                        if (Boolean.FALSE.equals(d.get("configurable"))) {
                            // delete on a non-configurable property fails
                            o.markNonConfigurable(name);
                        }
                        if (Boolean.FALSE.equals(d.get("enumerable"))) {
                            // Object.keys/for-in/object-rest skip it
                            o.markNonEnumerable(name);
                        }
                    }
                    vm2.eax.write(o);
                });
            case "assign":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject target = objectArg(vm2, args, 0);
                    for (int i = 1; i < args.length; i++) {
                        if (args[i] instanceof NgsObject) {
                            NgsObject src = (NgsObject) args[i];
                            for (String k : src.keys()) {
                                target.set(k, src.get(k));
                            }
                        }
                    }
                    vm2.eax.write(target);
                });
            case "entries":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject o = objectArg(vm2, args, 0);
                    NgsArray out = new NgsArray(null);
                    int i = 0;
                    for (String k : o.keys()) {
                        NgsArray pair = new NgsArray(null);
                        pair.setPrototype(vm2.arrayPrototype);
                        pair.set("0", k);
                        pair.set("1", o.get(k));
                        out.set("" + i++, pair);
                    }
                    out.setPrototype(vm2.arrayPrototype);
                    vm2.eax.write(out);
                });
            case "values":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject o = objectArg(vm2, args, 0);
                    NgsArray out = new NgsArray(null);
                    int i = 0;
                    for (String k : o.keys()) {
                        out.set("" + i++, o.get(k));
                    }
                    out.setPrototype(vm2.arrayPrototype);
                    vm2.eax.write(out);
                });
            case "fromEntries":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject out = new NgsObject((NgsObject) vm2.env.read());
                    out.setPrototype(vm2.objectPrototype);
                    Object entries = args.length > 0 ? args[0] : null;
                    if (entries instanceof NgsArray) {
                        NgsArray arr = (NgsArray) entries;
                        for (int i = 0; i < arr.length(); i++) {
                            Object pair = arr.get("" + i);
                            if (pair instanceof NgsObject) {
                                NgsObject p = (NgsObject) pair;
                                Object k = p.get("0");
                                Object v = p.get("1");
                                out.set(k == null ? "undefined"
                                        : org.ngscript.runtime.OpArray.keyString(k), v);
                            }
                        }
                    }
                    vm2.eax.write(out);
                });
            default:
                return null;
        }
    }

    private static NgsObject objectArg(Machine vm, Object[] args, int i) throws NgScriptRuntimeException {
        if (args.length > i && args[i] instanceof NgsObject) {
            return (NgsObject) args[i];
        }
        throw new NgScriptRuntimeException(vm, "expected an object argument");
    }

    /**
     * The property names of a function object (class constructors and
     * functions are not NgsObjects): name, length and prototype.
     */
    static String[] functionPropertyNames() {
        // ES own-property order for function objects: length, name,
        // prototype (chronological creation order)
        return new String[]{"length", "name", "prototype"};
    }
}
