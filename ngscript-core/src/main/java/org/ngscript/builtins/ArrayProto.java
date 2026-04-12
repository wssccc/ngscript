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

import java.util.ArrayList;
import java.util.List;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgScriptRuntimeException;
import org.ngscript.runtime.NgsArray;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;
import org.ngscript.value.Undefined;

/**
 * The shared Array.prototype object with the ES5 array methods. Array
 * literals link to it via {@link Machine#arrayPrototype}. Methods receive
 * the array through the {@code this} binding.
 *
 * @author wssccc
 */
public class ArrayProto {

    public static NgsObject create() {
        NgsObject proto = new NgsObject(null);
        // ES: Array.prototype.constructor === Array
        proto.set("constructor", org.ngscript.builtins.ArrayCtor.INSTANCE);
        // Array.prototype's [[Prototype]] is Object.prototype: instance
        // methods like hasOwnProperty must resolve through it
        proto.setPrototype(Builtins.getObjectPrototype());
        register(proto, "push", (vm, args) -> push(vm, args));
        register(proto, "pop", (vm, args) -> pop(vm, args));
        register(proto, "shift", (vm, args) -> shift(vm, args));
        register(proto, "unshift", (vm, args) -> unshift(vm, args));
        register(proto, "join", (vm, args) -> join(vm, args));
        register(proto, "indexOf", (vm, args) -> indexOf(vm, args, false));
        register(proto, "lastIndexOf", (vm, args) -> indexOf(vm, args, true));
        register(proto, "slice", (vm, args) -> slice(vm, args));
        register(proto, "concat", (vm, args) -> concat(vm, args));
        register(proto, "reverse", (vm, args) -> reverse(vm, args));
        register(proto, "forEach", (vm, args) -> forEach(vm, args, null));
        register(proto, "map", (vm, args) -> forEach(vm, args, "map"));
        register(proto, "filter", (vm, args) -> forEach(vm, args, "filter"));
        register(proto, "some", (vm, args) -> forEach(vm, args, "some"));
        register(proto, "every", (vm, args) -> forEach(vm, args, "every"));
        register(proto, "reduce", (vm, args) -> reduce(vm, args));
        register(proto, "sort", (vm, args) -> sort(vm, args));
        register(proto, "splice", (vm, args) -> splice(vm, args));
        register(proto, "find", (vm, args) -> find(vm, args, false));
        register(proto, "findIndex", (vm, args) -> find(vm, args, true));
        register(proto, "includes", (vm, args) -> includes(vm, args));
        register(proto, "fill", (vm, args) -> fill(vm, args));
        register(proto, "keys", (vm, args) -> keys(vm, args));
        register(proto, "entries", (vm, args) -> entries(vm, args));
        register(proto, "copyWithin", (vm, args) -> copyWithin(vm, args));
        register(proto, "toString", (vm, args) -> join(vm, new Object[0]));
        register(proto, "isArray", (vm, args) -> vm.eax.write(
                args.length > 0 && args[0] instanceof NgsArray));
        // the array iterator (for-of and arr[Symbol.iterator]()): the array
        // itself implements java.util.Iterator
        register(proto, "@@iterator", (vm, args) -> {
            Object t = vm.thisRef.read();
            if (t instanceof NgsArray) {
                ((NgsArray) t).resetIterator();
            }
            vm.eax.write(t);
        });
        return proto;
    }

    private static void register(NgsObject proto, String name, Invokable inv) {
        proto.set(name, inv);
    }

    private static NgsArray self(Machine vm) throws NgScriptRuntimeException {
        Object t = vm.thisRef.read();
        if (t instanceof NgsArray) {
            return (NgsArray) t;
        }
        throw new NgScriptRuntimeException(vm, "Array method called on a non-array value");
    }

    private static int lengthOf(NgsArray arr) {
        Object cached = arr.get("length");
        if (cached instanceof Integer) {
            return (Integer) cached;
        }
        // no cached length yet: compute from the highest numeric key
        int len = 0;
        for (String k : arr.keys()) {
            try {
                len = Math.max(len, Integer.parseInt(k) + 1);
            } catch (NumberFormatException ignored) {
                // non-numeric keys do not affect length
            }
        }
        return len;
    }

