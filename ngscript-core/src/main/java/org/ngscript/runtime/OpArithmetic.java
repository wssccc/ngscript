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

    public static void neg(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(OpUtils.normalize(-OpUtils.toNumber(vm.eax.read())));
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

    public static void lt(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        boolean r;
        if (objs[0] instanceof String && objs[1] instanceof String) {
            r = ((String) objs[0]).compareTo((String) objs[1]) < 0;
        } else {
            r = OpUtils.toNumber(objs[0]) < OpUtils.toNumber(objs[1]); // NaN compares false
        }
        vm.eax.write(r);
    }

    public static void gt(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        boolean r;
        if (objs[0] instanceof String && objs[1] instanceof String) {
            r = ((String) objs[0]).compareTo((String) objs[1]) > 0;
        } else {
            r = OpUtils.toNumber(objs[0]) > OpUtils.toNumber(objs[1]);
        }
        vm.eax.write(r);
    }

    public static void ge(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        boolean r;
        if (objs[0] instanceof String && objs[1] instanceof String) {
            r = ((String) objs[0]).compareTo((String) objs[1]) >= 0;
        } else {
            r = OpUtils.toNumber(objs[0]) >= OpUtils.toNumber(objs[1]);
        }
        vm.eax.write(r);
    }

    public static void le(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        boolean r;
        if (objs[0] instanceof String && objs[1] instanceof String) {
            r = ((String) objs[0]).compareTo((String) objs[1]) <= 0;
        } else {
            r = OpUtils.toNumber(objs[0]) <= OpUtils.toNumber(objs[1]);
        }
        vm.eax.write(r);
    }

    public static void add(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_ADD, objs[0], objs[1]));
    }

    public static void bit_and(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.toInt32(objs[0]) & OpUtils.toInt32(objs[1]));
    }

    public static void bit_or(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.toInt32(objs[0]) | OpUtils.toInt32(objs[1]));
    }

    public static void bit_xor(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.toInt32(objs[0]) ^ OpUtils.toInt32(objs[1]));
    }

    public static void shl(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.toInt32(objs[0]) << (OpUtils.toInt32(objs[1]) & 31));
    }

    public static void shr(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.toInt32(objs[0]) >> (OpUtils.toInt32(objs[1]) & 31));
    }

    public static void ushr(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.toInt32(objs[0]) >>> (OpUtils.toInt32(objs[1]) & 31));
    }

    public static void exp(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(OpUtils.normalize(Math.pow(OpUtils.toNumber(objs[0]), OpUtils.toNumber(objs[1]))));
    }

    public static void unary_plus(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(OpUtils.normalize(OpUtils.toNumber(vm.eax.read())));
    }

    public static void bit_not(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        vm.eax.write(~OpUtils.toInt32(vm.eax.read()));
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
        vm.eax.write(TypeOp.eval(TypeOp.OP_SUB, objs[0], objs[1]));
    }

    public static void mul(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_MUL, objs[0], objs[1]));
    }

    public static void mod(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
        vm.eax.write(TypeOp.eval(TypeOp.OP_MOD, objs[0], objs[1]));
    }

    public static void div(Machine vm, String param, String param_extend) throws NgScriptRuntimeException {
        Object[] objs = OpUtils.get2OpParam(vm);
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
