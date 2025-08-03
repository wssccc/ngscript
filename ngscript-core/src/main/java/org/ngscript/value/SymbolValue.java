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

package org.ngscript.value;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An ES Symbol value. Well-known symbols carry a canonical {@code @@key}
 * used as the object property key; user-created symbols get a unique
 * internal key that never matches real properties.
 *
 * @author wssccc
 */
public class SymbolValue {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    /**
     * The canonical property key for well-known symbols (e.g. "@@iterator"),
     * or null for user symbols.
     */
    private final String key;
    private final String description;
    private final String id;

    public SymbolValue(String key, String description) {
        this.key = key;
        this.description = description;
        this.id = key != null ? key : "@@user#" + COUNTER.incrementAndGet();
    }

    /** The canonical property key of a well-known symbol. */
    public String getKey() {
        return key;
    }

    /** The internal unique key used for property lookup. */
    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Symbol(" + (description == null ? "" : description) + ")";
    }
}