    private static Object get(NgsObject o, int idx) {
        return o.get("" + idx);
    }

    private static void set(NgsObject o, int idx, Object v) {
        o.set("" + idx, v);
    }

    private static Object arg(Object[] args, int i, Object def) {
        return args.length > i && args[i] != null ? args[i] : def;
    }

    //----------------------------------------------------------------------
    // mutators
    //----------------------------------------------------------------------

    private static void push(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        int len = lengthOf(arr);
        for (int i = 0; i < args.length; i++) {
            set(arr, len + i, args[i]);
        }
        vm.eax.write(len + args.length);
    }

    private static void pop(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        int len = lengthOf(arr);
        if (len == 0) {
            vm.eax.write(Undefined.value);
            return;
        }
        Object v = get(arr, len - 1);
        arr.remove("" + (len - 1));
        vm.eax.write(v);
    }

    private static void shift(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        int len = lengthOf(arr);
        if (len == 0) {
            vm.eax.write(Undefined.value);
            return;
        }
        Object v = get(arr, 0);
        for (int i = 1; i < len; i++) {
            set(arr, i - 1, get(arr, i));
        }
        arr.remove("" + (len - 1));
        vm.eax.write(v);
    }

    private static void unshift(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        int len = lengthOf(arr);
        for (int i = len - 1; i >= 0; i--) {
            set(arr, i + args.length, get(arr, i));
        }
        for (int i = 0; i < args.length; i++) {
            set(arr, i, args[i]);
        }
        vm.eax.write(len + args.length);
    }

    private static void reverse(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        int len = lengthOf(arr);
        for (int i = 0; i < len / 2; i++) {
            Object tmp = get(arr, i);
            set(arr, i, get(arr, len - 1 - i));
            set(arr, len - 1 - i, tmp);
        }
        vm.eax.write(arr);
    }

    private static void splice(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        int len = lengthOf(arr);
        int start = toIndex(args.length > 0 ? args[0] : 0, len);
        int del = args.length > 1 ? Math.max(0, toIndex(args[1], len - start)) : len - start;
        NgsArray removed = new NgsArray(null);
        for (int i = 0; i < del; i++) {
            set(removed, i, get(arr, start + i));
        }
        List<Object> keep = new ArrayList<>();
        for (int i = start + del; i < len; i++) {
            keep.add(get(arr, i));
        }
        for (int i = 0; i < keep.size(); i++) {
            set(arr, start + i, keep.get(i));
        }
        int newLen = start + keep.size();
        for (int i = start + keep.size(); i < len; i++) {
            arr.remove("" + i);
        }
        int insert = Math.max(0, args.length - 2);
        if (insert > 0) {
            for (int i = keep.size() - 1; i >= 0; i--) {
                set(arr, start + insert + i, keep.get(i));
            }
            for (int i = 0; i < insert; i++) {
                set(arr, start + i, args[2 + i]);
            }
        }
        removed.setPrototype(vm.arrayPrototype);
        vm.eax.write(removed);
    }

    //----------------------------------------------------------------------
    // queries
    //----------------------------------------------------------------------

    private static int toIndex(Object v, int len) {
        double d = OpUtils.toNumber(v);
        if (Double.isNaN(d)) {
            return 0;
        }
        int i = (int) d;
        return i < 0 ? Math.max(len + i, 0) : Math.min(i, len);
    }

