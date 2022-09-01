package ge.vakho.ai.cartoonize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class CartoonizeDemo {

    public static void main(String[] args) throws IOException {
        Path srcPath = Paths.get("F:/data_src");
        Path dstPath = srcPath.resolveSibling("data_dst");

        // Create the output folder if it doesn't exist
        if (Files.notExists(dstPath)) {
            Files.createDirectories(dstPath);
        }

        // I use executor service to launch multiple processes at once
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        List<String> processedFiles = Files.walk(dstPath)
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(filename -> filename.substring(0, filename.lastIndexOf(".")))
                .collect(Collectors.toList());

        Files.walk(srcPath)
                // (1) Grab all images from folder
                .filter(Files::isRegularFile)
                // ! Ignore previously processes files !
                .filter(file -> !processedFiles.contains(file.getFileName().toString().substring(0, file.getFileName().toString().lastIndexOf("."))))
                // (2) Wrap in class
                .map(inputImage -> {
                    final String filename = inputImage.getFileName().toString();
                    return new Cartoonize(
                            inputImage,
                            dstPath.resolve(filename.substring(0, filename.lastIndexOf(".")).concat(".jpg")));
                })
                // (3) Execute tasks
                .forEach(executorService::submit);
    }

}
