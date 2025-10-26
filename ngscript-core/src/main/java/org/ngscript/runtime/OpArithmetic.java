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

import org.ngscript.value.Undefined;

/**
 * Arithmetic opcodes: unary/binary numeric operators, comparisons and
 * increment/decrement.
 *
 * @author wssccc
 */
public class OpArithmetic {

    /**
     * ES arithmetic with BigInt support: when both operands are BigInts the
     * result is a BigInt; a BigInt mixed with a Number is a TypeError.
     *
     * @return true when the operation handled the operands (result in eax)
     */
    private static boolean bigIntBinary(Machine vm, Object[] objs, int op) throws NgScriptRuntimeException {
        boolean aBig = OpUtils.isBigInt(objs[0]);
        boolean bBig = OpUtils.isBigInt(objs[1]);
        if (!aBig && !bBig) {
            return false;
        }
        if (aBig != bBig) {
            throw new NgScriptRuntimeException(vm, "Cannot mix BigInt and other types, use explicit conversions");
        }
        java.math.BigInteger a = OpUtils.toBigInt(objs[0]);
        java.math.BigInteger b = OpUtils.toBigInt(objs[1]);
        java.math.BigInteger r;
        switch (op) {
            case TypeOp.OP_ADD:
                r = a.add(b);
                break;
            case TypeOp.OP_SUB:
                r = a.subtract(b);
                break;
            case TypeOp.OP_MUL:
                r = a.multiply(b);
                break;
            case TypeOp.OP_DIV:
                if (b.signum() == 0) {
                    throw new NgScriptRuntimeException(vm, "Division by zero (BigInt)");
                }
                r = a.divide(b);
                break;
            case TypeOp.OP_MOD:
                if (b.signum() == 0) {
                    throw new NgScriptRuntimeException(vm, "Division by zero (BigInt)");
                }
                r = a.remainder(b);
                break;
            case '^':
                if (b.signum() < 0) {
                    throw new NgScriptRuntimeException(vm, "Exponent must be positive (BigInt)");
                }
                if (b.bitLength() > 31) {
                    throw new NgScriptRuntimeException(vm, "Exponent too large (BigInt)");
                }
                r = a.pow(b.intValue());
                break;
            case 1: // bit_and
                r = a.and(b);
                break;
            case 2: // bit_or
                r = a.or(b);
                break;
            case 3: // bit_xor
                r = a.xor(b);
                break;
            case 4: // shl
                r = a.shiftLeft(b.intValue());
                break;
            case 5: // shr
                r = a.shiftRight(b.intValue());
                break;
            default:
                throw new NgScriptRuntimeException(vm, "unsupported BigInt operation");
        }
        vm.eax.write(new org.ngscript.value.BigIntValue(r));
        return true;
    }

    public static void neg(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object v = vm.eax.read();
        if (OpUtils.isBigInt(v)) {
            vm.eax.write(new org.ngscript.value.BigIntValue(OpUtils.toBigInt(v).negate()));
            return;
        }
        vm.eax.write(OpUtils.normalize(-OpUtils.toNumber(v)));
    }

    public static void not(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(!OpUtils.testValue(vm.eax.read()));
    }

    public static void inc(Machine vm, String param, String param_extend) {
        OpUtils.addEax(vm, 1, false);
    }

    public static void dec(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        OpUtils.addEax(vm, -1, false);
    }

    public static void post_inc(Machine vm, String param, String param_extend) {
        OpUtils.addEax(vm, 1, true);
    }

    public static void post_dec(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        OpUtils.addEax(vm, -1, true);
    }

    /** ES relational comparison supporting BigInt operands. */
    private static boolean bigIntCompare(Machine vm, Object a, Object b, String op) throws NgScriptRuntimeException {
        boolean aBig = OpUtils.isBigInt(a);
        boolean bBig = OpUtils.isBigInt(b);
        if (aBig && bBig) {
            int c = OpUtils.toBigInt(a).compareTo(OpUtils.toBigInt(b));
            switch (op) {
                case "lt":
                    return c < 0;
                case "gt":
                    return c > 0;
                case "le":
                    return c <= 0;
                default:
                    return c >= 0;
            }
        }
        if (aBig != bBig) {
            // BigInt vs Number: compare numerically (the Number side may be
            // non-integral, which simply orders between the integers)
            java.math.BigInteger big = OpUtils.toBigInt(aBig ? a : b);
            double d = OpUtils.toNumber(aBig ? b : a);
            if (Double.isNaN(d)) {
                return false;
            }
            int c = new java.math.BigDecimal(d).toBigInteger().compareTo(big);
            boolean aFirst = aBig; // a is the BigInt, b the Number
            switch (op) {
                case "lt":
                    return aFirst ? c < 0 : c > 0;
                case "gt":
                    return aFirst ? c > 0 : c < 0;
                case "le":
                    return aFirst ? c <= 0 : c >= 0;
                default:
                    return aFirst ? c >= 0 : c <= 0;
            }
        }
        return false;
    }

    public static void lt(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        boolean r;
        if (OpUtils.isBigInt(objs[0]) || OpUtils.isBigInt(objs[1])) {
            r = bigIntCompare(vm, objs[0], objs[1], "lt");
        } else if (objs[0] instanceof String && objs[1] instanceof String) {
            r = ((String) objs[0]).compareTo((String) objs[1]) < 0;
        } else {
            r = OpUtils.toNumber(objs[0]) < OpUtils.toNumber(objs[1]); // NaN compares false
        }
        vm.eax.write(r);
    }

