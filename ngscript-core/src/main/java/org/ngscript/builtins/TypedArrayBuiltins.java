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

import org.ngscript.runtime.ArrayBufferValue;
import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgScriptRuntimeException;
import org.ngscript.runtime.NgsArray;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.runtime.TypedArrayValue;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.ScriptFunction;
import org.ngscript.value.Undefined;

/**
 * The ES {@code ArrayBuffer}, {@code DataView} and the typed array
 * constructors (Int8Array ... Float64Array) with their prototype methods.
 *
 * @author wssccc
 */
public class TypedArrayBuiltins {

    //----------------------------------------------------------------------
    // ArrayBuffer
    //----------------------------------------------------------------------

    /** The shared ArrayBuffer.prototype. */
    private static NgsObject arrayBufferPrototype;

    public static synchronized NgsObject getArrayBufferPrototype() {
        if (arrayBufferPrototype == null) {
            arrayBufferPrototype = new NgsObject(null);
            arrayBufferPrototype.setPrototype(Builtins.getObjectPrototype());
            arrayBufferPrototype.set("slice", (Invokable) TypedArrayBuiltins::abSlice);
        }
        return arrayBufferPrototype;
    }

    public static class ArrayBufferCtor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            int len = args.length > 0 ? (int) OpUtils.toNumber(args[0]) : 0;
            if (len < 0) {
                throw new NgScriptRuntimeException(vm, "negative ArrayBuffer size");
            }
            // the JVM cannot back arrays beyond Integer.MAX_VALUE; the ES
            // spec asks for a RangeError when the allocation is impossible
            if (len > Integer.MAX_VALUE - 8) {
                throw new NgScriptRuntimeException(vm, "ArrayBuffer allocation failed (too large)");
            }
            ArrayBufferValue ab = new ArrayBufferValue(len);
            ab.setPrototype(getArrayBufferPrototype());
            vm.eax.write(ab);
        }
    }

    /** ArrayBuffer.prototype.slice(begin, end) */
    private static void abSlice(Machine vm, Object[] args) {
        Object t = vm.thisRef.read();
        if (!(t instanceof ArrayBufferValue)) {
            throw new NgScriptRuntimeException(vm, "ArrayBuffer.prototype.slice called on non-buffer");
        }
        ArrayBufferValue ab = (ArrayBufferValue) t;
        int begin = args.length > 0 ? (int) OpUtils.toNumber(args[0]) : 0;
        int end = args.length > 1 ? (int) OpUtils.toNumber(args[1]) : ab.bytes.length;
        ArrayBufferValue out = ab.slice(begin, end);
        out.setPrototype(getArrayBufferPrototype());
        vm.eax.write(out);
    }

    //----------------------------------------------------------------------
    // Typed arrays
    //----------------------------------------------------------------------

    private static final String[] CTOR_NAMES = {
            "Int8Array", "Uint8Array", "Uint8ClampedArray",
            "Int16Array", "Uint16Array", "Int32Array", "Uint32Array",
            "Float32Array", "Float64Array"
    };

    /** One typed array constructor, e.g. new Int8Array(len|array|buffer). */
    public static class TypedArrayCtor implements Invokable {
        public final int kind;

        public TypedArrayCtor(int kind) {
            this.kind = kind;
        }

        @Override
        public void invoke(Machine vm, Object[] args) {
            TypedArrayValue ta = construct(kind, vm, args);
            ta.setPrototype(getTypedArrayPrototype(kind));
            vm.eax.write(ta);
        }
    }

    /** Shared prototype per typed array kind. */
    private static final NgsObject[] typedProtos = new NgsObject[9];

    public static synchronized NgsObject getTypedArrayPrototype(int kind) {
        if (typedProtos[kind] == null) {
            NgsObject p = new NgsObject(null);
            p.setPrototype(Builtins.getObjectPrototype());
            p.set("set", (Invokable) TypedArrayBuiltins::taSet);
            p.set("subarray", (Invokable) TypedArrayBuiltins::taSubarray);
            p.set("slice", (Invokable) TypedArrayBuiltins::taSlice);
            p.set("fill", (Invokable) TypedArrayBuiltins::taFill);
            p.set("join", (Invokable) TypedArrayBuiltins::taJoin);
            p.set("toString", (Invokable) TypedArrayBuiltins::taToString);
            p.set("indexOf", (Invokable) TypedArrayBuiltins::taIndexOf);
            p.set("includes", (Invokable) TypedArrayBuiltins::taIncludes);
            p.set("forEach", (Invokable) TypedArrayBuiltins::taForEach);
            p.set("map", (Invokable) TypedArrayBuiltins::taMap);
            p.set("filter", (Invokable) TypedArrayBuiltins::taFilter);
            p.set("reduce", (Invokable) TypedArrayBuiltins::taReduce);
            p.set("keys", (Invokable) TypedArrayBuiltins::taKeys);
            p.set("values", (Invokable) TypedArrayBuiltins::taValues);
            p.set("entries", (Invokable) TypedArrayBuiltins::taEntries);
            p.set("reverse", (Invokable) TypedArrayBuiltins::taReverse);
            p.set("sort", (Invokable) TypedArrayBuiltins::taSort);
            typedProtos[kind] = p;
        }
        return typedProtos[kind];
    }

    /**
     * Constructs a typed array from: a length; another typed array; an
     * array-like; or (buffer, byteOffset, length).
     */
    private static TypedArrayValue construct(int kind, Machine vm, Object[] args) throws NgScriptRuntimeException {
        int es = TypedArrayValue.elementSize(kind);
        if (args.length == 0) {
            return new TypedArrayValue(kind, new ArrayBufferValue(0), 0, 0);
        }
        Object a0 = args[0];
        if (a0 instanceof ArrayBufferValue) {
            int off = args.length > 1 ? (int) OpUtils.toNumber(args[1]) : 0;
            int len = args.length > 2 ? (int) OpUtils.toNumber(args[2])
                    : (((ArrayBufferValue) a0).bytes.length - off) / es;
            if (off < 0 || off % es != 0 || len < 0 || off + len * es > ((ArrayBufferValue) a0).bytes.length) {
                throw new NgScriptRuntimeException(vm, "invalid typed array length/offset");
            }
            return new TypedArrayValue(kind, (ArrayBufferValue) a0, off, len);
        }
        if (a0 instanceof TypedArrayValue) {
            TypedArrayValue src = (TypedArrayValue) a0;
            TypedArrayValue out = new TypedArrayValue(kind, new ArrayBufferValue(src.length * es), 0, src.length);
            for (int i = 0; i < src.length; i++) {
                out.writeElement(i, src.readElement(i));
            }
            return out;
        }
        // array-like or number
        if (OpUtils.isNumber(a0)) {
            int len = (int) OpUtils.toNumber(a0);
            if (len < 0) {
                throw new NgScriptRuntimeException(vm, "negative typed array length");
            }
            return new TypedArrayValue(kind, new ArrayBufferValue(len * es), 0, len);
        }
        // array-like: read length and copy elements
        int len = (int) OpUtils.toNumber(OpUtils.getMember(vm, a0, "length"));
        TypedArrayValue out = new TypedArrayValue(kind, new ArrayBufferValue(len * es), 0, len);
        for (int i = 0; i < len; i++) {
            out.writeElement(i, OpUtils.getMember(vm, a0, "" + i));
        }
        return out;
    }

    private static TypedArrayValue requireTa(Machine vm, String what) {
        Object t = vm.thisRef.read();
        if (!(t instanceof TypedArrayValue)) {
            throw new NgScriptRuntimeException(vm, what + " called on non-typed-array");
        }
        return (TypedArrayValue) t;
    }

    /** TypedArray.prototype.set(array|typedArray[, offset]) */
    private static void taSet(Machine vm, Object[] args) throws Exception {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.set");
        Object src = args.length > 0 ? args[0] : null;
        int off = args.length > 1 ? (int) OpUtils.toNumber(args[1]) : 0;
        if (src instanceof TypedArrayValue) {
            TypedArrayValue s = (TypedArrayValue) src;
            for (int i = 0; i < s.length && off + i < ta.length; i++) {
                ta.writeElement(off + i, s.readElement(i));
            }
            return;
        }
        int len = (int) OpUtils.toNumber(OpUtils.getMember(vm, src, "length"));
        for (int i = 0; i < len && off + i < ta.length; i++) {
            ta.writeElement(off + i, OpUtils.getMember(vm, src, "" + i));
        }
    }

    /** TypedArray.prototype.subarray(begin, end) */
    private static void taSubarray(Machine vm, Object[] args) {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.subarray");
        int begin = args.length > 0 ? (int) OpUtils.toNumber(args[0]) : 0;
        int end = args.length > 1 ? (int) OpUtils.toNumber(args[1]) : ta.length;
        if (begin < 0) {
            begin = Math.max(0, ta.length + begin);
        }
        if (end < 0) {
            end = Math.max(0, ta.length + end);
        }
        begin = Math.min(begin, ta.length);
        end = Math.min(end, ta.length);
        int len = Math.max(0, end - begin);
        TypedArrayValue out = new TypedArrayValue(ta.kind, ta.buffer,
                ta.byteOffset + begin * TypedArrayValue.elementSize(ta.kind), len);
        out.setPrototype(getTypedArrayPrototype(ta.kind));
        vm.eax.write(out);
    }

    /** TypedArray.prototype.slice(begin, end) */
    private static void taSlice(Machine vm, Object[] args) {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.slice");
        int begin = args.length > 0 ? (int) OpUtils.toNumber(args[0]) : 0;
        int end = args.length > 1 ? (int) OpUtils.toNumber(args[1]) : ta.length;
        if (begin < 0) {
            begin = Math.max(0, ta.length + begin);
        }
        if (end < 0) {
            end = Math.max(0, ta.length + end);
        }
        begin = Math.min(begin, ta.length);
        end = Math.min(end, ta.length);
        int len = Math.max(0, end - begin);
        int es = TypedArrayValue.elementSize(ta.kind);
        TypedArrayValue out = new TypedArrayValue(ta.kind, new ArrayBufferValue(len * es), 0, len);
        for (int i = 0; i < len; i++) {
            out.writeElement(i, ta.readElement(begin + i));
        }
        out.setPrototype(getTypedArrayPrototype(ta.kind));
        vm.eax.write(out);
    }

    /** TypedArray.prototype.fill(value[, start[, end]]) */
    private static void taFill(Machine vm, Object[] args) {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.fill");
        Object value = args.length > 0 ? args[0] : Undefined.value;
        int start = args.length > 1 ? (int) OpUtils.toNumber(args[1]) : 0;
        int end = args.length > 2 ? (int) OpUtils.toNumber(args[2]) : ta.length;
        if (start < 0) {
            start = Math.max(0, ta.length + start);
        }
        if (end < 0) {
            end = Math.max(0, ta.length + end);
        }
        start = Math.min(start, ta.length);
        end = Math.min(end, ta.length);
        for (int i = start; i < end; i++) {
            ta.writeElement(i, value);
        }
        vm.eax.write(ta);
    }

    /** TypedArray.prototype.join(separator) */
    private static void taJoin(Machine vm, Object[] args) {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.join");
        String sep = args.length > 0 && args[0] != null && args[0] != Undefined.value
                ? OpUtils.toStringValue(args[0]) : ",";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ta.length; i++) {
            if (i > 0) {
                sb.append(sep);
            }
            Object v = ta.readElement(i);
            if (v != null && v != Undefined.value) {
                sb.append(OpUtils.toStringValue(v));
            }
        }
        vm.eax.write(sb.toString());
    }

    /** TypedArray.prototype.toString() */
    private static void taToString(Machine vm, Object[] args) throws Exception {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.toString");
        Object join = OpUtils.getMember(vm, ta, "join");
        if (join instanceof Invokable || join instanceof ScriptFunction) {
            vm.eax.write(OpUtils.callValue(vm, join, ta, new Object[0]));
        } else {
            taJoin(vm, new Object[0]);
        }
    }

    /** TypedArray.prototype.indexOf(searchElement[, fromIndex]) */
    private static void taIndexOf(Machine vm, Object[] args) {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.indexOf");
        Object search = args.length > 0 ? args[0] : Undefined.value;
        int from = args.length > 1 ? (int) OpUtils.toNumber(args[1]) : 0;
        if (from < 0) {
            from = Math.max(0, ta.length + from);
        }
        for (int i = from; i < ta.length; i++) {
            if (OpUtils.strictEquals(ta.readElement(i), search)) {
                vm.eax.write(i);
                return;
            }
        }
        vm.eax.write(-1);
    }

    /** TypedArray.prototype.includes(searchElement[, fromIndex]) */
    private static void taIncludes(Machine vm, Object[] args) {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.includes");
        Object search = args.length > 0 ? args[0] : Undefined.value;
        int from = args.length > 1 ? (int) OpUtils.toNumber(args[1]) : 0;
        if (from < 0) {
            from = Math.max(0, ta.length + from);
        }
        for (int i = from; i < ta.length; i++) {
            if (OpUtils.sameValueZero(ta.readElement(i), search)) {
                vm.eax.write(Boolean.TRUE);
                return;
            }
        }
        vm.eax.write(Boolean.FALSE);
    }

    /** TypedArray.prototype.forEach(callback[, thisArg]) */
    private static void taForEach(Machine vm, Object[] args) throws Exception {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.forEach");
        Object cb = args.length > 0 ? args[0] : null;
        Object thisArg = args.length > 1 ? args[1] : Undefined.value;
        for (int i = 0; i < ta.length; i++) {
            OpUtils.callValue(vm, cb, thisArg, new Object[]{ta.readElement(i), i, ta});
        }
    }

    /** TypedArray.prototype.map(callback[, thisArg]) */
    private static void taMap(Machine vm, Object[] args) throws Exception {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.map");
        Object cb = args.length > 0 ? args[0] : null;
        Object thisArg = args.length > 1 ? args[1] : Undefined.value;
        int es = TypedArrayValue.elementSize(ta.kind);
        TypedArrayValue out = new TypedArrayValue(ta.kind, new ArrayBufferValue(ta.length * es), 0, ta.length);
        for (int i = 0; i < ta.length; i++) {
            Object r = OpUtils.callValue(vm, cb, thisArg, new Object[]{ta.readElement(i), i, ta});
            out.writeElement(i, r);
        }
        out.setPrototype(getTypedArrayPrototype(ta.kind));
        vm.eax.write(out);
    }

    /** TypedArray.prototype.filter(callback[, thisArg]) */
    private static void taFilter(Machine vm, Object[] args) throws Exception {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.filter");
        Object cb = args.length > 0 ? args[0] : null;
        Object thisArg = args.length > 1 ? args[1] : Undefined.value;
        java.util.ArrayList<Object> keep = new java.util.ArrayList<>();
        for (int i = 0; i < ta.length; i++) {
            Object v = ta.readElement(i);
            Object r = OpUtils.callValue(vm, cb, thisArg, new Object[]{v, i, ta});
            if (OpUtils.testValue(r)) {
                keep.add(v);
            }
        }
        int es = TypedArrayValue.elementSize(ta.kind);
        TypedArrayValue out = new TypedArrayValue(ta.kind, new ArrayBufferValue(keep.size() * es), 0, keep.size());
        for (int i = 0; i < keep.size(); i++) {
            out.writeElement(i, keep.get(i));
        }
        out.setPrototype(getTypedArrayPrototype(ta.kind));
        vm.eax.write(out);
    }

    /** TypedArray.prototype.reduce(callback[, initialValue]) */
    private static void taReduce(Machine vm, Object[] args) throws Exception {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.reduce");
        Object cb = args.length > 0 ? args[0] : null;
        int start = 0;
        Object acc;
        if (args.length > 1) {
            acc = args[1];
        } else {
            if (ta.length == 0) {
                throw new NgScriptRuntimeException(vm, "Reduce of empty typed array with no initial value");
            }
            acc = ta.readElement(0);
            start = 1;
        }
        for (int i = start; i < ta.length; i++) {
            acc = OpUtils.callValue(vm, cb, Undefined.value,
                    new Object[]{acc, ta.readElement(i), i, ta});
        }
        vm.eax.write(acc);
    }

    /** TypedArray.prototype.keys() -> iterator over indices */
    private static void taKeys(Machine vm, Object[] args) {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.keys");
        NgsArray idx = new NgsArray(null);
        idx.setPrototype(Builtins.getArrayPrototype());
        for (int i = 0; i < ta.length; i++) {
            idx.set("" + i, i);
        }
        vm.eax.write(new ArrayIterator(vm, idx));
    }

    /** TypedArray.prototype.values() and entries() — simple iterators. */
    private static void taValues(Machine vm, Object[] args) {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.values");
        vm.eax.write(new ArrayIterator(vm, ta.toArray()));
    }

    private static void taEntries(Machine vm, Object[] args) {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.entries");
        NgsArray pairs = new NgsArray(null);
        pairs.setPrototype(Builtins.getArrayPrototype());
        for (int i = 0; i < ta.length; i++) {
            NgsArray pair = new NgsArray(null);
            pair.setPrototype(Builtins.getArrayPrototype());
            pair.set("0", i);
            pair.set("1", ta.readElement(i));
            pairs.set("" + i, pair);
        }
        vm.eax.write(new ArrayIterator(vm, pairs));
    }

    /** TypedArray.prototype.reverse() */
    private static void taReverse(Machine vm, Object[] args) {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.reverse");
        int i = 0;
        int j = ta.length - 1;
        while (i < j) {
            Object tmp = ta.readElement(i);
            ta.writeElement(i, ta.readElement(j));
            ta.writeElement(j, tmp);
            i++;
            j--;
        }
        vm.eax.write(ta);
    }

    /** TypedArray.prototype.sort(compareFn) */
    private static void taSort(Machine vm, Object[] args) throws Exception {
        TypedArrayValue ta = requireTa(vm, "TypedArray.prototype.sort");
        Object cmp = args.length > 0 && (args[0] instanceof Invokable
                || args[0] instanceof ScriptFunction) ? args[0] : null;
        Object[] vals = new Object[ta.length];
        for (int i = 0; i < ta.length; i++) {
            vals[i] = ta.readElement(i);
        }
        java.util.Arrays.sort(vals, (a, b) -> {
            if (cmp != null) {
                try {
                    Object r = OpUtils.callValue(vm, cmp, Undefined.value, new Object[]{a, b});
                    double d = OpUtils.toNumber(r);
                    return d < 0 ? -1 : (d > 0 ? 1 : 0);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            double da = OpUtils.toNumber(a);
            double db = OpUtils.toNumber(b);
            return da < db ? -1 : (da > db ? 1 : 0);
        });
        for (int i = 0; i < ta.length; i++) {
            ta.writeElement(i, vals[i]);
        }
        vm.eax.write(ta);
    }

    //----------------------------------------------------------------------
    // DataView
    //----------------------------------------------------------------------

    /** The shared DataView.prototype. */
    private static NgsObject dataViewPrototype;

    public static synchronized NgsObject getDataViewPrototype() {
        if (dataViewPrototype == null) {
            dataViewPrototype = new NgsObject(null);
            dataViewPrototype.setPrototype(Builtins.getObjectPrototype());
            for (String m : new String[]{
                    "getInt8", "getUint8", "getInt16", "getUint16",
                    "getInt32", "getUint32", "getFloat32", "getFloat64"}) {
                dataViewPrototype.set(m, (Invokable) (vm, a) -> dvGet(vm, m, a));
            }
            for (String m : new String[]{
                    "setInt8", "setUint8", "setInt16", "setUint16",
                    "setInt32", "setUint32", "setFloat32", "setFloat64"}) {
                dataViewPrototype.set(m, (Invokable) (vm, a) -> dvSet(vm, m, a));
            }
        }
        return dataViewPrototype;
    }

    public static class DataViewValue extends NgsObject {
        public final ArrayBufferValue buffer;
        public final int byteOffset;
        public final int byteLength;

        public DataViewValue(ArrayBufferValue buffer, int byteOffset, int byteLength) {
            super(null);
            this.buffer = buffer;
            this.byteOffset = byteOffset;
            this.byteLength = byteLength;
            setPrototype(getDataViewPrototype());
        }

        @Override
        public MemRef lookup(String member, Machine vm, boolean isMember) {
            switch (member) {
                case "buffer":
                    return new MemRef(buffer);
                case "byteOffset":
                    return new MemRef(byteOffset);
                case "byteLength":
                    return new MemRef(byteLength);
            }
            return super.lookup(member, vm, isMember);
        }
    }

    public static class DataViewCtor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            Object a0 = args.length > 0 ? args[0] : null;
            if (!(a0 instanceof ArrayBufferValue)) {
                throw new NgScriptRuntimeException(vm, "DataView constructor requires an ArrayBuffer");
            }
            ArrayBufferValue ab = (ArrayBufferValue) a0;
            int off = args.length > 1 ? (int) OpUtils.toNumber(args[1]) : 0;
            int len = args.length > 2 ? (int) OpUtils.toNumber(args[2]) : ab.bytes.length - off;
            if (off < 0 || len < 0 || off + len > ab.bytes.length) {
                throw new NgScriptRuntimeException(vm, "invalid DataView offset/length");
            }
            vm.eax.write(new DataViewValue(ab, off, len));
        }
    }

    private static DataViewValue requireDv(Machine vm) {
        Object t = vm.thisRef.read();
        if (!(t instanceof DataViewValue)) {
            throw new NgScriptRuntimeException(vm, "DataView method called on non-DataView");
        }
        return (DataViewValue) t;
    }

    /** DataView.prototype.getXxx(byteOffset[, littleEndian]) */
    private static void dvGet(Machine vm, String name, Object[] args) {
        DataViewValue dv = requireDv(vm);
        int off = args.length > 0 ? (int) OpUtils.toNumber(args[0]) : 0;
        boolean le = args.length > 1 && OpUtils.testValue(args[1]);
        int es = name.endsWith("8") ? 1 : (name.endsWith("16") ? 2 : 4);
        if (name.endsWith("64")) {
            es = 8;
        }
        int abs = dv.byteOffset + off;
        if (off < 0 || abs + es > dv.byteOffset + dv.byteLength) {
            throw new NgScriptRuntimeException(vm, "DataView read out of bounds");
        }
        switch (name) {
            case "getInt8":
                vm.eax.write((int) dv.buffer.bytes[abs]);
                return;
            case "getUint8":
                vm.eax.write(dv.buffer.bytes[abs] & 0xFF);
                return;
            case "getInt16":
                vm.eax.write((int) readShort(dv.buffer.bytes, abs, le));
                return;
            case "getUint16":
                vm.eax.write(readShort(dv.buffer.bytes, abs, le) & 0xFFFF);
                return;
            case "getInt32":
                vm.eax.write(readInt(dv.buffer.bytes, abs, le));
                return;
            case "getUint32":
                vm.eax.write(readInt(dv.buffer.bytes, abs, le) & 0xFFFFFFFFL);
                return;
            case "getFloat32":
                vm.eax.write((double) Float.intBitsToFloat(readInt(dv.buffer.bytes, abs, le)));
                return;
            default:
                vm.eax.write(Double.longBitsToDouble(readLong(dv.buffer.bytes, abs, le)));
                return;
        }
    }

    /** DataView.prototype.setXxx(byteOffset, value[, littleEndian]) */
    private static void dvSet(Machine vm, String name, Object[] args) {
        DataViewValue dv = requireDv(vm);
        int off = args.length > 0 ? (int) OpUtils.toNumber(args[0]) : 0;
        double value = OpUtils.toNumber(args.length > 1 ? args[1] : Undefined.value);
        boolean le = args.length > 2 && OpUtils.testValue(args[2]);
        int es = name.endsWith("8") ? 1 : (name.endsWith("16") ? 2 : 4);
        if (name.endsWith("64")) {
            es = 8;
        }
        int abs = dv.byteOffset + off;
        if (off < 0 || abs + es > dv.byteOffset + dv.byteLength) {
            throw new NgScriptRuntimeException(vm, "DataView write out of bounds");
        }
        switch (name) {
            case "setInt8":
            case "setUint8":
                dv.buffer.bytes[abs] = (byte) (int) value;
                return;
            case "setInt16":
            case "setUint16":
                writeShort(dv.buffer.bytes, abs, (short) (int) value, le);
                return;
            case "setInt32":
            case "setUint32":
                writeInt(dv.buffer.bytes, abs, (int) value, le);
                return;
            case "setFloat32":
                writeInt(dv.buffer.bytes, abs, Float.floatToRawIntBits((float) value), le);
                return;
            default:
                writeLong(dv.buffer.bytes, abs, Double.doubleToRawLongBits(value), le);
                return;
        }
    }

    // little-endian byte helpers (shared with the typed array storage)
    private static short readShort(byte[] b, int off, boolean le) {
        if (le) {
            return (short) ((b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8));
        }
        return (short) (((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF));
    }

    private static int readInt(byte[] b, int off, boolean le) {
        if (le) {
            return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                    | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
        }
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    private static long readLong(byte[] b, int off, boolean le) {
        if (le) {
            return (readInt(b, off, true) & 0xFFFFFFFFL) | ((long) readInt(b, off + 4, true) << 32);
        }
        return ((readInt(b, off, false) & 0xFFFFFFFFL) << 32) | (readInt(b, off + 4, false) & 0xFFFFFFFFL);
    }

    private static void writeShort(byte[] b, int off, short v, boolean le) {
        if (le) {
            b[off] = (byte) (v & 0xFF);
            b[off + 1] = (byte) ((v >> 8) & 0xFF);
        } else {
            b[off] = (byte) ((v >> 8) & 0xFF);
            b[off + 1] = (byte) (v & 0xFF);
        }
    }

    private static void writeInt(byte[] b, int off, int v, boolean le) {
        if (le) {
            b[off] = (byte) (v & 0xFF);
            b[off + 1] = (byte) ((v >> 8) & 0xFF);
            b[off + 2] = (byte) ((v >> 16) & 0xFF);
            b[off + 3] = (byte) ((v >> 24) & 0xFF);
        } else {
            b[off] = (byte) ((v >> 24) & 0xFF);
            b[off + 1] = (byte) ((v >> 16) & 0xFF);
            b[off + 2] = (byte) ((v >> 8) & 0xFF);
            b[off + 3] = (byte) (v & 0xFF);
        }
    }

    private static void writeLong(byte[] b, int off, long v, boolean le) {
        if (le) {
            writeInt(b, off, (int) v, true);
            writeInt(b, off + 4, (int) (v >> 32), true);
        } else {
            writeInt(b, off, (int) (v >> 32), false);
            writeInt(b, off + 4, (int) v, false);
        }
    }

    /** Register the typed array constructors into the global map. */
    public static void registerConstructors(java.util.Map<String, MemRef> map) {
        map.put("ArrayBuffer", new MemRef(new ArrayBufferCtor()));
        map.put("DataView", new MemRef(new DataViewCtor()));
        for (int k = 0; k < CTOR_NAMES.length; k++) {
            map.put(CTOR_NAMES[k], new MemRef(new TypedArrayCtor(k)));
        }
    }

    /** A simple iterator over an NgsArray (used by values/entries). */
    public static class ArrayIterator extends NgsObject implements java.util.Iterator<Object> {
        private final java.util.Iterator<Object> it;

        public ArrayIterator(Machine vm, NgsArray arr) {
            super(null);
            this.it = arr.iterator();
            setPrototype(Builtins.getObjectPrototype());
            set("next", (Invokable) (m, a) -> {
                NgsObject result = new NgsObject(null);
                result.setPrototype(Builtins.getObjectPrototype());
                if (it.hasNext()) {
                    result.set("value", it.next());
                    result.set("done", Boolean.FALSE);
                } else {
                    result.set("value", Undefined.value);
                    result.set("done", Boolean.TRUE);
                }
                m.eax.write(result);
            });
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public Object next() {
            return it.next();
        }
    }
}
