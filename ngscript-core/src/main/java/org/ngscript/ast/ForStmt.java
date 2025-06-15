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
 * A for loop: {@code for (init; test; step) { body }}.
 * The {@code init}, {@code test} and {@code step} expressions may be null
 * when the corresponding clause is empty.
 *
 * @author wssccc
 */
public class ForStmt extends Stmt {

    public final Expr init;
    public final Expr test;
    public final Expr step;
    public final Block body;

    public ForStmt(Expr init, Expr test, Expr step, Block body, int line) {
        this.init = init;
        this.test = test;
        this.step = step;
        this.body = body;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, init, test, step, body);
    }
}
