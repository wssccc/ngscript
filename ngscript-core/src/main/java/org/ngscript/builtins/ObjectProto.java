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
import org.ngscript.runtime.NgsObject;
import org.ngscript.value.Invokable;
import org.ngscript.value.Undefined;

/**
 * The shared Object.prototype: toString/hasOwnProperty/valueOf. Object
 * literals link to it via {@link Machine#objectPrototype}.
 *
 * @author wssccc
 */
public class ObjectProto {

    public static NgsObject create() {
        NgsObject proto = new NgsObject(null);
        proto.set("toString", (Invokable) (vm, args) -> vm.eax.write("[object Object]"));
        proto.set("valueOf", (Invokable) (vm, args) -> {
            Object t = vm.thisRef.read();
            vm.eax.write(t == null ? Undefined.value : t);
        });
        proto.set("hasOwnProperty", (Invokable) (vm, args) -> {
            String key = args.length > 0
                    ? org.ngscript.runtime.OpArray.keyString(args[0]) : "undefined";
            Object t = vm.thisRef.read();
            if (t instanceof NgsObject) {
                vm.eax.write(((NgsObject) t).has(key));
            } else if (t instanceof org.ngscript.value.ScriptFunction) {
                // intrinsic function properties (name/length/prototype,
                // call/apply/bind placeholders) are own properties of the
                // function object; class-defined static members live in
                // hostProps (private names #x stay invisible)
                org.ngscript.value.ScriptFunction fn = (org.ngscript.value.ScriptFunction) t;
                boolean intrinsic = "name".equals(key) || "length".equals(key)
                        || "prototype".equals(key)
                        || "call".equals(key) || "apply".equals(key) || "bind".equals(key);
                java.util.Map<String, org.ngscript.value.MemRef> own = vm.hostProps.get(t);
                vm.eax.write((intrinsic
                            && (fn.deletedProps == null || !fn.deletedProps.contains(key)))
                        || (own != null && own.containsKey(key)
                            && (key.length() == 0 || key.charAt(0) != '#')));
            } else {
                // host objects: class-defined static members live in the
                // shared hostProps map; private names (#x) stay invisible
                java.util.Map<String, org.ngscript.value.MemRef> own = vm.hostProps.get(t);
                vm.eax.write(own != null && own.containsKey(key)
                        && (key.length() == 0 || key.charAt(0) != '#'));
            }
        });
        proto.set("propertyIsEnumerable", (Invokable) (vm, args) -> {
            String key = args.length > 0
                    ? org.ngscript.runtime.OpArray.keyString(args[0]) : "undefined";
            Object t = vm.thisRef.read();
            if (t instanceof NgsObject) {
                NgsObject obj = (NgsObject) t;
                // an own data property is enumerable unless defineProperty
                // (or a non-enumerable class member) marked it otherwise
                vm.eax.write(obj.has(key) && obj.isEnumerable(key));
            } else {
                java.util.Map<String, org.ngscript.value.MemRef> own = vm.hostProps.get(t);
                java.util.Set<String> nonEnum = vm.hostNonEnumerable.get(t);
                vm.eax.write(own != null && own.containsKey(key)
                        && (nonEnum == null || !nonEnum.contains(key)));
            }
        });
        proto.set("isPrototypeOf", (Invokable) (vm, args) -> {
            Object t = vm.thisRef.read();
            Object other = args.length > 0 ? args[0] : null;
            if (t instanceof NgsObject && other instanceof NgsObject) {
                NgsObject p = ((NgsObject) other).getPrototype();
                while (p != null) {
                    if (p == t) {
                        vm.eax.write(true);
                        return;
                    }
                    p = p.getPrototype();
                }
            }
            vm.eax.write(false);
        });
        // annexB: __lookupGetter__/__lookupSetter__ walk the [[Prototype]]
        // chain (including the object itself) for an accessor property and
        // return its getter/setter function (undefined when absent)
        proto.set("__lookupGetter__", (Invokable) (vm, args) -> {
            String key = args.length > 0
                    ? org.ngscript.runtime.OpArray.keyString(args[0]) : "undefined";
            vm.eax.write(lookupAccessor(vm.thisRef.read(), key, "get"));
        });
        proto.set("__lookupSetter__", (Invokable) (vm, args) -> {
            String key = args.length > 0
                    ? org.ngscript.runtime.OpArray.keyString(args[0]) : "undefined";
            vm.eax.write(lookupAccessor(vm.thisRef.read(), key, "set"));
        });
        return proto;
    }

    /** Finds an accessor's getter/setter on the object or its prototype chain. */
    private static Object lookupAccessor(Object t, String key, String which) {
        NgsObject p = t instanceof NgsObject ? (NgsObject) t : null;
        while (p != null) {
            Object fn = p.getAccessor(key, which);
            if (fn != null) {
                return fn;
            }
            p = p.getPrototype();
        }
        return Undefined.value;
    }
}
