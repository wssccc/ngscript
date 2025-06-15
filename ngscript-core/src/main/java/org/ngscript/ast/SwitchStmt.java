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
 * A switch statement with an optional default branch.
 *
 * @author wssccc
 */
public class SwitchStmt extends Stmt {

    public final Expr value;
    public final List<SwitchCase> cases;
    public final Block defaultBody;

    public SwitchStmt(Expr value, List<SwitchCase> cases, Block defaultBody, int line) {
        this.value = value;
        this.cases = cases;
        this.defaultBody = defaultBody;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return childrenToString(indent, value, cases, defaultBody);
    }
}
