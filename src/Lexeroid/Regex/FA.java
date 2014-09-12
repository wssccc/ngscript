/*
 *  wssccc all rights reserved
 */
package Lexeroid.Regex;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public abstract class FA {

    public Vertex begin_state;
    private int count;

    public void print() {
        traverse(new Traveler() {

            @Override
            public boolean visit(Vertex state) {
                System.out.println(state);
                return true;
            }
        });
    }

    /**
     * traverse the states in the machine
     *
     * @param traveler the visitor
     */
    public void traverse(Traveler traveler) {
        ArrayList<Vertex> visitedVertex = new ArrayList<Vertex>();
        Queue<Vertex> q = new LinkedList<Vertex>();
        q.offer(begin_state);
        boolean running = true;
        while (!q.isEmpty() && running) {
            Vertex currentVertex = q.poll();
            //visit
            if (visitedVertex.contains(currentVertex)) {
                continue;
            }
            running = traveler.visit(currentVertex);
            visitedVertex.add(currentVertex);
            for (Edge edge : currentVertex.edges) {
                if (visitedVertex.contains(edge.nextState) == false) {
                    q.offer(edge.nextState);
                }
            }
        }
    }
}
