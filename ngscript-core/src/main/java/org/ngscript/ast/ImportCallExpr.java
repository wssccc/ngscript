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

import java.util.Collections;
import java.util.List;

/**
 * A dynamic import call: {@code import( AssignmentExpression )}. The host
 * has no module loader yet, so the compiled form evaluates the specifier
 * and yields a rejected Promise (ES requires a promise result).
 *
 * @author wssccc
 */
public class ImportCallExpr extends Expr {

    public final List<Expr> args;

    public ImportCallExpr(List<Expr> args, int line) {
        this.line = line;
        this.args = args == null ? Collections.emptyList() : args;
    }

    @Override
    public String toString() {
        return "import(" + args + ")";
    }
}
