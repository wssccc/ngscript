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

import org.ngscript.builtins.Builtins;
import org.ngscript.value.MemRef;
import org.ngscript.value.Undefined;

/**
 * An ES typed array: a view into an {@link ArrayBufferValue} with a fixed
 * element type (see {@link #KIND_*}). Elements are stored in the buffer
 * using the native little-endian layout of the JVM; element reads/writes
 * go through the numeric conversion for the element type.
 *
 * @author wssccc
 */
public class TypedArrayValue extends NgsObject {

    public static final int KIND_INT8 = 0;
    public static final int KIND_UINT8 = 1;
    public static final int KIND_UINT8C = 2;
    public static final int KIND_INT16 = 3;
    public static final int KIND_UINT16 = 4;
    public static final int KIND_INT32 = 5;
    public static final int KIND_UINT32 = 6;
    public static final int KIND_FLOAT32 = 7;
    public static final int KIND_FLOAT64 = 8;
    public static final int KIND_BIGINT64 = 9;
    public static final int KIND_BIGUINT64 = 10;

    private static final int[] ELEMENT_SIZES = {1, 1, 1, 2, 2, 4, 4, 4, 8, 8, 8};

    /** The kind of elements stored in this view. */
    public final int kind;
    /** The backing buffer. */
    public final ArrayBufferValue buffer;
    /** Byte offset of this view into the buffer. */
    public final int byteOffset;
    /** Number of elements in this view. */
    public final int length;

    public TypedArrayValue(int kind, ArrayBufferValue buffer, int byteOffset, int length) {
        super(null);
        this.kind = kind;
        this.buffer = buffer;
        this.byteOffset = byteOffset;
        this.length = length;
        setPrototype(Builtins.getObjectPrototype());
    }

    /** The byte size of one element for the given kind. */
    public static int elementSize(int kind) {
        return ELEMENT_SIZES[kind];
    }

    /** The number of bytes this view covers. */
    public int byteLength() {
        return length * ELEMENT_SIZES[kind];
    }

    /** Reads the element at the given index (no bounds check). */
    public Object readElement(int i) {
        int off = byteOffset + i * ELEMENT_SIZES[kind];
        switch (kind) {
            case KIND_INT8:
                return (int) buffer.bytes[off];
            case KIND_UINT8:
                return buffer.bytes[off] & 0xFF;
            case KIND_UINT8C:
                return buffer.bytes[off] & 0xFF;
            case KIND_INT16:
                return (int) readShort(off);
            case KIND_UINT16:
                return readShort(off) & 0xFFFF;
            case KIND_INT32:
                return readInt(off);
            case KIND_UINT32:
                return readInt(off) & 0xFFFFFFFFL;
            case KIND_FLOAT32:
                return (double) Float.intBitsToFloat(readInt(off));
            case KIND_BIGINT64:
                return org.ngscript.value.BigIntValue.of(readLong(off));
            case KIND_BIGUINT64:
                return org.ngscript.value.BigIntValue.of(readLong(off));
            default:
                return Double.longBitsToDouble(readLong(off));
        }
    }

