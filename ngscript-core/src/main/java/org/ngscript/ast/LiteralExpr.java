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
 * A literal: integer, double, string, true, false, null or undefined.
 *
 * @author wssccc
 */
public class LiteralExpr extends Expr {

    public enum Kind { INTEGER, DOUBLE, STRING, TRUE, FALSE, NULL, UNDEFINED, BIGINT }

    public final Kind kind;
    public final String value;

    /** Whether a string literal contained an escape (directive matching). */
    public boolean hasEscape;

    public LiteralExpr(Kind kind, String value, int line) {
        this.kind = kind;
        this.value = value;
        this.line = line;
    }

    @Override
    protected String toString(int indent) {
        return super.toString(indent) + " kind=" + kind + " value=" + value;
    }
}
