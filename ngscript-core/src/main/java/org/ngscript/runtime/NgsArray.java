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
 * ({@code "0"}..{@code "n-1"}).
 *
 * @author wssccc
 */
public class NgsArray extends NgsObject {

    public NgsArray(NgsObject parent) {
        super(parent);
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
}
