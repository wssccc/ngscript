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

import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;
import org.ngscript.value.ScriptFunction;
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

    /**
     * ES ToPropertyKey on a value: primitives stringify directly; objects
     * go through ToPrimitive(hint String) — their toString method (or
     * valueOf, or the [[Prototype]] chain's), throwing a TypeError when
     * neither exists ({@code Object.create(null)} as a computed key).
     */
    public static String convertKey(Machine vm, Object v) {
        if (v instanceof NgsObject) {
            NgsObject obj = (NgsObject) v;
            // ToPrimitive(hint String): try toString, then valueOf (ES
            // OrdinaryToPrimitive); a missing pair is a TypeError
            for (String methodName : new String[]{"toString", "valueOf"}) {
                Object method = null;
                try {
                    MemRef ref = obj.lookup(methodName, vm, true);
                    method = ref.read();
                } catch (NgScriptRuntimeException ignored) {
                    // no method on the object or its prototype chain
                }
                if (method instanceof ScriptFunction || method instanceof Invokable) {
                    try {
                        return toStringValue(
                                callValue(vm, method, obj, new Object[0]));
                    } catch (Exception e) {
                        throw e instanceof RuntimeException
                                ? (RuntimeException) e
                                : new RuntimeException(e.getMessage() == null
                                        ? e.toString() : e.getMessage());
                    }
                }
            }
            throw new IllegalArgumentException(
                    "Cannot convert object to primitive value");
        }
        return toStringValue(v);
    }

    /**
     * Reads a member of an object (own data and the prototype chain for
     * script objects, reflection for Java objects); null when absent.
     */
    public static Object getMember(Machine vm, Object obj, String key) throws NgScriptRuntimeException {
        if (obj instanceof NgsObject) {
            return ((NgsObject) obj).lookup(key, vm, true).read();
        }
        if (obj == null || obj == Undefined.value) {
            // ES: reading a property of null/undefined is a TypeError
            throw new IllegalArgumentException("cannot read property '" + key + "' of null/undefined");
        }
        Object v = NgsObject._lookupNative(obj, obj.getClass(), key, vm);
        if (v instanceof MemRef) {
            return ((MemRef) v).read();
        }
        return v;
    }

    /**
     * Obtains an iterator for a value: Java iterators pass through, Java
     * iterables use iterator(), strings iterate by code point, and other
     * objects go through the ES @@iterator protocol.
     */
    public static Object getIterator(Machine vm, Object iterable) throws Exception {
        if (iterable instanceof NgsArray) {
            // ES: GetMethod(iterable, @@iterator). The array itself is a
            // java.util.Iterator, but deleting Array.prototype[@@iterator]
            // must make it non-iterable, so go through the protocol
            Object method = getMember(vm, iterable, "@@iterator");
            if (method == null || method == Undefined.value) {
                throw new IllegalArgumentException("value is not iterable");
            }
            return callValue(vm, method, iterable, new Object[0]);
        }
        if (iterable instanceof java.util.Iterator) {
            return iterable;
        }
        if (iterable instanceof Generator) {
            // generators follow the generic ES iterator protocol (next() ->
            // {value, done}); the for-of ops already poll it that way
            return iterable;
        }
        if (iterable instanceof java.lang.Iterable) {
            return ((java.lang.Iterable) iterable).iterator();
        }
        if (iterable instanceof String) {
            return new OpObject.CodePointIterator((String) iterable);
        }
        Object method = getMember(vm, iterable, "@@iterator");
        if (method == null || method == Undefined.value) {
            throw new IllegalArgumentException("value is not iterable");
        }
        return callValue(vm, method, iterable, new Object[0]);
    }

    /**
     * Adapts any ES iterator (a Java iterator or a {@code {next()}} protocol
     * object) to a java.util.Iterator for use from builtins.
     */
    public static java.util.Iterator<Object> toJavaIterator(final Machine vm, final Object itObj) {
        if (itObj instanceof java.util.Iterator) {
            @SuppressWarnings("unchecked")
            java.util.Iterator<Object> it = (java.util.Iterator<Object>) itObj;
            return it;
        }
        // ES iterator protocol: poll next() and read the result object's
        // done/value properties (advance happens once per element, on the
        // first hasNext/next call after the previous element was consumed)
        return new java.util.Iterator<Object>() {
            private Object next;
            private boolean done;
            private boolean advanced;

            private void advance() {
                if (done) {
                    return;
                }
                try {
                    Object method = getMember(vm, itObj, "next");
                    Object result = callValue(vm, method, itObj, new Object[0]);
                    if (result instanceof NgsObject
                            && !testValue(((NgsObject) result).get("done"))) {
                        next = ((NgsObject) result).get("value");
                    } else {
                        done = true;
                    }
                } catch (Exception ignored) {
                    done = true;
                }
            }

            @Override
            public boolean hasNext() {
                if (!advanced) {
                    advance();
                    advanced = true;
                }
                return !done;
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                advanced = false;
                return next;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Calls a value with a receiver and arguments, synchronously for script
     * functions (so iterator polling works from opcodes).
     */
    public static Object callValue(Machine vm, Object callee, Object receiver, Object[] args) throws Exception {
        if (callee instanceof Invokable && !(callee instanceof ScriptFunction)) {
            // builtins read their receiver via the this binding
            Object savedThis = vm.thisRef.read();
            vm.thisRef.write(receiver != null ? receiver : Undefined.value);
            try {
                ((Invokable) callee).invoke(vm, args);
            } finally {
                vm.thisRef.write(savedThis);
            }
            return vm.eax.read();
        }
        if (callee instanceof ScriptFunction) {
            return vm.callFunctionSync(callee, args, receiver);
        }
        throw new NgScriptRuntimeException(vm, (callee == null ? "null" : callee.toString()) + " is not callable");
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
    public static boolean isNumber(Object obj) {
        return obj instanceof Integer || obj instanceof Double || obj instanceof Long;
    }

    /** Whether a value is a JS BigInt. */
    public static boolean isBigInt(Object obj) {
        return obj instanceof org.ngscript.value.BigIntValue;
    }

    /**
     * ES ToBigInt for the arithmetic paths: a BigInt stays, an integer-like
     * Number converts exactly, a non-integral Number throws (ES requires a
     * TypeError when mixing BigInt with a Number).
     */
    public static java.math.BigInteger toBigInt(Object obj) {
        if (obj instanceof org.ngscript.value.BigIntValue) {
            return ((org.ngscript.value.BigIntValue) obj).value;
        }
        if (obj instanceof Integer) {
            return java.math.BigInteger.valueOf((Integer) obj);
        }
        if (obj instanceof Long) {
            return java.math.BigInteger.valueOf((Long) obj);
        }
        if (obj instanceof Double) {
            double d = (Double) obj;
            if (d != Math.floor(d) || Double.isInfinite(d) || Double.isNaN(d)) {
                throw new RuntimeException("Cannot convert a Number to BigInt: " + d);
            }
            return java.math.BigDecimal.valueOf(d).toBigInteger();
        }
        throw new RuntimeException("Cannot convert " + (obj == null ? "null" : obj.getClass().getSimpleName()) + " to BigInt");
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
        if (obj instanceof org.ngscript.value.BigIntValue) {
            return ((org.ngscript.value.BigIntValue) obj).value.toString();
        }
        if (obj instanceof NgsArray) {
            // ES: String(array) joins the elements (null/undefined become "")
            NgsArray arr = (NgsArray) obj;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Object v = arr.get("" + i);
                if (v != null && v != Undefined.value) {
                    sb.append(toStringValue(v));
                }
            }
            return sb.toString();
        }
        if (obj instanceof NgsObject) {
            return "[object Object]";
        }
        if (obj instanceof Character) {
            return obj.toString();
        }
        return obj.toString();
    }

    /** ES Number::toString (shortest decimal, no trailing .0 for integers). */
    public static String numberToString(double d) {
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
    public static Object normalize(double d) {
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
        if (isBigInt(a) && isBigInt(b)) {
            return toBigInt(a).equals(toBigInt(b));
        }
        if (isNumber(a) && isBigInt(b)) {
            return bigIntNumberEquals(toBigInt(b), toNumber(a));
        }
        if (isBigInt(a) && isNumber(b)) {
            return bigIntNumberEquals(toBigInt(a), toNumber(b));
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

    /** BigInt == Number: numerically equal only when the Number is integral. */
    private static boolean bigIntNumberEquals(java.math.BigInteger big, double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return false;
        }
        return new java.math.BigDecimal(d).toBigInteger().equals(big)
                && d == new java.math.BigDecimal(big).doubleValue();
    }

    /** ES {@code ===} (strict equality). Integer and Double are the same JS type. */
    public static boolean strictEquals(Object a, Object b) {
        if (a == b) {
            // same object, but ES still says NaN !== NaN
            if (a instanceof Double && ((Double) a).isNaN()) {
                return false;
            }
            return true;
        }
        if (isNumber(a) && isNumber(b)) {
            return numericEquals(toNumber(a), toNumber(b));
        }
        if (isBigInt(a) && isBigInt(b)) {
            return toBigInt(a).equals(toBigInt(b));
        }
        if (isNumber(a) && isBigInt(b)) {
            // BigInt === Number compares numerically when the Number is
            // integral (e.g. 1n === 1 is true, 1.5n is impossible)
            double d = toNumber(a);
            if (d != Math.floor(d) || Double.isInfinite(d) || Double.isNaN(d)) {
                return false;
            }
            return BigDecimalEquals(d, toBigInt(b));
        }
        if (isBigInt(a) && isNumber(b)) {
            return strictEquals(b, a);
        }
        if (a instanceof String && b instanceof String) {
            return a.equals(b);
        }
        if (a instanceof Boolean && b instanceof Boolean) {
            return a.equals(b);
        }
        return false;
    }

    /** Whether a double equals a BigInteger exactly. */
    private static boolean BigDecimalEquals(double d, java.math.BigInteger big) {
        return new java.math.BigDecimal(d).toBigInteger().equals(big)
                && d == new java.math.BigDecimal(big).doubleValue();
    }

    /**
     * ES SameValueZero: like strict equality except NaN equals itself.
     * Used by Array.prototype.includes and Map/Set membership.
     */
    public static boolean sameValueZero(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (isNumber(a) && isNumber(b)) {
            double da = toNumber(a);
            double db = toNumber(b);
            return da == db || (Double.isNaN(da) && Double.isNaN(db));
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
        } else if (testObj instanceof org.ngscript.value.BigIntValue) {
            val = ((org.ngscript.value.BigIntValue) testObj).value.signum() != 0;
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
        Object result;
        if (val instanceof org.ngscript.value.BigIntValue) {
            java.math.BigInteger big = ((org.ngscript.value.BigIntValue) val).value;
            result = new org.ngscript.value.BigIntValue(big.add(java.math.BigInteger.valueOf(num)));
        } else {
            result = normalize(toNumber(val) + num);
        }
        addr.write(result);
        if (rewrite) {
            // postfix: the expression value is the old value (as a number)
            vm.eax.write(val instanceof org.ngscript.value.BigIntValue
                    ? val : normalize(toNumber(val)));
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
