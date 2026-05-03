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

package org.ngscript.builtins;

import org.ngscript.runtime.Machine;
import org.ngscript.runtime.NgsObject;
import org.ngscript.runtime.OpUtils;
import org.ngscript.value.Invokable;
import org.ngscript.value.Undefined;

/**
 * Lightweight ES builtin constructors that only need to be constructible and
 * usable as {@code instanceof} targets (subclass-builtins tests, minimal
 * Date/RegExp/SharedArrayBuffer/WeakRef support). They intentionally do not
 * implement the full builtin surface.
 *
 * @author wssccc
 */
public final class NativeCtors {

    private NativeCtors() {
    }

    /**
     * An ES error constructor (AggregateError/EvalError/URIError). Instances
     * are plain objects carrying {@code name}/{@code message}; {@code e
     * instanceof Ctor} also matches the Java exceptions that map to this
     * constructor (e.g. URISyntaxException for URIError), mirroring the
     * class-based error mapping used elsewhere.
     */
    public static class ErrorCtor implements Invokable {
        public final String esName;
        private final Class<?>[] javaTypes;

        public ErrorCtor(String esName, Class<?>... javaTypes) {
            this.esName = esName;
            this.javaTypes = javaTypes;
        }

        @Override
        public void invoke(Machine vm, Object[] args) {
            NgsObject e = new NgsObject(null);
            e.setPrototype(Builtins.getObjectPrototype());
            e.set("name", esName);
            e.set("message", args.length > 0 && args[0] != null
                    && args[0] != Undefined.value ? OpUtils.toStringValue(args[0]) : "");
            vm.eax.write(e);
        }

        /** Whether a thrown Java exception maps to this ES constructor. */
        public boolean matches(Throwable t) {
            for (Class<?> c : javaTypes) {
                if (c.isInstance(t)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A minimal Date constructor (timestamp in a plain object). */
    public static class DateCtor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            NgsObject d = new NgsObject(null);
            d.setPrototype(Builtins.getObjectPrototype());
            long t = System.currentTimeMillis();
            if (args.length > 0 && args[0] != null && args[0] != Undefined.value) {
                t = (long) OpUtils.toNumber(args[0]);
            }
            d.set("_time", t);
            d.set("getTime", (Invokable) (m, a) ->
                    m.eax.write(((Number) ((NgsObject) m.thisRef.read()).get("_time")).longValue()));
            vm.eax.write(d);
        }
    }

    /** A minimal RegExp constructor. */
    public static class RegExpCtor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            String pattern = args.length > 0 && args[0] != null
                    && args[0] != Undefined.value ? OpUtils.toStringValue(args[0]) : "";
            String flags = args.length > 1 && args[1] != null
                    && args[1] != Undefined.value ? OpUtils.toStringValue(args[1]) : "";
            vm.eax.write(new org.ngscript.value.RegExpValue(pattern, flags));
        }
    }

    /** A minimal SharedArrayBuffer constructor (a plain host object). */
    public static class SharedArrayBufferCtor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            NgsObject o = new NgsObject(null);
            o.setPrototype(Builtins.getObjectPrototype());
            int len = args.length > 0 && args[0] != null
                    ? (int) OpUtils.toNumber(args[0]) : 0;
            o.set("byteLength", Math.max(0, len));
            vm.eax.write(o);
        }
    }

    /** A minimal WeakRef constructor (a plain host object). */
    public static class WeakRefCtor implements Invokable {
        @Override
        public void invoke(Machine vm, Object[] args) {
            NgsObject o = new NgsObject(null);
            o.setPrototype(Builtins.getObjectPrototype());
            if (args.length > 0) {
                o.set("deref", (Invokable) (m, a) -> m.eax.write(args[0]));
            }
            vm.eax.write(o);
        }
    }
}
