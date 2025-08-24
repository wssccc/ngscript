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

/**
 * A function declaration: {@code function name(params) { body }}.
 * {@link #generator} marks a generator function ({@code function* name}).
 *
 * @author wssccc
 */
public class FunctionDecl extends Stmt {

    public final String name;
    public final Params params;
    public final Block body;
    public final boolean generator;

    public FunctionDecl(String name, Params params, Block body, int line) {
        this(name, params, body, false, line);
    }

    public FunctionDecl(String name, Params params, Block body, boolean generator, int line) {
        this.name = name;
        this.params = params;
        this.body = body;
        this.generator = generator;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, "name=" + name, body);
    }
}
