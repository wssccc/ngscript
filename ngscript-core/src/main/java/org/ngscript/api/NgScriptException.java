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

package org.ngscript.api;

/**
 * Base class of all ngscript exceptions: parse, compile and runtime errors.
 *
 * @author wssccc
 */
public class NgScriptException extends RuntimeException {

    private final Source source;

    public NgScriptException(String message) {
        this(message, (Source) null);
    }

    public NgScriptException(Throwable cause) {
        super(cause);
        this.source = null;
    }

    public NgScriptException(String message, Throwable cause) {
        super(message, cause);
        this.source = null;
    }

    public NgScriptException(String message, Source source) {
        super(message);
        this.source = source;
    }

    public Source getSource() {
        return source;
    }

    public int getLine() {
        return source == null ? -1 : source.getLine();
    }

    @Override
    public String getMessage() {
        if (source == null || source.getLine() <= 0) {
            return super.getMessage();
        }
        return super.getMessage() + " at " + source;
    }

    /**
     * The message without the appended source position, for subclasses that
     * embed position information in their own message text.
     */
    protected String getRawMessage() {
        return super.getMessage();
    }
}
