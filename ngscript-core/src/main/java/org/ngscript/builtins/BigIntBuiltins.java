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
import org.ngscript.runtime.NgScriptRuntimeException;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.BigIntValue;
import org.ngscript.value.Invokable;
import org.ngscript.value.Undefined;

/**
 * The ES {@code BigInt} builtin: the constructor converts a Number or a
 * decimal string to a BigInt; the prototype carries toString/valueOf
 * (asIntegerN/asUintN are registered too).
 *
 * @author wssccc
 */
public class BigIntBuiltins {

    private static NgsObject bigIntPrototype;

    public static synchronized NgsObject getBigIntPrototype() {
        if (bigIntPrototype == null) {
            bigIntPrototype = new NgsObject(null);
            bigIntPrototype.setPrototype(Builtins.getObjectPrototype());
            bigIntPrototype.set("toString", (Invokable) BigIntBuiltins::toString_);
            bigIntPrototype.set("valueOf", (Invokable) BigIntBuiltins::valueOf_);
            bigIntPrototype.set("asIntN", (Invokable) BigIntBuiltins::asIntN);
            bigIntPrototype.set("asUintN", (Invokable) BigIntBuiltins::asUintN);
        }
        return bigIntPrototype;
    }

    /** The BigInt constructor: BigInt(value) converts number/string/boolean. */
    public static class Ctor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            Object v = args.length > 0 ? args[0] : null;
            vm.eax.write(new BigIntValue(toBigIntValue(v)));
        }
    }

    private static java.math.BigInteger toBigIntValue(Object v) {
        if (v instanceof BigIntValue) {
            return ((BigIntValue) v).value;
        }
        if (v instanceof Integer) {
            return java.math.BigInteger.valueOf((Integer) v);
        }
        if (v instanceof Long) {
            return java.math.BigInteger.valueOf((Long) v);
        }
        if (v instanceof Double) {
            double d = (Double) v;
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new RuntimeException("Cannot convert " + d + " to a BigInt");
            }
            if (d != Math.floor(d)) {
                throw new RuntimeException("Cannot convert " + d + " to a BigInt");
            }
            return java.math.BigDecimal.valueOf(d).toBigInteger();
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? java.math.BigInteger.ONE : java.math.BigInteger.ZERO;
        }
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) {
                return java.math.BigInteger.ZERO;
            }
            int radix = 10;
            String digits = s;
            if (s.startsWith("0x") || s.startsWith("0X")) {
                radix = 16;
                digits = s.substring(2);
            } else if (s.startsWith("0o") || s.startsWith("0O")) {
                radix = 8;
                digits = s.substring(2);
            } else if (s.startsWith("0b") || s.startsWith("0B")) {
                radix = 2;
                digits = s.substring(2);
            }
            try {
                return new java.math.BigInteger(digits, radix);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Cannot convert '" + v + "' to a BigInt");
            }
        }
        if (v == null || v == Undefined.value) {
            throw new RuntimeException("Cannot convert undefined to a BigInt");
        }
        throw new RuntimeException("Cannot convert " + v.getClass().getSimpleName() + " to a BigInt");
    }

    private static java.math.BigInteger requireBigInt(Machine vm, String what) {
        Object t = vm.thisRef.read();
        if (!(t instanceof BigIntValue)) {
            throw new NgScriptRuntimeException(vm, what + " called on a non-BigInt");
        }
        return ((BigIntValue) t).value;
    }

    /**
     * Primitive BigInt member lookup: resolves prototype methods as
     * Invokables that operate on the primitive receiver.
     */
    public static org.ngscript.value.MemRef lookupPrimitive(BigIntValue big, String member, Machine vm) {
        if (member.equals("toString")) {
            return new org.ngscript.value.MemRef((org.ngscript.value.Invokable) (m, a) -> {
                int radix = a.length > 0 ? (int) OpUtils.toNumber(a[0]) : 10;
                m.eax.write(big.value.toString(radix));
            });
        }
        if (member.equals("valueOf")) {
            return new org.ngscript.value.MemRef((org.ngscript.value.Invokable) (m, a) ->
                    m.eax.write(big));
        }
        return null;
    }

    /** BigInt.prototype.toString([radix]) */
    private static void toString_(Machine vm, Object[] args) {
        java.math.BigInteger v = requireBigInt(vm, "BigInt.prototype.toString");
        int radix = args.length > 0 ? (int) OpUtils.toNumber(args[0]) : 10;
        vm.eax.write(v.toString(radix));
    }

    /** BigInt.prototype.valueOf() */
    private static void valueOf_(Machine vm, Object[] args) {
        requireBigInt(vm, "BigInt.prototype.valueOf");
        vm.eax.write(vm.thisRef.read());
    }

    /** BigInt.asIntN(bits, bigint): wrap to a signed two's complement. */
    public static void asIntNPublic(Machine vm, Object[] args) {
        asIntN(vm, args);
    }

    /** BigInt.asUintN(bits, bigint): wrap to an unsigned value. */
    public static void asUintNPublic(Machine vm, Object[] args) {
        asUintN(vm, args);
    }

    /** BigInt.asIntN(bits, bigint): wrap to a signed two's complement. */
    private static void asIntN(Machine vm, Object[] args) {
        int bits = args.length > 0 ? (int) OpUtils.toNumber(args[0]) : 0;
        Object v = args.length > 1 ? args[1] : Undefined.value;
        java.math.BigInteger x = toBigIntValue(v);
        if (bits <= 0) {
            vm.eax.write(new BigIntValue(java.math.BigInteger.ZERO));
            return;
        }
        java.math.BigInteger mod = java.math.BigInteger.ONE.shiftLeft(bits);
        java.math.BigInteger r = x.mod(mod);
        if (r.testBit(bits - 1)) {
            r = r.subtract(mod);
        }
        vm.eax.write(new BigIntValue(r));
    }

    /** BigInt.asUintN(bits, bigint): wrap to an unsigned value. */
    private static void asUintN(Machine vm, Object[] args) {
        int bits = args.length > 0 ? (int) OpUtils.toNumber(args[0]) : 0;
        Object v = args.length > 1 ? args[1] : Undefined.value;
        java.math.BigInteger x = toBigIntValue(v);
        if (bits <= 0) {
            vm.eax.write(new BigIntValue(java.math.BigInteger.ZERO));
            return;
        }
        java.math.BigInteger mod = java.math.BigInteger.ONE.shiftLeft(bits);
        vm.eax.write(new BigIntValue(x.mod(mod)));
    }
}
