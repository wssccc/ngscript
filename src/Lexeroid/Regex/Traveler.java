/*
 *  wssccc all rights reserved
 */
package Lexeroid.Regex;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public interface Traveler {

    /**
     * visit the state
     *
     * @param state currentState
     * @return continue traverse
     */
    public abstract boolean visit(Vertex state);
}
