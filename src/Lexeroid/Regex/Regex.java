/*
 *  wssccc all rights reserved
 */
package Lexeroid.Regex;

import java.util.ArrayList;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class Regex {

    /**
     * create a NFA that accept a single char
     *
     * @param chr accept char
     * @return
     */
    public static NFA chr(int chr) {
        NFA nfa = new NFA();
        nfa.begin_state = new Vertex();
        nfa.exit_state = new Vertex();

        nfa.begin_state.edges.add(new Edge(Input.chr(chr), nfa.exit_state));
        return nfa;
    }

    /**
     * create a NFA that not accept the specified char
     *
     * @param chr
     * @return
     */
    public static NFA not(int chr) {
        ArrayList<Integer> chars = new ArrayList<Integer>();
        chars.add(chr);
        //create not-accept collection of chars, even if it just has one element
        return chars(chars, true);
    }

    /**
     * create a NFA that accept a String
     *
     * @param str
     * @return
     */
    public static NFA string(String str) {
        NFA nfa = new NFA();
        nfa.begin_state = new Vertex();

        Vertex ptr = nfa.begin_state;
        char[] chrs = str.toCharArray();
        for (char c : chrs) {
            Vertex next = new Vertex();
            ptr.edges.add(new Edge(Input.chr(c), next));
            ptr = next;
        }
        nfa.exit_state = ptr;
        return nfa;
    }

    /**
     * make the NFA select one of the NFAs
     *
     * @param nfas
     * @return
     */
    public static NFA or(NFA... nfas) {
        NFA first = nfas[0];
        for (int i = 1; i < nfas.length; ++i) {
            first.begin_state.edges.add(new Edge(Input.epsilon, nfas[i].begin_state));
            nfas[i].exit_state.edges.add(new Edge(Input.epsilon, first.exit_state));
        }
        return first;
    }

    /**
     * concat NFAs into one NFA
     *
     * @param nfas NFAs to be concat
     * @return
     */
    public static NFA concat(NFA... nfas) {
        NFA reference = nfas[0];
        for (int i = 0; i < nfas.length - 1; i++) {
            //add epsilon edge between tail of the last and the head of the next
            nfas[i].exit_state.edges.add(new Edge(Input.epsilon, nfas[i + 1].begin_state));
            //update reference's tail to the newest tail
            reference.exit_state = nfas[i + 1].exit_state;
        }

        return reference;
    }

    /**
     * create a NFA that accept(or not) a collection of chars
     *
     * @param chars ArrayList of chars
     * @param not accept or not
     * @return
     */
    public static NFA chars(ArrayList<Integer> chars, boolean not) {
        NFA nfa = new NFA();
        nfa.begin_state = new Vertex();
        nfa.exit_state = new Vertex();

        nfa.begin_state.edges.add(new Edge(Input.chars(chars, not), nfa.exit_state));

        return nfa;
    }

    /**
     * create a NFA that accept chars in range
     *
     * @param start start of the char range
     * @param end end of the char range(inclusive)
     * @return
     */
    public static NFA range(int start, int end) {
        ArrayList<Integer> chars = new ArrayList<Integer>();
        for (int i = start; i <= end; i++) {
            chars.add(i);
        }
        //expand the range into collection, use chars() instead
        return chars(chars, false);
    }

    /**
     * create a NFA that accepts ANY char
     *
     * @return
     */
    public static NFA any() {
        NFA nfa = new NFA();
        nfa.begin_state = new Vertex();
        nfa.exit_state = new Vertex();

        nfa.begin_state.edges.add(new Edge(Input.any, nfa.exit_state));

        return nfa;
    }

    /**
     * make the target NFA run n >= 0 times
     *
     * @param targetNFA
     * @return
     */
    public static NFA many(NFA targetNFA) {
        Vertex new_begin = new Vertex();
        Vertex new_exit = new Vertex();
        new_begin.edges.add(new Edge(Input.epsilon, targetNFA.begin_state));
        new_begin.edges.add(new Edge(Input.epsilon, new_exit));
        targetNFA.exit_state.edges.add(new Edge(Input.epsilon, targetNFA.begin_state));
        targetNFA.exit_state.edges.add(new Edge(Input.epsilon, new_exit));
        targetNFA.begin_state = new_begin;
        targetNFA.exit_state = new_exit;
        return targetNFA;
    }

    /**
     * make the target NFA run n >= 1 times
     *
     * @param targetNFA
     * @return
     */
    public static NFA many1(NFA targetNFA) {
        Vertex new_begin = new Vertex();
        Vertex new_exit = new Vertex();
        new_begin.edges.add(new Edge(Input.epsilon, targetNFA.begin_state));
        targetNFA.exit_state.edges.add(new Edge(Input.epsilon, targetNFA.begin_state));
        targetNFA.exit_state.edges.add(new Edge(Input.epsilon, new_exit));

        targetNFA.begin_state = new_begin;
        targetNFA.exit_state = new_exit;
        return targetNFA;
    }

    /**
     * create a NFA that accepts digits
     *
     * @return
     */
    public static NFA digit() {
        return Regex.range('0', '9');
    }

    /**
     * create a NFA that accepts letters
     *
     * @return
     */
    public static NFA letter() {
        return Regex.or(Regex.range('a', 'z'), Regex.range('A', 'Z'));
    }

    /**
     * make a NFA run once or ..
     *
     * @param targetNFA
     * @return
     */
    public static NFA optional(NFA targetNFA) {
        Vertex new_begin = new Vertex();
        Vertex new_end = new Vertex();

        new_begin.edges.add(new Edge(Input.epsilon, targetNFA.begin_state));
        targetNFA.exit_state.edges.add(new Edge(Input.epsilon, new_end));
        new_begin.edges.add(new Edge(Input.epsilon, new_end));

        targetNFA.begin_state = new_begin;
        targetNFA.exit_state = new_end;
        return targetNFA;
    }

    /**
     * compile regex string to NFA
     *
     * @param regex
     * @return
     */
    public static NFA compile(String regex) {
        //System.out.println(regex);
        return new RegexParser(regex).parse();
    }
}
