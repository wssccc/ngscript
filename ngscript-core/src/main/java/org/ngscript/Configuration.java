package org.ngscript;

import lombok.Data;

/**
 * @author wssccc
 */
@Data
public class Configuration {

    public static final Configuration DEFAULT = new Configuration();

    boolean generateDebugInfo = false;
    boolean interactive = false;

}
