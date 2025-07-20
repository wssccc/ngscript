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
import org.ngscript.value.Undefined;

/**
 * @author wssccc
 */
public class OpUtils {

    static Object[] get2OpParam(Machine vm) {
        Object[] objects = new Object[2];
        objects[0] = vm.stack.pop();
        objects[1] = vm.eax.read();
        return objects;
    }

    static double getNumber(Machine vm, Object obj) throws NgScriptRuntimeException {
        return toNumber(obj);
    }

    static int getInteger(Machine vm, Object obj) throws NgScriptRuntimeException {
        return toInt32(obj);
    }

    //----------------------------------------------------------------------
    // ECMAScript value conversion primitives
    //----------------------------------------------------------------------

    /** Whether a value is an internal numeric representation (a JS Number). */
    static boolean isNumber(Object obj) {
        return obj instanceof Integer || obj instanceof Double || obj instanceof Long;
    }

    /** ES ToNumber. Objects require ToPrimitive (arrives with the object model). */
    public static double toNumber(Object obj) {
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        if (obj instanceof Double) {
            return (Double) obj;
        }
        if (obj instanceof Long) {
            return (Long) obj;
        }
        if (obj instanceof Boolean) {
            return ((Boolean) obj) ? 1 : 0;
        }
        if (obj instanceof String) {
            return stringToNumber((String) obj);
        }
        if (obj == null) {
            return 0;
        }
        if (obj == Undefined.value) {
            return Double.NaN;
        }
        // host objects / NgsObject: ToPrimitive then ToNumber (Phase 2.3)
        return Double.NaN;
    }

    private static double stringToNumber(String s) {
        s = s.trim();
        if (s.isEmpty()) {
            return 0;
        }
        if ("Infinity".equals(s) || "+Infinity".equals(s)) {
            return Double.POSITIVE_INFINITY;
        }
        if ("-Infinity".equals(s)) {
            return Double.NEGATIVE_INFINITY;
        }
        if (s.length() > 2 && s.charAt(0) == '0' && (s.charAt(1) == 'x' || s.charAt(1) == 'X')) {
            try {
                return Long.parseLong(s.substring(2), 16);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** ES ToInt32. */
    static int toInt32(Object obj) {
        double d = toNumber(obj);
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return 0;
        }
        double m = d % 4294967296.0;
        if (m < 0) {
            m += 4294967296.0;
        }
        return (int) (long) m;
    }

    /** ES ToString. */
    public static String toStringValue(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj == Undefined.value) {
            return "undefined";
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Integer) {
            return Integer.toString((Integer) obj);
        }
        if (obj instanceof Double) {
            return numberToString((Double) obj);
        }
        if (obj instanceof Long) {
            return Long.toString((Long) obj);
        }
        return obj.toString();
    }

    /** ES Number::toString (shortest decimal, no trailing .0 for integers). */
    static String numberToString(double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (d == Double.POSITIVE_INFINITY) {
            return "Infinity";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "-Infinity";
        }
        if (d == Math.floor(d) && Math.abs(d) < 1e21) {
            return new java.math.BigDecimal(d).toBigInteger().toString();
        }
        return Double.toString(d);
    }

    /**
     * Keeps the internal Integer representation when a double result is an
     * integral value within the int range (like V8's Smi optimization).
     */
    static Object normalize(double d) {
        if (!Double.isInfinite(d) && d == Math.floor(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
            return (int) d;
        }
        return d;
    }

    /** ES {@code ==} (abstract equality). */
    static boolean abstractEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null) {
            return b == Undefined.value;
        }
        if (b == null) {
            return a == Undefined.value;
        }
        if (a == Undefined.value) {
            return b == null;
        }
        if (b == Undefined.value) {
            return a == null;
        }
        if (isNumber(a) && isNumber(b)) {
            return numericEquals(toNumber(a), toNumber(b));
        }
        if (a instanceof String && b instanceof String) {
            return a.equals(b);
        }
        if (a instanceof Boolean && b instanceof Boolean) {
            return a.equals(b);
        }
        if (isNumber(a) && b instanceof String) {
            return numericEquals(toNumber(a), toNumber(b));
        }
        if (a instanceof String && isNumber(b)) {
            return numericEquals(toNumber(a), toNumber(b));
        }
        if (a instanceof Boolean) {
            return abstractEquals(toNumber(a), b);
        }
        if (b instanceof Boolean) {
            return abstractEquals(a, toNumber(b));
        }
        // object vs primitive requires ToPrimitive (Phase 2.3); identity handled above
        return false;
    }

    /** ES {@code ===} (strict equality). Integer and Double are the same JS type. */
    public static boolean strictEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (isNumber(a) && isNumber(b)) {
            return numericEquals(toNumber(a), toNumber(b));
        }
        if (a instanceof String && b instanceof String) {
            return a.equals(b);
        }
        if (a instanceof Boolean && b instanceof Boolean) {
            return a.equals(b);
        }
        return false;
    }

    private static boolean numericEquals(double a, double b) {
        return a == b; // NaN != NaN and +0 == -0 come for free
    }

    /**
     * Legacy name kept for callers; now the ES abstract equality.
     */
    static boolean testEq(Object obj1, Object obj2) {
        return abstractEquals(obj1, obj2);
    }

    public static boolean testValue(Object testObj) {
        boolean val = false;
        if (testObj == null) {
            val = false;
        } else if (testObj instanceof Boolean) {
            val = ((Boolean) testObj);
        } else if (testObj instanceof Integer) {
            val = ((Integer) testObj) != 0;
        } else if (testObj instanceof Double) {
            double d = (Double) testObj;
            val = d != 0 && !Double.isNaN(d); // -0 and NaN are falsy
        } else if (testObj instanceof Undefined) {
            val = false;
        } else {
            //is an object
            val = true;
        }
        return val;
    }

    /**
     * Increments/decrements the value behind eax's MemRef using ES ToNumber
     * semantics; results keep the Integer representation when integral.
     */
    static void addEax(Machine vm, int num, boolean rewrite) {
        MemRef addr = (MemRef) vm.eax.read();
        Object val = addr.read();
        double d = toNumber(val) + num;
        Object result = normalize(d);
        addr.write(result);
        if (rewrite) {
            // postfix: the expression value is the old value (as a number)
            vm.eax.write(normalize(toNumber(val)));
        } else {
            vm.eax.write(result);
        }
    }

    static String getObjInfo(Object obj) {
        if (obj != null) {
            return obj.getClass().getName() + "[" + obj.toString() + "]";
        } else {
            return "null";
        }
    }
}
