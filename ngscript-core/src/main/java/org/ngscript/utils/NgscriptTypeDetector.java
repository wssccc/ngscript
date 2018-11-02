package org.ngscript.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NgscriptTypeDetector extends FileTypeDetector {

    private static final String[] KNOWN_NGS_FILES = new String[]{"ngscript"};
    private static final String[] KNOWN_NGS_SUFFIXES = new String[]{".ngs"};
    private static final String NGS_MIME = "application/x-ngscript";
    private static final Pattern SHEBANG_REGEXP = Pattern.compile("^#! ?/usr/bin/(env +ngscript|ngscript).*");

    @Override
    public String probeContentType(Path path) throws IOException {
        final Path fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            return null;
        }

        final String fileName = fileNamePath.toString();
        final String lowerCaseFileName = fileName.toLowerCase(Locale.ROOT);

        for (String candidate : KNOWN_NGS_SUFFIXES) {
            if (lowerCaseFileName.endsWith(candidate)) {
                return NGS_MIME;
            }
        }

        for (String candidate : KNOWN_NGS_FILES) {
            if (fileName.equals(candidate)) {
                return NGS_MIME;
            }
        }

        try (BufferedReader fileContent = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            final String firstLine = fileContent.readLine();
            if (firstLine != null && SHEBANG_REGEXP.matcher(firstLine).matches()) {
                return NGS_MIME;
            }
        } catch (IOException e) {
            // Reading random files as UTF-8 could cause all sorts of errors
        }

        return null;
    }
}

