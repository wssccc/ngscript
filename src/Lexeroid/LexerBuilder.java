/*
 *  wssccc all rights reserved
 */
package Lexeroid;

import Lexeroid.Regex.DFA;
import Lexeroid.Regex.DFABuilder;
import Lexeroid.Regex.Edge;
import Lexeroid.Regex.FinalStateComment;
import Lexeroid.Regex.Input;
import Lexeroid.Regex.NFA;
import Lexeroid.Regex.Vertex;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class LexerBuilder {

    NFA nfa;

    public LexerBuilder() {
        nfa = new NFA();
        nfa.begin_state = new Vertex();
    }

    /**
     * define a token
     *
     * @param identifier
     * @param regex
     */
    public void defineToken(String identifier, NFA regex) {
        nfa.begin_state.edges.add(new Edge(Input.epsilon, regex.begin_state));
        regex.exit_state.finalStateComment = new FinalStateComment(identifier, false);
    }

    /**
     * define a token
     *
     * @param identifier
     * @param regex
     * @param ignored
     */
    public void defineToken(String identifier, NFA regex, boolean ignored) {
        nfa.begin_state.edges.add(new Edge(Input.epsilon, regex.begin_state));
        regex.exit_state.finalStateComment = new FinalStateComment(identifier, ignored);
    }

    /**
     * build a lexer
     *
     * @return Lexer
     */
    public DFA buildDFA() {

        //nfa.print();
        nfa.normalize();
        DFABuilder builder = new DFABuilder();
        DFA dfa = builder.build(nfa);
        //        dfa.traverse(new Traveler() {
        //
        //            @Override
        //            public boolean visit(Vertex state) {
        //                System.out.println(state.acceptStateName);
        //                return true;
        //            }
        //        });
        //dfa.print();
        //System.out.println("=====================================================");
        //Vertex.precedence_counter=0;
        return dfa;
    }
}
