/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Lexeroid.Regex;

import java.util.ArrayList;
import java.util.HashSet;

/**
 *
 * @author wssccc
 */
class Subset {

    //the class is used in subset method to build DFA
    boolean marked = false;
    HashSet<Vertex> nfaStates;
    Vertex equivalentDFAVertex;

    public Subset(HashSet<Vertex> states, Vertex dfaVertex) {
        this.nfaStates = states;
        this.equivalentDFAVertex = dfaVertex;
    }

    public FinalStateComment getNFAComment() {
        /*
         notice that the NFA comment may be conflicted in certain situations,
         choose the right one by stateId
         */
        int precedence = Integer.MAX_VALUE;
        Vertex nsresult = null;
        for (Vertex ns : nfaStates) {
            if (ns.finalStateComment != null) {
                if (ns.stateId < precedence) {
                    precedence = ns.stateId;
                    nsresult = ns;
                }
            }
        }
        if (nsresult != null) {
            return nsresult.finalStateComment;
        } else {
            return null;
        }
    }
}

public class DFABuilder {

    HashSet<Vertex> getMoveSet(HashSet<Vertex> T, Input input) {
        HashSet<Vertex> nt = new HashSet<Vertex>();
        for (Vertex state : T) {
            state.addToMoveSet(input, nt);
        }
        return nt;
    }

    Subset getUnmarked(ArrayList<Subset> c) {
        for (Subset cs : c) {
            if (cs.marked == false) {
                return cs;
            }
        }
        return null;
    }

    Subset getEquivalentSubset(HashSet<Vertex> eqStates, ArrayList<Subset> subsets) {
        for (Subset subset : subsets) {
            if (subset.nfaStates.equals(eqStates)) {
                return subset;
            }
        }
        return null;
    }

    /**
     * build DFA from NFA
     *
     * @param nfa the NFA
     * @return build DFA
     */
    public DFA build(NFA nfa) {
        ArrayList<Input> allinputs = nfa.getAllInput();
        ArrayList<Subset> subsetList = new ArrayList<Subset>();
        //build DFA
        DFA resultDFA = new DFA();
        resultDFA.begin_state = new Vertex();
        resultDFA.reset();
        Subset t0 = new Subset(nfa.e_closure(nfa.begin_state), resultDFA.begin_state);
        //
        subsetList.add(t0);
        Subset selectedSubset;
        while ((selectedSubset = getUnmarked(subsetList)) != null) {
            selectedSubset.marked = true;
            for (Input input : allinputs) {
                HashSet<Vertex> e_closure_vertexes = nfa.e_closure(getMoveSet(selectedSubset.nfaStates, input));
                //optimized
                if (e_closure_vertexes.size() > 0) {
                    Subset equivalentSubset = getEquivalentSubset(e_closure_vertexes, subsetList);
                    if (equivalentSubset == null) {
                        Vertex v = new Vertex();
                        equivalentSubset = new Subset(e_closure_vertexes, v);
                        subsetList.add(equivalentSubset);
                    }
                    Vertex selectedSubsetVertex = selectedSubset.equivalentDFAVertex;
                    Vertex equivalentSubsetVertex = equivalentSubset.equivalentDFAVertex;
                    selectedSubsetVertex.finalStateComment = selectedSubset.getNFAComment();
                    equivalentSubsetVertex.finalStateComment = equivalentSubset.getNFAComment();
                    selectedSubsetVertex.edges.add(new Edge(input, equivalentSubsetVertex));
                }
            }
        }

        //state id is used for normalization
        return resultDFA;
    }
}
