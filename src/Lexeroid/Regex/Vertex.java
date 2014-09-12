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
public class Vertex {

    //inner states
    public static int precedence_counter = 0;
    final int stateId;
    //members
    public ArrayList<Edge> edges;
    public FinalStateComment finalStateComment = null;

    public Vertex() {
        stateId = precedence_counter++;
        edges = new ArrayList<Edge>();
    }

    public void addToMoveSet(Input c, HashSet<Vertex> nfs) {
        for (Edge edge : edges) {
            //in accept range
            if (edge.input == c && edge.input.type != Input.TYPE_EPSILON) {
                nfs.add(edge.nextState);
            }
        }
    }

    public boolean isFinal() {
        return finalStateComment != null;
    }

    @Override
    public String toString() {
        return "FAVertex(" + stateId + ") " + (finalStateComment == null ? "" : "accept as " + finalStateComment.acceptStateName + (finalStateComment.ignored ? " ignored " : "")) + " edge=" + edges + "\r\n";
    }

}
