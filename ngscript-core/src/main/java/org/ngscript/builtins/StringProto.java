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

import java.util.LinkedHashMap;
import java.util.Map;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgScriptRuntimeException;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.Undefined;

/**
 * The ES {@code String.prototype} methods that Java's String cannot serve
 * directly (includes/repeat/startsWith/endsWith/codePointAt). Methods are
 * dispatched from {@link org.ngscript.runtime.OpObject#member_ref} before
 * the Java reflection fallback (charAt/substring/indexOf/... stay native).
 * The receiver arrives through the {@code this} binding and must be a
 * String.
 *
 * @author wssccc
 */
public class StringProto {

    private static final Map<String, Invokable> METHODS = new LinkedHashMap<>();

    static {
        register("includes", (vm, args) -> {
            String self = self(vm);
            String search = args.length > 0 ? OpUtils.toStringValue(args[0]) : "undefined";
            vm.eax.write(self.indexOf(search, toIndex(args, 1)) >= 0);
        });
        register("startsWith", (vm, args) -> {
            String self = self(vm);
            String search = args.length > 0 ? OpUtils.toStringValue(args[0]) : "undefined";
            int pos = toIndex(args, 1);
            vm.eax.write(self.startsWith(search, pos));
        });
        register("endsWith", (vm, args) -> {
            String self = self(vm);
            String search = args.length > 0 ? OpUtils.toStringValue(args[0]) : "undefined";
            int end = args.length > 1 ? toIndex(args, 1) : self.length();
            vm.eax.write(self.regionMatches(end - search.length(), search, 0, search.length()));
        });
        register("repeat", (vm, args) -> {
            String self = self(vm);
            double count = args.length > 0 ? OpUtils.toNumber(args[0]) : 0;
            if (count < 0 || Double.isInfinite(count)) {
                throw new IndexOutOfBoundsException("Invalid count value");
            }
            int n = (int) count;
            StringBuilder sb = new StringBuilder(self.length() * n);
            for (int i = 0; i < n; i++) {
                sb.append(self);
            }
            vm.eax.write(sb.toString());
        });
        register("codePointAt", (vm, args) -> {
            String self = self(vm);
            int pos = toIndex(args, 0);
            if (pos < 0 || pos >= self.length()) {
                vm.eax.write(Undefined.value);
                return;
            }
            char c = self.charAt(pos);
            if (Character.isHighSurrogate(c) && pos + 1 < self.length()
                    && Character.isLowSurrogate(self.charAt(pos + 1))) {
                vm.eax.write(Character.toCodePoint(c, self.charAt(pos + 1)));
            } else {
                vm.eax.write((int) c);
            }
        });
        register("codePointBefore", (vm, args) -> {
            String self = self(vm);
            int pos = toIndex(args, 0);
            if (pos <= 0 || pos > self.length()) {
                vm.eax.write(Undefined.value);
                return;
            }
            char c = self.charAt(pos - 1);
            if (Character.isLowSurrogate(c) && pos - 2 >= 0
                    && Character.isHighSurrogate(self.charAt(pos - 2))) {
                vm.eax.write(Character.toCodePoint(self.charAt(pos - 2), c));
            } else {
                vm.eax.write((int) c);
            }
        });
        register("trimStart", (vm, args) -> {
            String self = self(vm);
            int i = 0;
            while (i < self.length() && Character.isWhitespace(self.charAt(i))) {
                i++;
            }
            vm.eax.write(self.substring(i));
        });
        register("trimEnd", (vm, args) -> {
            String self = self(vm);
            int i = self.length();
            while (i > 0 && Character.isWhitespace(self.charAt(i - 1))) {
                i--;
            }
            vm.eax.write(self.substring(0, i));
        });
        // ES string iteration by code point (for-of / spread already use a
        // code-point iterator; Symbol.iterator mirrors it)
        register("@@iterator", (vm, args) -> {
            String self = self(vm);
            vm.eax.write(new org.ngscript.runtime.OpObject.CodePointIterator(self));
        });
    }

    private static void register(String name, Invokable inv) {
        METHODS.put(name, inv);
    }

    private static String self(Machine vm) throws NgScriptRuntimeException {
        Object t = vm.thisRef.read();
        if (t instanceof String) {
            return (String) t;
        }
        throw new NgScriptRuntimeException(vm, "String method called on a non-string value");
    }

    /** ES ToInteger for positions (NaN -> 0, negatives stay negative). */
    private static int toIndex(Object[] args, int i) {
        if (args.length <= i || args[i] == null) {
            return 0;
        }
        double d = OpUtils.toNumber(args[i]);
        if (Double.isNaN(d)) {
            return 0;
        }
        return (int) d;
    }

    /**
     * Looks up a String.prototype method; null when absent (the caller falls
     * back to Java reflection).
     */
    public static MemRef lookup(String member, Machine vm) {
        Invokable inv = METHODS.get(member);
        return inv == null ? null : new MemRef(inv);
    }
}
