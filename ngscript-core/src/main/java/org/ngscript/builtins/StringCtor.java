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
import org.ngscript.runtime.NgsArray;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;

/**
 * The ES {@code String} constructor: {@code String(x)} / {@code new
 * String(x)} coerce to a string primitive (the engine stores strings as
 * Java String values, so there is no boxed object). The static methods
 * fromCharCode/fromCodePoint/raw are dispatched from
 * {@link org.ngscript.runtime.OpObject#member_ref}.
 *
 * @author wssccc
 */
public class StringCtor implements Invokable {

    @Override
    public void invoke(Machine vm, Object[] args) {
        vm.eax.write(args.length > 0 ? OpUtils.toStringValue(args[0]) : "");
    }

    public static MemRef lookup(StringCtor ctor, String member, Machine vm) {
        switch (member) {
            case "fromCharCode":
                return new MemRef((Invokable) (m, args) -> {
                    StringBuilder sb = new StringBuilder(args.length);
                    for (Object a : args) {
                        sb.append((char) ((int) OpUtils.toNumber(a) & 0xFFFF));
                    }
                    m.eax.write(sb.toString());
                });
            case "fromCodePoint":
                return new MemRef((Invokable) (m, args) -> {
                    StringBuilder sb = new StringBuilder(args.length);
                    for (Object a : args) {
                        int cp = (int) OpUtils.toNumber(a);
                        if (cp < 0 || cp > 0x10FFFF) {
                            throw new IndexOutOfBoundsException("Invalid code point " + cp);
                        }
                        sb.appendCodePoint(cp);
                    }
                    m.eax.write(sb.toString());
                });
            case "raw":
                return new MemRef((Invokable) (m, args) -> {
                    Object template = args.length > 0 ? args[0] : null;
                    if (template == null || template == org.ngscript.value.Undefined.value) {
                        throw new IllegalArgumentException("String.raw: template is not an object");
                    }
                    Object raw = OpUtils.getMember(m, template, "raw");
                    if (raw == null || raw == org.ngscript.value.Undefined.value) {
                        throw new IllegalArgumentException("String.raw: template.raw is not an object");
                    }
                    NgsObject rawArr = (NgsObject) raw;
                    StringBuilder sb = new StringBuilder();
                    int n = rawArr instanceof NgsArray ? ((NgsArray) rawArr).length()
                            : rawArr.keys().size();
                    for (int i = 0; i < n; i++) {
                        Object seg = rawArr.get("" + i);
                        sb.append(seg == null ? "undefined" : OpUtils.toStringValue(seg));
                        if (i + 1 < args.length) {
                            sb.append(OpUtils.toStringValue(args[i + 1]));
                        } else if (i + 1 < n) {
                            // a substitution is required between raw segments
                            throw new IllegalArgumentException("String.raw: not enough arguments");
                        }
                    }
                    m.eax.write(sb.toString());
                });
            default:
                return null;
        }
    }
}
