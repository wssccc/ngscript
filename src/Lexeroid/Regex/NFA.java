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
public class NFA extends FA {

    public Vertex exit_state;

    /**
     * split intersected edges
     *
     * @param edges
     * @return
     */
    boolean reduce(ArrayList<Edge> edges) {
        for (int i = 0; i < edges.size(); ++i) {
            Edge tobeSplitted = edges.get(i);

            Input[] inputs = tobeSplitted.input.split();
            if (inputs != null) {
                //add splitted
                for (Input reged : inputs) {
                    edges.add(new Edge(reged, tobeSplitted.nextState));
                }
                edges.remove(tobeSplitted);
                return true;
            }
        }
        return false;
    }

    /**
     * reduce all
     */
    public void normalize() {

        traverse(new Traveler() {

            @Override
            public boolean visit(Vertex state) {
                while (reduce(state.edges)) {
                    //do nothing
                }
                return true;
            }
        });
    }

    ArrayList<Input> getAllInput() {
        final ArrayList<Input> inputList = new ArrayList<Input>();
        //collect inputs
        inputList.add(Input.undefined);
        inputList.addAll(Input.registeredInput.values());
        return inputList;
    }

    HashSet<Vertex> e_closure(Vertex state, HashSet<Vertex> capturedEcSet) {

        if (capturedEcSet == null) {

            capturedEcSet = new HashSet<Vertex>();
            capturedEcSet.add(state);
        }
        for (Edge ne : state.edges) {
            if (ne.input == Input.epsilon) {
                if (!capturedEcSet.contains(ne.nextState)) {
                    capturedEcSet.add(ne.nextState);
                    //System.out.println("from " + ne.state + " to " + ne.newstate);
                    e_closure(ne.nextState, capturedEcSet);
                }
            }
        }

        return capturedEcSet;

    }

    public HashSet<Vertex> e_closure(Vertex state) {
        return e_closure(state, null);
    }

    public HashSet<Vertex> e_closure(HashSet<Vertex> states) {
        HashSet<Vertex> ecs = new HashSet<Vertex>();
        for (Vertex st : states) {
            ecs.addAll(e_closure(st, null));
        }
        //Collections.sort(ecs, comparator);
        return ecs;
    }
}
