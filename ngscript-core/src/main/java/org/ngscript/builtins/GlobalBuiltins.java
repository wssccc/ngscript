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

import java.io.PrintWriter;
import java.util.Map;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.Undefined;

/**
 * ES global functions: isNaN/isFinite/parseInt/parseFloat/eval and the URI
 * codecs, plus the {@code globalThis} reference (the shared builtins
 * baseline environment).
 *
 * @author wssccc
 */
public class GlobalBuiltins {

    static void register(Map<String, MemRef> map, PrintWriter out) {
        map.put("isNaN", new MemRef((Invokable) (vm, args) ->
                vm.eax.write(Double.isNaN(OpUtils.toNumber(arg(args, 0))))));
        map.put("isFinite", new MemRef((Invokable) (vm, args) -> {
            double d = OpUtils.toNumber(arg(args, 0));
            vm.eax.write(!Double.isNaN(d) && !Double.isInfinite(d));
        }));
        map.put("parseInt", new MemRef((Invokable) (vm, args) ->
                vm.eax.write(parseInt(arg(args, 0), args.length > 1 ? args[1] : null))));
        map.put("parseFloat", new MemRef((Invokable) (vm, args) ->
                vm.eax.write(parseFloat(arg(args, 0)))));
        map.put("eval", new MemRef((Invokable) (vm, args) -> {
            Object code = arg(args, 0);
            String src = code instanceof String ? (String) code
                    : OpUtils.toStringValue(code);
            // direct eval shares the caller's environment: pass the current
            // env so eval can see (and declare into) the caller's scope
            vm.eax.write(Machine.evalHook == null ? Undefined.value
                    : Machine.evalHook.apply(src, (NgsObject) vm.env.read()));
        }));
        map.put("decodeURI", new MemRef((Invokable) (vm, args) ->
                vm.eax.write(decodeUri(OpUtils.toStringValue(arg(args, 0)), false))));
        map.put("decodeURIComponent", new MemRef((Invokable) (vm, args) ->
                vm.eax.write(decodeUri(OpUtils.toStringValue(arg(args, 0)), true))));
        map.put("encodeURI", new MemRef((Invokable) (vm, args) ->
                vm.eax.write(encodeUri(OpUtils.toStringValue(arg(args, 0)), false))));
        map.put("encodeURIComponent", new MemRef((Invokable) (vm, args) ->
                vm.eax.write(encodeUri(OpUtils.toStringValue(arg(args, 0)), true))));
        map.put("undefined", new MemRef(Undefined.value));
        // globalThis: the shared builtins baseline (host objects persist
        // across script executions, like the ES global object)
        map.put("globalThis", new MemRef(new NgsObject(null)) {
            @Override
            public Object read() {
                return Machine.globalThis;
            }

            @Override
            public void write(Object value) {
                throw new UnsupportedOperationException("globalThis is read-only");
            }
        });
    }

    private static Object arg(Object[] args, int i) {
        return args.length > i ? args[i] : Undefined.value;
    }

    //----------------------------------------------------------------------
    // parseInt / parseFloat (ES semantics)
    //----------------------------------------------------------------------

    /** ES parseInt (shared with Number.parseInt). */
    public static Object parseIntValue(Object value, Object radixArg) {
        return parseInt(value, radixArg);
    }

    /** ES parseFloat (shared with Number.parseFloat). */
    public static Object parseFloatValue(Object value) {
        return parseFloat(value);
    }

