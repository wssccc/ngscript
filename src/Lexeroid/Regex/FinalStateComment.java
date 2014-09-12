/*
 *  wssccc all rights reserved
 */
package Lexeroid.Regex;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class FinalStateComment {

    public String acceptStateName;
    public boolean ignored;

    public FinalStateComment(String acceptStateName, boolean ignored) {
        this.acceptStateName = acceptStateName;
        this.ignored = ignored;
    }

}
