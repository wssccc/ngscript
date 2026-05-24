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

import org.ngscript.value.MemRef;

/**
 * An array literal: an {@link NgsObject} indexed by stringified integers
 * ({@code "0"}..{@code "n-1"}). Also acts as its own java.util.Iterator
 * (the for-of loop and Array.prototype[Symbol.iterator] use it directly).
 *
 * @author wssccc
 */
public class NgsArray extends NgsObject implements java.util.Iterator<Object>, java.lang.Iterable<Object> {

    private int iterIndex;

    public NgsArray(NgsObject parent) {
        super(parent);
    }

    @Override
    public java.util.Iterator<Object> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return iterIndex < length();
    }

    @Override
    public Object next() {
        Object v = get("" + iterIndex);
        ++iterIndex;
        return v;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    /** The virtual length: one past the highest numeric index. */
    public int length() {
        Object cached = get("length");
        if (cached instanceof Integer) {
            return (Integer) cached;
        }
        int len = 0;
        for (String key : keys()) {
            try {
                len = Math.max(len, Integer.parseInt(key) + 1);
            } catch (NumberFormatException ignored) {
                // non-numeric keys do not affect length
            }
        }
        return len;
    }

    /** Resets the iteration cursor (arrays iterate from index 0). */
    public void resetIterator() {
        iterIndex = 0;
    }

    @Override
    public void set(String member, Object value) {
        super.set(member, value);
        if ("length".equals(member)) {
            // an explicit length (new Array(n), Array.prototype.length =
            // v): keep it when it covers the highest numeric index (ES
            // length is always one past the highest index); a write that
            // would leave holes below the max invalidates the cache so
            // the virtual length recomputes
            Object cached = get("length");
            if (cached instanceof Integer) {
                int len = (Integer) cached;
                int max = 0;
                for (String key : keys()) {
                    try {
                        max = Math.max(max, Integer.parseInt(key) + 1);
                    } catch (NumberFormatException ignored) {
                        // non-numeric keys do not affect length
                    }
                }
                if (len < max) {
                    remove("length");
                }
            }
            return;
        }
        if (isNumericKey(member)) {
            // invalidate the cached virtual length
            remove("length");
        }
    }

    @Override
    public boolean remove(String key) {
        if (isNumericKey(key)) {
            remove("length");
        }
        return super.remove(key);
    }

    /** True for all-digit keys (array indices), used by length tracking. */
    static boolean isNumericKey(String key) {
        if (key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public MemRef lookup(String member, Machine vm, boolean isMember) throws NgScriptRuntimeException {
        if ("length".equals(member)) {
            // virtual length: one past the highest numeric index
            MemRef ref = getRef(member);
            if (ref == null) {
                int len = 0;
                for (String key : keys()) {
                    try {
                        len = Math.max(len, Integer.parseInt(key) + 1);
                    } catch (NumberFormatException ignored) {
                        // non-numeric keys do not affect length
                    }
                }
                ref = new MemRef(len);
                define(member);
                set(member, len);
            }
            return ref;
        }
        return super.lookup(member, vm, isMember);
    }

    @Override
    public MemRef lookupForWrite(String member, Machine vm) throws NgScriptRuntimeException {
        if (!"length".equals(member) && isNumericKey(member)) {
            // a numeric index write must go through set() so the virtual
            // length cache invalidates (a[3] = v grows a.length); the
            // writability/private-write checks apply as for any data key
            MemRef ref = dataGet(member);
            if (ref != null) {
                if (isWriteThrows(member)) {
                    throw new IllegalArgumentException(
                            "Cannot write to private method '" + member + "'");
                }
                if (isNonWritable(member)) {
                    // non-writable property: writes are silently ignored
                    return new MemRef(ref.read());
                }
                return ref;
            }
            final String key = member;
            return new MemRef(org.ngscript.value.Undefined.value) {
                @Override
                public void write(Object v) {
                    set(key, v);
                }
            };
        }
        return super.lookupForWrite(member, vm);
    }
}
