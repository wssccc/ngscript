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
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;
import org.ngscript.value.MemRef;

/**
 * The ES {@code Number} constructor: {@code Number(x)} / {@code new
 * Number(x)} coerce to a number primitive (the engine stores numbers as
 * Integer/Double values, so there is no boxed object). The static
 * constants and parseInt/parseFloat/isNaN/isFinite are dispatched from
 * {@link org.ngscript.runtime.OpObject#member_ref}.
 *
 * @author wssccc
 */
public class NumberCtor implements Invokable {

    @Override
    public void invoke(Machine vm, Object[] args) {
        Object v = args.length > 0 ? args[0] : null;
        if (v == null || v == org.ngscript.value.Undefined.value) {
            vm.eax.write(0);
            return;
        }
        double d = OpUtils.toNumber(v);
        vm.eax.write(OpUtils.normalize(d));
    }

    public static MemRef lookup(NumberCtor ctor, String member, Machine vm) {
        switch (member) {
            case "parseInt":
                return new MemRef((Invokable) (m, args) -> {
                    Object r = GlobalBuiltins.parseIntValue(
                            args.length > 0 ? args[0] : org.ngscript.value.Undefined.value,
                            args.length > 1 ? args[1] : null);
                    m.eax.write(r);
                });
            case "parseFloat":
                return new MemRef((Invokable) (m, args) ->
                        m.eax.write(GlobalBuiltins.parseFloatValue(
                                args.length > 0 ? args[0] : null)));
            case "isNaN":
                return new MemRef((Invokable) (m, args) -> m.eax.write(
                        Double.isNaN(OpUtils.toNumber(args.length > 0 ? args[0] : null))));
            case "isFinite":
                return new MemRef((Invokable) (m, args) -> {
                    double d = OpUtils.toNumber(args.length > 0 ? args[0] : null);
                    m.eax.write(!Double.isNaN(d) && !Double.isInfinite(d));
                });
            case "MAX_VALUE":
                return new MemRef(Double.MAX_VALUE);
            case "MIN_VALUE":
                return new MemRef(Double.MIN_VALUE);
            case "NaN":
                return new MemRef(Double.NaN);
            case "POSITIVE_INFINITY":
                return new MemRef(Double.POSITIVE_INFINITY);
            case "NEGATIVE_INFINITY":
                return new MemRef(Double.NEGATIVE_INFINITY);
            case "EPSILON":
                return new MemRef(2.220446049250313e-16);
            case "MAX_SAFE_INTEGER":
                return new MemRef(9007199254740991L);
            case "MIN_SAFE_INTEGER":
                return new MemRef(-9007199254740991L);
            default:
                return null;
        }
    }
}
