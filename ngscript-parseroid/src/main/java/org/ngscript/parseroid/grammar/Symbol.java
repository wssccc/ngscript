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

package org.ngscript.parseroid.grammar;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author wssccc
 */
@ToString
@EqualsAndHashCode
public class Symbol implements Serializable{

    public static final Symbol NULL = new Symbol("NULL", true);
    public static final Symbol EOF = new Symbol("EOF", true);
    public static final Symbol ERROR = new Symbol("ERROR", true);

    public String identifier;
    public boolean isTerminal;

    private Symbol(String identifier, boolean isTerminal) {
        this.identifier = identifier;
        this.isTerminal = isTerminal;
    }

    public static Symbol create(String identifier, boolean isTerminal) {
        return new Symbol(identifier, isTerminal);
    }
}