    private static Object parseInt(Object value, Object radixArg) {
        String s = OpUtils.toStringValue(value).trim();
        int sign = 1;
        int i = 0;
        if (s.length() > 0 && (s.charAt(0) == '-' || s.charAt(0) == '+')) {
            if (s.charAt(0) == '-') {
                sign = -1;
            }
            i = 1;
        }
        int radix = 10;
        boolean radixGiven = false;
        if (radixArg != null && radixArg != Undefined.value) {
            double r = OpUtils.toNumber(radixArg);
            if (!Double.isNaN(r)) {
                radixGiven = true;
                radix = (int) r;
                if (radix != 0 && (radix < 2 || radix > 36)) {
                    return Double.NaN;
                }
            }
        }
        boolean stripPrefix = !radixGiven || radix == 0 || radix == 16;
        if (stripPrefix && s.length() - i >= 2 && s.charAt(i) == '0'
                && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
            radix = 16;
            i += 2;
        } else if (radix == 0) {
            radix = 10;
        }
        StringBuilder digits = new StringBuilder();
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = Character.digit(c, radix);
            if (d < 0) {
                break;
            }
            digits.append(c);
        }
        if (digits.length() == 0) {
            return Double.NaN;
        }
        try {
            long v = Long.parseLong(digits.toString(), radix);
            return OpUtils.normalize(sign * (double) v);
        } catch (NumberFormatException e) {
            // overflow: fall back to a double parse
            double v = 0;
            try {
                v = Double.parseDouble(sign + "0x" + digits + (radix == 16 ? "" : ""));
            } catch (NumberFormatException e2) {
                v = Double.NaN;
            }
            return v;
        }
    }

    private static Object parseFloat(Object value) {
        String s = OpUtils.toStringValue(value).trim();
        int i = 0;
        if (s.length() > 0 && (s.charAt(0) == '+' || s.charAt(0) == '-')) {
            i = 1;
        }
        StringBuilder num = new StringBuilder();
        boolean dot = false;
        boolean exp = false;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                num.append(c);
            } else if (c == '.' && !dot && !exp) {
                dot = true;
                num.append(c);
            } else if ((c == 'e' || c == 'E') && num.length() > 0 && !exp) {
                // exponent: only when followed by a digit or sign+digit
                int j = i + 1;
                if (j < s.length() && (s.charAt(j) == '+' || s.charAt(j) == '-')) {
                    j++;
                }
                if (j < s.length() && Character.isDigit(s.charAt(j))) {
                    exp = true;
                    num.append(c);
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        if (num.length() == 0) {
            return Double.NaN;
        }
        try {
            double d = Double.parseDouble(s.substring(0, i));
            return OpUtils.normalize(d);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    //----------------------------------------------------------------------
    // URI codecs
    //----------------------------------------------------------------------

    private static boolean unreserved(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.'
                || c == '!' || c == '~' || c == '*' || c == '\'' || c == '(' || c == ')';
    }

    private static String encodeUri(String s, boolean component) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (unreserved(c) || (!component && ";/?:@&=+$,#".indexOf(c) >= 0)) {
                sb.append(c);
            } else if (c < 0x80) {
                sb.append(String.format("%%%02X", (int) c));
            } else {
                // encode the UTF-8 bytes of a code point
                int cp = Character.isHighSurrogate(c) && i + 1 < s.length()
                        && Character.isLowSurrogate(s.charAt(i + 1))
                        ? Character.toCodePoint(c, s.charAt(++i)) : c;
                for (byte b : new String(Character.toChars(cp)).getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                    sb.append(String.format("%%%02X", b & 0xFF));
                }
            }
        }
        return sb.toString();
    }

    private static String decodeUri(String s, boolean component) {
        StringBuilder sb = new StringBuilder();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%') {
                if (i + 2 >= s.length()) {
                    bytes.write((int) c);
                    continue;
                }
                int h = Character.digit(s.charAt(i + 1), 16);
                int l = Character.digit(s.charAt(i + 2), 16);
                if (h < 0 || l < 0) {
                    bytes.write((int) c);
                    continue;
                }
                bytes.write((h << 4) | l);
                i += 2;
            } else {
                if (bytes.size() > 0) {
                    sb.append(new String(bytes.toByteArray(),
                            java.nio.charset.StandardCharsets.UTF_8));
                    bytes.reset();
                }
                sb.append(c);
            }
        }
        if (bytes.size() > 0) {
            sb.append(new String(bytes.toByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