    public static void gt(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        boolean r;
        if (OpUtils.isBigInt(objs[0]) || OpUtils.isBigInt(objs[1])) {
            r = bigIntCompare(vm, objs[0], objs[1], "gt");
        } else if (objs[0] instanceof String && objs[1] instanceof String) {
            r = ((String) objs[0]).compareTo((String) objs[1]) > 0;
        } else {
            r = OpUtils.toNumber(objs[0]) > OpUtils.toNumber(objs[1]);
        }
        vm.eax.write(r);
    }

    public static void ge(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        boolean r;
        if (OpUtils.isBigInt(objs[0]) || OpUtils.isBigInt(objs[1])) {
            r = bigIntCompare(vm, objs[0], objs[1], "ge");
        } else if (objs[0] instanceof String && objs[1] instanceof String) {
            r = ((String) objs[0]).compareTo((String) objs[1]) >= 0;
        } else {
            r = OpUtils.toNumber(objs[0]) >= OpUtils.toNumber(objs[1]);
        }
        vm.eax.write(r);
    }

    public static void le(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        boolean r;
        if (OpUtils.isBigInt(objs[0]) || OpUtils.isBigInt(objs[1])) {
            r = bigIntCompare(vm, objs[0], objs[1], "le");
        } else if (objs[0] instanceof String && objs[1] instanceof String) {
            r = ((String) objs[0]).compareTo((String) objs[1]) <= 0;
        } else {
            r = OpUtils.toNumber(objs[0]) <= OpUtils.toNumber(objs[1]);
        }
        vm.eax.write(r);
    }

    public static void add(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, TypeOp.OP_ADD)) {
            return;
        }
        vm.eax.write(TypeOp.eval(TypeOp.OP_ADD, objs[0], objs[1]));
    }

    public static void bit_and(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, 1)) {
            return;
        }
        vm.eax.write(OpUtils.toInt32(objs[0]) & OpUtils.toInt32(objs[1]));
    }

    public static void bit_or(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, 2)) {
            return;
        }
        vm.eax.write(OpUtils.toInt32(objs[0]) | OpUtils.toInt32(objs[1]));
    }

    public static void bit_xor(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, 3)) {
            return;
        }
        vm.eax.write(OpUtils.toInt32(objs[0]) ^ OpUtils.toInt32(objs[1]));
    }

    public static void shl(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, 4)) {
            return;
        }
        vm.eax.write(OpUtils.toInt32(objs[0]) << (OpUtils.toInt32(objs[1]) & 31));
    }

    public static void shr(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, 5)) {
            return;
        }
        vm.eax.write(OpUtils.toInt32(objs[0]) >> (OpUtils.toInt32(objs[1]) & 31));
    }

    public static void ushr(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (OpUtils.isBigInt(objs[0]) || OpUtils.isBigInt(objs[1])) {
            throw new NgScriptRuntimeException(vm, "BigInts have no unsigned right shift");
        }
        vm.eax.write(OpUtils.toInt32(objs[0]) >>> (OpUtils.toInt32(objs[1]) & 31));
    }

    public static void exp(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, '^')) {
            return;
        }
        vm.eax.write(OpUtils.normalize(Math.pow(OpUtils.toNumber(objs[0]), OpUtils.toNumber(objs[1]))));
    }

    public static void unary_plus(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object v = vm.eax.read();
        if (OpUtils.isBigInt(v)) {
            throw new NgScriptRuntimeException(vm, "Cannot convert a BigInt to a number");
        }
        vm.eax.write(OpUtils.normalize(OpUtils.toNumber(v)));
    }

    public static void bit_not(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object v = vm.eax.read();
        if (OpUtils.isBigInt(v)) {
            vm.eax.write(new org.ngscript.value.BigIntValue(OpUtils.toBigInt(v).not()));
            return;
        }
        vm.eax.write(~OpUtils.toInt32(v));
    }

    public static void nullish(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        Object left = objs[0];
        if (left == null || left == Undefined.value) {
            vm.eax.write(objs[1]);
        } else {
            vm.eax.write(left);
        }
    }

    public static void sub(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, TypeOp.OP_SUB)) {
            return;
        }
        vm.eax.write(TypeOp.eval(TypeOp.OP_SUB, objs[0], objs[1]));
    }

    public static void mul(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, TypeOp.OP_MUL)) {
            return;
        }
        vm.eax.write(TypeOp.eval(TypeOp.OP_MUL, objs[0], objs[1]));
    }

    public static void mod(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, TypeOp.OP_MOD)) {
            return;
        }
        vm.eax.write(TypeOp.eval(TypeOp.OP_MOD, objs[0], objs[1]));
    }

    public static void div(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        if (bigIntBinary(vm, objs, TypeOp.OP_DIV)) {
            return;
        }
        vm.eax.write(TypeOp.eval(TypeOp.OP_DIV, objs[0], objs[1]));
    }

    public static void eq(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.abstractEquals(objs[0], objs[1]));
    }

    public static void veq(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.strictEquals(objs[0], objs[1]));
    }

    public static void neq(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(!OpUtils.abstractEquals(objs[0], objs[1]));
    }

    public static void vneq(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(!OpUtils.strictEquals(objs[0], objs[1]));
    }
}
