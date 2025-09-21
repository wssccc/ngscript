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
            case "keys":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject o = objectArg(vm2, args, 0);
                    NgsArray out = new NgsArray(null);
                    int i = 0;
                    for (String k : o.keys()) {
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
                    NgsObject o = objectArg(vm2, args, 0);
                    NgsObject p = o.getPrototype();
                    vm2.eax.write(p == null ? Undefined.value : p);
                });
            case "getOwnPropertyDescriptor":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject o = objectArg(vm2, args, 0);
                    String name = args.length > 1 ? args[1].toString() : "undefined";
                    Object v = o.get(name);
                    if (v == null && !o.has(name)) {
                        vm2.eax.write(Undefined.value);
                        return;
                    }
                    NgsObject desc = new NgsObject((NgsObject) vm2.env.read());
                    desc.setPrototype(vm2.objectPrototype);
                    desc.set("value", v == null ? Undefined.value : v);
                    desc.set("writable", Boolean.TRUE);
                    desc.set("enumerable", Boolean.TRUE);
                    desc.set("configurable", Boolean.TRUE);
                    vm2.eax.write(desc);
                });
            case "getOwnPropertyNames":
                return new MemRef((Invokable) (vm2, args) -> {
                    NgsObject o = objectArg(vm2, args, 0);
                    NgsArray out = new NgsArray(null);
                    int i = 0;
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
                    String name = args.length > 1 ? args[1].toString() : "undefined";
                    Object desc = args.length > 2 ? args[2] : null;
                    if (desc instanceof NgsObject) {
                        NgsObject d = (NgsObject) desc;
                        if (d.get("value") != null) {
                            o.set(name, d.get("value"));
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
}
