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
 * A {@code for (var x in obj) ...} or {@code for (x in obj) ...} statement.
 * The target is either a {@link VarDeclExpr} or an {@link IdentExpr}.
 *
 * @author wssccc
 */
public class ForInStmt extends Stmt {

    public final Expr target;
    public final Expr object;
    public final Stmt body;

    public ForInStmt(Expr target, Expr object, Stmt body, int line) {
        this.target = target;
        this.object = object;
        this.body = body;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, target, object, body);
    }
}
