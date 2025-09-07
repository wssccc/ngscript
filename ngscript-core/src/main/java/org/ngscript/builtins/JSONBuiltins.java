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

import java.util.IdentityHashMap;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgScriptRuntimeException;
import org.ngscript.runtime.NgsArray;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.Undefined;

/**
 * The ES {@code JSON} object: stringify and parse with the standard value
 * conversion rules (cycles reject with TypeError, NaN/Infinity become null,
 * object keys are enumerated in insertion order).
 *
 * @author wssccc
 */
public class JSONBuiltins {

    /** JSON is a plain object, not a constructor. */
    public static class Json implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            throw new NgScriptRuntimeException(vm, "JSON is not a constructor");
        }
    }

    public static MemRef lookup(Json json, String member, Machine vm) {
        switch (member) {
            case "stringify":
                return new MemRef((Invokable) (m, args) -> {
                    StringBuilder sb = new StringBuilder();
                    stringify(m, args.length > 0 ? args[0] : null,
                            new IdentityHashMap<>(), sb);
                    m.eax.write(sb.toString());
                });
            case "parse":
                return new MemRef((Invokable) (m, args) -> {
                    String text = args.length > 0 ? OpUtils.toStringValue(args[0]) : "";
                    m.eax.write(new Parser(text).parseValue());
                });
            default:
                return null;
        }
    }

    //----------------------------------------------------------------------
    // stringify
    //----------------------------------------------------------------------

    private static void stringify(Machine vm, Object v, IdentityHashMap<Object, Boolean> seen,
                                  StringBuilder sb) throws NgScriptRuntimeException {
        if (v == null || v == Undefined.value) {
            sb.append("null");
            return;
        }
        if (v instanceof String) {
            quote((String) v, sb);
            return;
        }
        if (v instanceof Boolean) {
            sb.append(v);
            return;
        }
        if (OpUtils.isNumber(v)) {
            double d = OpUtils.toNumber(v);
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else {
                sb.append(OpUtils.numberToString(d));
            }
            return;
        }
        if (v instanceof NgsArray) {
            if (seen.put(v, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Converting circular structure to JSON");
            }
            NgsArray arr = (NgsArray) v;
            sb.append('[');
            for (int i = 0; i < arr.length(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Object item = arr.get("" + i);
                if (item == null || item == Undefined.value
                        || item instanceof Invokable || item instanceof org.ngscript.value.ScriptFunction) {
                    sb.append("null");
                } else {
                    stringify(vm, item, seen, sb);
                }
            }
            sb.append(']');
            seen.remove(v);
            return;
        }
        if (v instanceof NgsObject) {
            if (seen.put(v, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Converting circular structure to JSON");
            }
            NgsObject obj = (NgsObject) v;
            sb.append('{');
            boolean first = true;
            for (String k : obj.keys()) {
                Object val = obj.get(k);
                if (val == null || val == Undefined.value
                        || val instanceof Invokable || val instanceof org.ngscript.value.ScriptFunction) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(k, sb);
                sb.append(':');
                stringify(vm, val, seen, sb);
            }
            sb.append('}');
            seen.remove(v);
            return;
        }
        // foreign Java values stringify as their toString
        quote(OpUtils.toStringValue(v), sb);
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04X", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    //----------------------------------------------------------------------
    // parse
    //----------------------------------------------------------------------

    private static class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object parseValue() {
            ws();
            if (i >= s.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            char c = s.charAt(i);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't') {
                expect("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                expect("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                expect("null");
                return null;
            }
            return parseNumber();
        }

        private void expect(String word) {
            if (!s.startsWith(word, i)) {
                throw new IllegalArgumentException("Unexpected token in JSON");
            }
            i += word.length();
        }

        private NgsObject parseObject() {
            NgsObject obj = new NgsObject(null);
            i++; // {
            ws();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return obj;
            }
            while (true) {
                ws();
                if (i >= s.length() || s.charAt(i) != '"') {
                    throw new IllegalArgumentException("Expected string key in JSON");
                }
                String key = parseString();
                ws();
                if (i >= s.length() || s.charAt(i) != ':') {
                    throw new IllegalArgumentException("Expected ':' in JSON");
                }
                i++;
                obj.set(key, parseValue());
                ws();
                if (i >= s.length()) {
                    throw new IllegalArgumentException("Unexpected end of JSON object");
                }
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                } else if (c == '}') {
                    i++;
                    return obj;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' in JSON");
                }
            }
        }

        private NgsArray parseArray() {
            NgsArray arr = new NgsArray(null);
            i++; // [
            ws();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return arr;
            }
            int idx = 0;
            while (true) {
                arr.set("" + idx++, parseValue());
                ws();
                if (i >= s.length()) {
                    throw new IllegalArgumentException("Unexpected end of JSON array");
                }
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                } else if (c == ']') {
                    i++;
                    return arr;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' in JSON");
                }
            }
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            i++; // opening quote
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (i >= s.length()) {
                        break;
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (i + 4 > s.length()) {
                                throw new IllegalArgumentException("Invalid \\u escape");
                            }
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid escape in JSON string");
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object parseNumber() {
            int start = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
                i++;
            }
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (i < s.length() && s.charAt(i) == '.') {
                i++;
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    i++;
                }
                while (i < s.length() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            try {
                double d = Double.parseDouble(s.substring(start, i));
                return OpUtils.normalize(d);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number in JSON");
            }
        }
    }
}
