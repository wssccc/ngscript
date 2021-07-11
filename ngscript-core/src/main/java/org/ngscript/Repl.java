/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

/**
 * @author wssccc
 */
public class Repl {

    public static void main(String[] args) throws IOException {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        LineReader lineReader = LineReaderBuilder.builder()
                .history(new DefaultHistory())
                .terminal(terminal)
                .build();
        Configuration configuration = new Configuration();
        configuration.setInteractive(true);
        Ngscript ngscript = new Ngscript(configuration);
        String prompt = "ngscript> ";
        boolean running = true;
        while (running) {
            String line;
            try {
                line = lineReader.readLine(prompt);
                boolean compiled = ngscript.feed(line);
                if (compiled) {
                    prompt = "ngscript> ";
                } else {
                    prompt = "... ";
                }
            } catch (UserInterruptException | EndOfFileException e) {
                running = false;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        System.out.println("\nBye.");
    }
}
