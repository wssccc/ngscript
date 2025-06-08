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

package org.ngscript.parser.ast;

/**
 * Base class of all AST nodes. Every node records the source line it was parsed from,
 * which is used by the compiler to generate debug information.
 *
 * @author wssccc
 */
public abstract class AstNode {

    public int line;

    @Override
    public String toString() {
        return toString(0);
    }

    protected String toString(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
        sb.append(getClass().getSimpleName()).append(" line:").append(line);
        return sb.toString();
    }

    protected String childrenToString(int indent, Object... children) {
        StringBuilder sb = new StringBuilder(toString(indent));
        for (Object child : children) {
            sb.append("\n");
            if (child instanceof AstNode) {
                sb.append(((AstNode) child).toString(indent + 1));
            } else if (child instanceof Iterable) {
                for (Object o : (Iterable<?>) child) {
                    sb.append("\n");
                    if (o instanceof AstNode) {
                        sb.append(((AstNode) o).toString(indent + 1));
                    } else {
                        sb.append(o);
                    }
                }
            } else {
                sb.append(child);
            }
        }
        return sb.toString();
    }
}
