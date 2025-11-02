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

import java.util.ArrayList;
import java.util.List;

/**
 * A destructuring pattern (declaration targets and assignment targets).
 * The optional {@link #defaultValue} applies when the matched value is
 * undefined.
 *
 * @author wssccc
 */
public abstract class Pattern extends AstNode {

    public Expr defaultValue;

    /** A bound name. */
    public static class IdentP extends Pattern {
        public final String name;

        public IdentP(String name, int line) {
            this.name = name;
            this.line = line;
        }

        @Override
        protected String toString(int indent) {
            return name;
        }
    }

    /** An array pattern: {@code [a, , ...rest]}. Null items are holes. */
    public static class ArrayP extends Pattern {
        public final List<Pattern> items;
        public Pattern rest;

        public ArrayP(List<Pattern> items, int line) {
            this.items = items;
            this.line = line;
        }

        @Override
        protected String toString(int indent) {
            return childrenToString(indent, (Object[]) items.toArray(new Pattern[0]));
        }
    }

    /** One object pattern property: {@code key: pattern} or {@code key}. */
    public static class Prop {
        public final String key;
        public final Pattern pattern;

        public Prop(String key, Pattern pattern) {
            this.key = key;
            this.pattern = pattern;
        }
    }

    /** An object pattern: {@code {x, y: a}}. */
    public static class ObjectP extends Pattern {
        public final List<Prop> props;
        /** The rest pattern ({@code {...rest}}), or null. */
        public Pattern rest;

        public ObjectP(List<Prop> props, int line) {
            this.props = props;
            this.line = line;
        }

        @Override
        protected String toString(int indent) {
            List<String> names = new ArrayList<>();
            for (Prop p : props) {
                names.add(p.key);
            }
            return childrenToString(indent, names);
        }
    }
}
