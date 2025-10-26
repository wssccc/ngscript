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

package org.ngscript.value;

import java.math.BigInteger;

/**
 * An ES BigInt value: an arbitrary-precision integer. BigInts are primitives
 * in the engine's value model (a plain wrapper class like the numeric
 * representations); the {@code typeof} of a BigInt is {@code "bigint"}.
 *
 * @author wssccc
 */
public class BigIntValue {

    /** The wrapped arbitrary-precision integer. */
    public final BigInteger value;

    public BigIntValue(BigInteger value) {
        this.value = value;
    }

    public static BigIntValue of(long v) {
        return new BigIntValue(BigInteger.valueOf(v));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BigIntValue && value.equals(((BigIntValue) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
