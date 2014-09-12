/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Lexeroid.Regex;

/**
 *
 * @author wssccc
 */
public class Edge {

    Input input;
    Vertex nextState;

    public Edge(Input input, Vertex next) {
        this.input = input;
        this.nextState = next;
    }

    public boolean accept(char c) {
        return input.accept(c);
    }

//    public static Comparator<Edge> comparator = new Comparator<Edge>() {
//
//        @Override
//        public int compare(Edge o1, Edge o2) {
//            return (o1.input.end - o1.input.begin) - (o2.input.end - o2.input.begin);
//        }
//    };
    @Override
    public String toString() {
        return "\r\n    " + input + " => " + nextState.stateId;
    }

}
