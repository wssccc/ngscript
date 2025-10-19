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

/**
 * An ES {@code ArrayBuffer}: a raw byte buffer with the byteLength property.
 * The buffer itself is backed by a byte array; typed arrays and data views
 * read and write into it.
 *
 * @author wssccc
 */
public class ArrayBufferValue extends NgsObject {

    /** The raw bytes. */
    public final byte[] bytes;

    public ArrayBufferValue(int byteLength) {
        super(null);
        setPrototype(Builtins.getObjectPrototype());
        this.bytes = new byte[byteLength];
    }

    @Override
    public MemRef lookup(String member, Machine vm, boolean isMember) {
        if (member.equals("byteLength")) {
            return new MemRef(bytes.length);
        }
        return super.lookup(member, vm, isMember);
    }

    /** ArrayBuffer.prototype.slice(begin, end): a new buffer with the copy. */
    public ArrayBufferValue slice(int begin, int end) {
        int lo = Math.max(0, begin);
        int hi = Math.min(bytes.length, end < 0 ? bytes.length + end : end);
        if (hi < lo) {
            hi = lo;
        }
        ArrayBufferValue out = new ArrayBufferValue(hi - lo);
        System.arraycopy(bytes, lo, out.bytes, 0, hi - lo);
        return out;
    }
}
