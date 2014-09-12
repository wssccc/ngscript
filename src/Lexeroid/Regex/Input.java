/*
 *  wssccc all rights reserved
 */
package Lexeroid.Regex;

import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class Input {

    public static final int TYPE_EPSILON = 0;
    public static final int TYPE_ANY_ = 1;
    public static final int TYPE_CHARS = 2;
    public static final int TYPE_NOT_CHARS = 3;
    public static final int TYPE_UNDEFINED = 4;
    public static final int TYPE_CHAR = 5;

    static final HashMap<Integer, Input> registeredInput = new HashMap<Integer, Input>();
    public static final Input epsilon = new Input(TYPE_EPSILON, 0, null);
    public static final Input any = new Input(TYPE_ANY_, 0, null);
    public static final Input undefined = new Input(TYPE_UNDEFINED, 0, null);

    private static Input[] _splitAnyCache = null;

    public static Input chr(int c) {
        if (registeredInput.containsKey(c) == false) {
            Input in = new Input(TYPE_CHAR, c, null);
            registeredInput.put(c, in);
            return in;
        } else {
            return registeredInput.get(c);
        }
    }

    public static Input chars(ArrayList<Integer> chars, boolean not) {
        //register all
        for (Integer c : chars) {
            if (registeredInput.containsKey(c) == false) {
                Input in = new Input(TYPE_CHAR, c, null);
                registeredInput.put(c, in);
            }
        }
        if (not) {
            return new Input(TYPE_NOT_CHARS, 0, chars);
        } else {
            return new Input(TYPE_CHARS, 0, chars);
        }
    }

    private static Input[] _splitAny() {
        if (_splitAnyCache == null) {
            _splitAnyCache = new Input[registeredInput.size() + 1];
            int count = 0;
            for (Input input : registeredInput.values()) {
                _splitAnyCache[count++] = input;
            }
            _splitAnyCache[count++] = undefined;
            assert count == _splitAnyCache.length;
        }
        return _splitAnyCache;
    }

    final int type;
    final int chr;
    final ArrayList<Integer> chars;

    private Input(int type, int chr, ArrayList<Integer> chars) {
        this.type = type;
        this.chr = chr;
        this.chars = chars;
    }

    Input[] _splitChars() {
        if (type == TYPE_CHARS) {
            Input[] inputs = new Input[chars.size()];
            for (int i = 0; i < inputs.length; i++) {
                inputs[i] = registeredInput.get(chars.get(i));
                assert inputs[i] != null;
            }
            return inputs;
        }
        if (type == TYPE_NOT_CHARS) {
            Input[] inputs = new Input[registeredInput.size() + 1 - chars.size()];
            int count = 0;
            for (int i : registeredInput.keySet()) {
                if (!chars.contains(i)) {
                    inputs[count] = registeredInput.get(i);
                    ++count;
                }
            }
            inputs[count++] = Input.undefined;
            assert count == inputs.length;
            return inputs;
        }
        //unknown type, internal error
        assert false;
        return null;
    }

    public Input[] split() {
        switch (type) {
            case TYPE_ANY_:
                return _splitAny();
            case TYPE_CHARS:
            case TYPE_NOT_CHARS:
                return _splitChars();
        }
        return null;
    }

    public boolean accept(int c) {
        //if NFA is not normalized yet, this assert failed
        assert type != TYPE_ANY_ && type != TYPE_CHARS && type != TYPE_NOT_CHARS;
        switch (type) {
            case TYPE_UNDEFINED:
                return registeredInput.containsKey(c) == false;
            case TYPE_CHAR:
                return chr == c;
            case TYPE_EPSILON:
                return true;
        }
        //unknown type, internal error
        assert false;
        return false;
    }

    @Override
    public String toString() {
        switch (type) {
            case TYPE_EPSILON:
                return "epsilon";
            case TYPE_CHAR:
                return "'" + (char) chr + "'";
            case TYPE_ANY_:
                return "any";
            case TYPE_CHARS:
                return "chars[" + chars.toString() + "]";
            case TYPE_NOT_CHARS:
                return "ex_chars[" + chars.toString() + "]";
            case TYPE_UNDEFINED:
                return "undefined";
        }
        //unknown type, internal error
        assert false;
        return null;
    }

}
