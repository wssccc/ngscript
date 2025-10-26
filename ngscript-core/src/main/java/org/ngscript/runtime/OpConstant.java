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
 * Constant opcodes: literal values.
 *
 * @author wssccc
 */
public class OpConstant {

    public static void integer(Machine vm, String param, String param_extend) {
        vm.eax.write(Integer.parseInt(param));
    }

    public static void bigint(Machine vm, String param, String param_extend) {
        vm.eax.write(new org.ngscript.value.BigIntValue(new java.math.BigInteger(param)));
    }

    public static void bool(Machine vm, String param, String param_extend) {
        vm.eax.write(Boolean.parseBoolean(param));
    }

    public static void double_(Machine vm, String param, String param_extend) {
        vm.eax.write(Double.parseDouble(param));
    }

    public static void undefined(Machine vm, String param, String param_extend) {
        vm.eax.write(Undefined.value);
    }

    public static void string(Machine vm, String param, String param_extend) {
        // The lexer already decodes escape sequences and strips quotes;
        // the value is written as-is.
        vm.eax.write(param);
    }

    /**
     * Writes the new-expression receiver sentinel (distinguishes
     * {@code new f()} from method calls on String receivers).
     */
    public static void new_marker(Machine vm, String param, String param_extend) {
        vm.eax.write(OpCall.NEW_RECEIVER);
    }

    /**
     * Concatenates template literal chunks and substitution values: the
     * stack holds interleaved chunk strings and expression values (odd
     * positions are expressions), pushed chunk-first. Expressions use ES
     * ToString coercion.
     */
    public static void template_join(Machine vm, String param, String param_extend) {
        int n = Integer.parseInt(param);
        Object[] items = new Object[n];
        for (int i = n - 1; i >= 0; i--) {
            items[i] = vm.stack.pop();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            Object it = items[i];
            if (i % 2 == 1) {
                // substitution values
                sb.append(OpUtils.toStringValue(it));
            } else if (it != null) {
                // chunk text
                sb.append(it);
            }
        }
        vm.eax.write(sb.toString());
    }
}
