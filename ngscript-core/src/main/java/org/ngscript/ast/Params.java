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

package org.ngscript.ast;

import java.util.List;

/**
 * Formal parameter list of a function or lambda. {@link #defaults} is
 * parallel to {@link #names} (null = no default); {@link #rest} marks the
 * last parameter as a rest parameter ({@code function f(a, ...rest)}).
 *
 * @author wssccc
 */
public class Params {

    public final List<String> names;
    public final List<Expr> defaults;
    public final boolean rest;

    public Params(List<String> names) {
        this(names, null, false);
    }

    public Params(List<String> names, List<Expr> defaults, boolean rest) {
        this.names = names;
        this.defaults = defaults;
        this.rest = rest;
    }
}
