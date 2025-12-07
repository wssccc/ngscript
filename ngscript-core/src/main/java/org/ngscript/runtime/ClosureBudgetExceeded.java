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

/**
 * Thrown when a closure invoked from a different instruction array (an eval
 * whose execution has ended) exceeds the instruction budget. Propagates
 * through {@link OpCall#call} and the enclosing run loop without being
 * retried, so a runaway eval closure fails the case fast instead of burning
 * the whole budget repeatedly.
 */
public class ClosureBudgetExceeded extends NgScriptRuntimeException {

    public ClosureBudgetExceeded(Machine vm, String message) {
        super(vm, message);
    }
}