    /** Writes the element at the given index, converting per the kind. */
    public void writeElement(int i, Object v) {
        int off = byteOffset + i * ELEMENT_SIZES[kind];
        double d = OpUtils.toNumber(v);
        switch (kind) {
            case KIND_INT8:
                buffer.bytes[off] = (byte) (int) d;
                break;
            case KIND_UINT8:
                buffer.bytes[off] = (byte) (int) d;
                break;
            case KIND_UINT8C:
                buffer.bytes[off] = (byte) clampUint8(d);
                break;
            case KIND_INT16:
                writeShort(off, (short) (int) d);
                break;
            case KIND_UINT16:
                writeShort(off, (short) (int) d);
                break;
            case KIND_INT32:
                writeInt(off, (int) d);
                break;
            case KIND_UINT32:
                // the double may exceed Integer.MAX_VALUE: go through long
                // so the bit pattern is preserved (4294967295 -> 0xFFFFFFFF)
                writeInt(off, (int) (long) d);
                break;
            case KIND_FLOAT32:
                writeInt(off, Float.floatToRawIntBits((float) d));
                break;
            case KIND_BIGINT64:
            case KIND_BIGUINT64:
                // BigInt elements: store the raw 64-bit two's-complement
                // pattern (ToBigInt64/ToBigUint64 truncate modulo 2^64)
                if (v instanceof org.ngscript.value.BigIntValue) {
                    writeLong(off, ((org.ngscript.value.BigIntValue) v).value.longValue());
                } else {
                    writeLong(off, (long) d);
                }
                break;
            default:
                writeLong(off, Double.doubleToRawLongBits(d));
                break;
        }
    }

    /** ES ToUint8Clamp. */
    private static int clampUint8(double d) {
        if (Double.isNaN(d)) {
            return 0;
        }
        if (d <= 0) {
            return 0;
        }
        if (d >= 255) {
            return 255;
        }
        int floor = (int) d;
        double frac = d - floor;
        if (frac > 0.5) {
            return floor + 1;
        }
        if (frac < 0.5) {
            return floor;
        }
        // 0.5 rounds to even
        return (floor % 2 == 0) ? floor : floor + 1;
    }

    private short readShort(int off) {
        return (short) (((buffer.bytes[off] & 0xFF)) | ((buffer.bytes[off + 1] & 0xFF) << 8));
    }

    private int readInt(int off) {
        return (buffer.bytes[off] & 0xFF)
                | ((buffer.bytes[off + 1] & 0xFF) << 8)
                | ((buffer.bytes[off + 2] & 0xFF) << 16)
                | ((buffer.bytes[off + 3] & 0xFF) << 24);
    }

    private long readLong(int off) {
        return (readInt(off) & 0xFFFFFFFFL) | ((long) readInt(off + 4) << 32);
    }

    private void writeShort(int off, short v) {
        buffer.bytes[off] = (byte) (v & 0xFF);
        buffer.bytes[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    private void writeInt(int off, int v) {
        buffer.bytes[off] = (byte) (v & 0xFF);
        buffer.bytes[off + 1] = (byte) ((v >> 8) & 0xFF);
        buffer.bytes[off + 2] = (byte) ((v >> 16) & 0xFF);
        buffer.bytes[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private void writeLong(int off, long v) {
        writeInt(off, (int) v);
        writeInt(off + 4, (int) (v >> 32));
    }

    @Override
    public MemRef lookup(String member, Machine vm, boolean isMember) {
        try {
            final int i = Integer.parseInt(member);
            if (i >= 0 && i < length) {
                return new MemRef(readElement(i)) {
                    @Override
                    public void write(Object v) {
                        writeElement(i, v);
                    }
                };
            }
            return new MemRef(Undefined.value);
        } catch (NumberFormatException e) {
            // fall through to normal property access
        }
        switch (member) {
            case "length":
                return new MemRef(length);
            case "byteLength":
                return new MemRef(byteLength());
            case "byteOffset":
                return new MemRef(byteOffset);
            case "buffer":
                return new MemRef(buffer);
            case "@@iterator":
                return new MemRef((org.ngscript.value.Invokable) (m, a) -> {
                    m.eax.write(new org.ngscript.builtins.TypedArrayBuiltins.ArrayIterator(m, toArray()));
                });
        }
        return super.lookup(member, vm, isMember);
    }

    /** Copies this view's elements into a fresh NgsArray. */
    public NgsArray toArray() {
        NgsArray arr = new NgsArray(null);
        arr.setPrototype(Builtins.getArrayPrototype());
        for (int i = 0; i < length; i++) {
            arr.set("" + i, readElement(i));
        }
        return arr;
    }
}
