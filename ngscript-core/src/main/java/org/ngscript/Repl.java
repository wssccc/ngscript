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
        Ngscript ngscript = new Ngscript();
        String prompt = "ngscript> ";
        while (true) {
            String line;
            try {
                line = lineReader.readLine(prompt);
                boolean compiled = ngscript.feed(line);
                if (compiled) {
                    prompt = "ngscript> ";
                } else {
                    prompt = "... ";
                }
            } catch (UserInterruptException e) {
                // Do nothing
            } catch (EndOfFileException e) {
                System.out.println("\nBye.");
                return;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
