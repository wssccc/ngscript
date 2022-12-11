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

package org.ngscript.runtime.vo;

import lombok.Getter;

/**
 * @author wssccc
 */
public class VmMemRef {

    private Object value;
    @Getter
    private final boolean immutable;

    public VmMemRef() {
        this(null, false);
    }

    public VmMemRef(Object obj) {
        this(obj, false);
    }

    public VmMemRef(Object value, boolean immutable) {
        this.value = value;
        this.immutable = immutable;
    }

    public void write(Object v) {
        if (immutable && value != null) {
            throw new RuntimeException("immutable");
        }
        this.value = v;
    }

    public Object read() {
        return this.value;
    }

    @Override
    public String toString() {
        return "VmMemRef{" + "value=" + value + '}';
    }

}
