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

/**
 * @author wssccc
 */
public class VmMemRef {

    private Object value;

    public VmMemRef() {
        this.value = null;
    }

    public VmMemRef(Object obj_v) {
        this.value = obj_v;
    }

    public void write(Object v) {
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
