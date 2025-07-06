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
 * Marker interface for lexical scopes. Both {@link NgsObject} (objects,
 * including the active frame of a function or constructor call) and
 * {@link NgsArray} implement it. The invariant that the active frame of a
 * constructor IS the created instance (see {@code new_op}) is preserved
 * because constructor frames are NgsObjects themselves.
 *
 * @author wssccc
 */
public interface Scope {
}
