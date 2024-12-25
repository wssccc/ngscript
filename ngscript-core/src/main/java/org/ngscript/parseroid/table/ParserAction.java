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

package org.ngscript.parseroid.table;

import java.io.Serializable;

/**
 * @author wssccc
 */
public class ParserAction implements Serializable {

    public static final char REDUCE = 'r';
    public static final char ACCEPT = 'a';
    public static final char SHIFT = 's';
    public static final char GOTO = 't';

    public char action;
    public int param;

    private ParserAction(char action, int param) {
        this.action = action;
        this.param = param;
    }

    public static ParserAction reduce(int param) {
        return new ParserAction(REDUCE, param);
    }

    public static ParserAction shift(int param) {
        return new ParserAction(SHIFT, param);
    }

    public static ParserAction accept() {
        return new ParserAction(ACCEPT, 0);
    }

    public static ParserAction go(int param) {
        return new ParserAction(GOTO, param);
    }

    public boolean eq(ParserAction other) {
        return other.action == this.action && other.param == this.param;
    }

    @Override
    public String toString() {
        return "" + action + "" + param;
    }

}
