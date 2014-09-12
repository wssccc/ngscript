/*
 *  wssccc all rights reserved
 */
package Lexeroid.Regex;

import java.util.ArrayList;
import java.util.Stack;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class RegexParser {

    char[] ch;
    int pos;
    int paren = 0;
    int sqrt = 0;

    public RegexParser(String regex) {
        ch = regex.toCharArray();
        pos = 0;
    }

    public NFA parse() {
        NFA nfa = _parse();
        if (paren != 0) {
            throw new RuntimeException("Regex Parser: unbalanced parentheses");
        }
        if (sqrt != 0) {
            throw new RuntimeException("Regex Parser: unbalanced square brackets");
        }
        return nfa;
    }

    public NFA _parse() {
        Stack<NFA> parsed = new Stack<NFA>();
        NFA[] nfas;
        while (pos < ch.length) {
            switch (ch[pos]) {
                case '.':
                    ++pos;
                    parsed.add(Regex.any());
                    break;
                case '+':
                    ++pos;
                    parsed.push(Regex.many1(parsed.pop()));
                    break;
                case '?':
                    ++pos;
                    parsed.push(Regex.optional(parsed.pop()));
                    break;
                case '*':
                    ++pos;
                    parsed.push(Regex.many(parsed.pop()));
                    break;
                case '[':
                    ++pos;
                    ++sqrt;
                    parsed.push(parseChars());
                    break;
                case '(':
                    ++pos;
                    ++paren;
                    parsed.push(_parse());
                    break;
                case ')':
                    ++pos;
                    --paren;
                    nfas = new NFA[parsed.size()];
                    parsed.toArray(nfas);
                    return Regex.concat(nfas);
                case '|':
                    ++pos;
                    nfas = new NFA[parsed.size()];
                    parsed.toArray(nfas);
                    NFA left = Regex.concat(nfas);
                    NFA right = _parse();
                    NFA[] ors = new NFA[2];
                    ors[0] = left;
                    ors[1] = right;
                    return Regex.or(ors);
                case '\\':
                    ++pos;
                    ch[pos] = _escape(ch[pos]);
                //goto default
                default:
                    parsed.push(Regex.chr(ch[pos]));
                    ++pos;
                    break;
            }
        }
        nfas = new NFA[parsed.size()];
        parsed.toArray(nfas);
        return Regex.concat(nfas);
    }

    char _escape(char c) {
        switch (c) {
            case '|':
            case '.':
            case '+':
            case '?':
            case '*':
            case '^':
            case '-':
            case '[':
            case ']':
            case '(':
            case ')':
            case '\\':
                return c;
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
        }
        throw new RuntimeException("Regex Parser: unexpected escape char " + c);
    }

    public NFA parseChars() {
        boolean not = false;
        if (ch[pos] == '^') {
            ++pos;
            not = true;
        }

        Stack<Character> cached = new Stack<Character>();
        while (true) {
            switch (ch[pos]) {
                case '-':
                    char begin_char = cached.pop();
                    char end_char = ch[pos + 1];
                    for (int i = begin_char; i <= end_char; i++) {
                        cached.push((char) i);
                    }
                    pos += 2;
                    break;
                case ']':
                    --sqrt;
                    ArrayList<Integer> chars = new ArrayList<Integer>();
                    for (Character c : cached) {
                        chars.add(new Integer(c));
                    }

                    ++pos;
                    return Regex.chars(chars, not);
                case '\\':
                    ++pos;
                    ch[pos] = _escape(ch[pos]);
                //goto default
                default:
                    cached.push(ch[pos]);
                    ++pos;
                    break;
            }
        }
    }
}