    private static void join(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        String sep = args.length > 0 && args[0] != null ? OpUtils.toStringValue(args[0]) : ",";
        int len = lengthOf(arr);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(sep);
            }
            Object v = get(arr, i);
            if (v != null && v != Undefined.value) {
                sb.append(OpUtils.toStringValue(v));
            }
        }
        vm.eax.write(sb.toString());
    }

    private static void indexOf(Machine vm, Object[] args, boolean fromEnd) throws Exception {
        NgsArray arr = self(vm);
        int len = lengthOf(arr);
        Object target = args.length > 0 ? args[0] : null;
        if (fromEnd) {
            for (int i = len - 1; i >= 0; i--) {
                if (OpUtils.strictEquals(get(arr, i), target)) {
                    vm.eax.write(i);
                    return;
                }
            }
        } else {
            int from = args.length > 1 ? toIndex(args[1], len) : 0;
            for (int i = from; i < len; i++) {
                if (OpUtils.strictEquals(get(arr, i), target)) {
                    vm.eax.write(i);
                    return;
                }
            }
        }
        vm.eax.write(-1);
    }

    private static void slice(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        int len = lengthOf(arr);
        int start = args.length > 0 ? toIndex(args[0], len) : 0;
        int end = args.length > 1 ? toIndex(args[1], len) : len;
        NgsArray out = new NgsArray(null);
        for (int i = start; i < end; i++) {
            set(out, i - start, get(arr, i));
        }
        out.setPrototype(vm.arrayPrototype);
        vm.eax.write(out);
    }

    private static void concat(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        NgsArray out = new NgsArray(null);
        int n = 0;
        List<Object> values = new ArrayList<>();
        int len = lengthOf(arr);
        for (int i = 0; i < len; i++) {
            values.add(get(arr, i));
        }
        for (Object a : args) {
            if (a instanceof NgsArray) {
                NgsArray other = (NgsArray) a;
                int ol = lengthOf(other);
                for (int i = 0; i < ol; i++) {
                    values.add(get(other, i));
                }
            } else {
                values.add(a);
            }
        }
        for (Object v : values) {
            set(out, n++, v);
        }
        out.setPrototype(vm.arrayPrototype);
        vm.eax.write(out);
    }

    //----------------------------------------------------------------------
    // callbacks
    //----------------------------------------------------------------------

    private static void forEach(Machine vm, Object[] args, String mode) throws Exception {
        NgsArray arr = self(vm);
        Object cb = args.length > 0 ? args[0] : null;
        Object thisArg = args.length > 1 ? args[1] : null;
        if (!(cb instanceof Invokable) && !(cb instanceof org.ngscript.value.ScriptFunction)) {
            throw new NgScriptRuntimeException(vm, "callback is not a function");
        }
        int len = lengthOf(arr);
        if ("map".equals(mode)) {
            NgsArray out = new NgsArray(null);
            for (int i = 0; i < len; i++) {
                Object r = vm.callFunctionSync(cb, new Object[]{get(arr, i), i, arr}, thisArg);
                set(out, i, r);
            }
            out.setPrototype(vm.arrayPrototype);
            vm.eax.write(out);
            return;
        }
        if ("filter".equals(mode)) {
            NgsArray out = new NgsArray(null);
            int n = 0;
            for (int i = 0; i < len; i++) {
                Object r = vm.callFunctionSync(cb, new Object[]{get(arr, i), i, arr}, thisArg);
                if (OpUtils.testValue(r)) {
                    set(out, n++, get(arr, i));
                }
            }
            out.setPrototype(vm.arrayPrototype);
            vm.eax.write(out);
            return;
        }
        if ("some".equals(mode) || "every".equals(mode)) {
            boolean every = "every".equals(mode);
            for (int i = 0; i < len; i++) {
                Object r = vm.callFunctionSync(cb, new Object[]{get(arr, i), i, arr}, thisArg);
                if (OpUtils.testValue(r) != every) {
                    vm.eax.write(!every);
                    return;
                }
            }
            vm.eax.write(every);
            return;
        }
        for (int i = 0; i < len; i++) {
            vm.callFunctionSync(cb, new Object[]{get(arr, i), i, arr}, thisArg);
        }
        vm.eax.write(Undefined.value);
    }

    //----------------------------------------------------------------------
    // ES2015+ additions
    //----------------------------------------------------------------------

    /** ES Array.prototype.find/findIndex (predicate, thisArg). */
    private static void find(Machine vm, Object[] args, boolean wantIndex) throws Exception {
        NgsArray arr = self(vm);
        Object cb = args.length > 0 ? args[0] : null;
        Object thisArg = args.length > 1 ? args[1] : null;
        if (!(cb instanceof Invokable) && !(cb instanceof org.ngscript.value.ScriptFunction)) {
            throw new NgScriptRuntimeException(vm, "callback is not a function");
        }
        int len = lengthOf(arr);
        for (int i = 0; i < len; i++) {
            Object v = get(arr, i);
            Object r = vm.callFunctionSync(cb, new Object[]{v, i, arr}, thisArg);
            if (OpUtils.testValue(r)) {
                vm.eax.write(wantIndex ? (Integer) i : v);
                return;
            }
        }
        vm.eax.write(wantIndex ? -1 : Undefined.value);
    }

    /** ES Array.prototype.includes (SameValueZero). */
    private static void includes(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        Object target = args.length > 0 ? args[0] : null;
        int len = lengthOf(arr);
        int from = args.length > 1 ? toIndex(args[1], len) : 0;
        for (int i = from; i < len; i++) {
            if (OpUtils.sameValueZero(get(arr, i), target)) {
                vm.eax.write(true);
                return;
            }
        }
        vm.eax.write(false);
    }

    /** ES Array.prototype.fill(value, start, end). */
    private static void fill(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        Object value = args.length > 0 ? args[0] : null;
        int len = lengthOf(arr);
        int start = args.length > 1 ? toIndex(args[1], len) : 0;
        int end = args.length > 2 ? toIndex(args[2], len) : len;
        for (int i = start; i < end; i++) {
            set(arr, i, value);
        }
        vm.eax.write(arr);
    }

    /** ES Array.prototype.keys(): an iterator over the indices. */
    private static void keys(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        final int len = lengthOf(arr);
        vm.eax.write(new java.util.Iterator<Object>() {
            private int i;

            @Override
            public boolean hasNext() {
                return i < len;
            }

            @Override
            public Object next() {
                return i++;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        });
    }

    /** ES Array.prototype.entries(): an iterator over [index, value] pairs. */
    private static void entries(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        final int len = lengthOf(arr);
        vm.eax.write(new java.util.Iterator<Object>() {
            private int i;

            @Override
            public boolean hasNext() {
                return i < len;
            }

            @Override
            public Object next() {
                NgsArray pair = new NgsArray(null);
                pair.set("0", i);
                pair.set("1", get(arr, i));
                ++i;
                return pair;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        });
    }

    /** ES Array.prototype.copyWithin(target, start, end). */
    private static void copyWithin(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        int len = lengthOf(arr);
        int target = args.length > 0 ? toIndex(args[0], len) : 0;
        int start = args.length > 1 ? toIndex(args[1], len) : 0;
        int end = args.length > 2 ? toIndex(args[2], len) : len;
        if (target < start) {
            for (int i = start; i < end; i++) {
                set(arr, target + (i - start), get(arr, i));
            }
        } else {
            int count = Math.max(0, end - start);
            for (int i = count - 1; i >= 0; i--) {
                set(arr, target + i, get(arr, start + i));
            }
        }
        vm.eax.write(arr);
    }

    private static void reduce(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        Object cb = args.length > 0 ? args[0] : null;
        int len = lengthOf(arr);
        Object acc;
        int i = 0;
        if (args.length > 1) {
            acc = args[1];
        } else {
            if (len == 0) {
                throw new NgScriptRuntimeException(vm, "reduce of empty array with no initial value");
            }
            acc = get(arr, 0);
            i = 1;
        }
        for (; i < len; i++) {
            acc = vm.callFunctionSync(cb, new Object[]{acc, get(arr, i), i, arr}, null);
        }
        vm.eax.write(acc);
    }

    private static void sort(Machine vm, Object[] args) throws Exception {
        NgsArray arr = self(vm);
        Object cb = args.length > 0 ? args[0] : null;
        int len = lengthOf(arr);
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            values.add(get(arr, i));
        }
        if (cb instanceof Invokable || cb instanceof org.ngscript.value.ScriptFunction) {
            values.sort((a, b) -> {
                try {
                    Object r = vm.callFunctionSync(cb, new Object[]{a, b}, null);
                    double d = OpUtils.toNumber(r);
                    if (Double.isNaN(d)) {
                        return 0;
                    }
                    return d < 0 ? -1 : (d > 0 ? 1 : 0);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            values.sort((a, b) -> OpUtils.toStringValue(a).compareTo(OpUtils.toStringValue(b)));
        }
        for (int i = 0; i < len; i++) {
            set(arr, i, values.get(i));
        }
        vm.eax.write(arr);
    }
}
