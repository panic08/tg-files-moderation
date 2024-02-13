package ru.marthastudios.tgsteamcookiegetter.util;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class UrlFileDownloaderUtil {
    public File downloadFile(String URL, String prefix, String suffix, String path) throws IOException {
        java.net.URL fileUrl = new URL(URL);
        var in = fileUrl.openStream();

        File file = new File(path + prefix + suffix);

        Path filePath = file.toPath();
        Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);

        return file;
    }

    public File downloadFileTemp(String URL, String prefix, String suffix) throws IOException {
        java.net.URL fileUrl = new URL(URL);
        var in = fileUrl.openStream();

        File tempFile = File.createTempFile(prefix, suffix);

        Path filePath = tempFile.toPath();
        Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);

        return tempFile;
    }
}
