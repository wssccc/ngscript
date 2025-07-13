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
 * A {@code with (object) statement} (non-strict ECMAScript 5). Parsed for
 * conformance; semantics arrive with the environment-record rework.
 *
 * @author wssccc
 */
public class WithStmt extends Stmt {

    public final Expr object;
    public final Stmt body;

    public WithStmt(Expr object, Stmt body, int line) {
        this.object = object;
        this.body = body;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, object, body);
    }
}
