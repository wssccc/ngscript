/*
 *  wssccc all rights reserved
 */
package Lexeroid;

import Lexeroid.Regex.DFA;
import Lexeroid.Regex.Vertex;
import java.util.ArrayList;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class Lexer {

    DFA dfa;
    int lines = 0;

    public Lexer(DFA dfa) {
        this.dfa = dfa;
    }

    public void reset() {
        lines = 0;
    }

    /**
     * scan the source code and generate a list of Tokens
     *
     * @param code the source code
     * @return Token list
     * @throws Lexeroid.LexException
     */
    public synchronized ArrayList<LexToken> scanLine(String code) throws LexException {
        ++lines;
        ArrayList<LexToken> tokenList = new ArrayList<LexToken>();
        dfa.reset();
        SourceStream sourceStream = new SourceStream(code);
        //save the latest state to ensure matching longest substring
        boolean latestAcceptState = false;
        Vertex latestAcceptVertex = new Vertex();

        //running
        while (true) {
            int headChar = sourceStream.peek();
            //at the end of the stream
            if (headChar == -1) {
                //clean up
                if (latestAcceptState) {
                    if (latestAcceptVertex.finalStateComment.ignored == false) {
                        tokenList.add(new LexToken(latestAcceptVertex.finalStateComment.acceptStateName, sourceStream.line, dfa.getAcceptedString()));
                    }
                }
                break;
            }
            boolean isMoved = dfa.move(headChar);
            if (isMoved == true) {
                //can move
                if (dfa.currentState.isFinal()) {
                    latestAcceptState = true;
                    latestAcceptVertex = dfa.currentState;
                }
                sourceStream.forward();
            } else {
                //cannot move, the matched substring comes to an edge
                if (latestAcceptState == true) {
                    //restore the latest accepted state
                    if (latestAcceptVertex.finalStateComment.ignored == false) {
                        //if not ignored
                        tokenList.add(new LexToken(latestAcceptVertex.finalStateComment.acceptStateName, sourceStream.line, dfa.getAcceptedString()));
                    }
                    latestAcceptState = false;
                    dfa.reset();
                } else {
                    //cannot move, and the latest state is not available
                    throw new LexException(sourceStream, "Unexpected char " + headChar, tokenList.toString());
                }
            }
        }
        return tokenList;
    }
}
