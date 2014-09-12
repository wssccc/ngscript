/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package Lexeroid.Regex;

/**
 *
 * @author wssccc
 */
public final class DFA extends FA {

    public Vertex currentState;
    StringBuilder builder = new StringBuilder();

    /**
     * reset the machine
     */
    public void reset() {
        this.currentState = this.begin_state;
        builder = new StringBuilder();
    }

    /**
     * get accepted string
     *
     * @return the string
     */
    public String getAcceptedString() {
        return builder.toString();
    }

    /**
     * transfer machine state with the input
     *
     * @param input machine input
     * @return true if the machine accepted the input
     */
    public boolean move(int input) {
        for (Edge de : currentState.edges) {
            //accept in range
            if (de.input.accept(input)) {
                this.currentState = de.nextState;
                builder.append((char) input);
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "state=" + currentState + " read=" + builder;
    }

}
