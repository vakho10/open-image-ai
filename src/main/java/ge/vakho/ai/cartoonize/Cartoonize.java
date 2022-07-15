package ge.vakho.ai.cartoonize;

import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public class Cartoonize implements Callable<Path> {

    private static final RestTemplate REST_TEMPLATE = new RestTemplate();

    public String postUrl = "https://cartoonize-lkqov62dia-de.a.run.app/cartoonize";

    private Path inputImage;
    private Path outputImage;

    public Cartoonize(Path inputImage) {
        this.inputImage = inputImage;
        outputImage = inputImage.resolveSibling("cartoonized.jpg");
    }

    public Cartoonize(Path inputImage, Path outputImage) {
        this(inputImage);
        this.outputImage = outputImage;
    }

    @Override
    public Path call() throws IOException {
        // (1) Upload image
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        byte[] imageBytes = Files.readAllBytes(inputImage);

        body.add("image", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return inputImage.getFileName().toString();
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> htmlResponse = REST_TEMPLATE.postForEntity(postUrl, requestEntity, String.class);
        System.out.printf("%s]: Got response HTML page back\n", inputImage.getFileName());

        // (2) Parse response HTML and look for the image
        Document doc = Jsoup.parse(htmlResponse.getBody());
        Elements imageLinks = doc.select("a[href][download]");
        if (imageLinks.isEmpty()) {
            throw new IllegalStateException("No image link found on the response HTML page");
        }
        String imageLink = imageLinks.get(0).absUrl("href");
        System.out.printf("%s]: Got image URL: %s\n", inputImage.getFileName(), imageLink);

        // (3) Download the image
        byte[] resultImageBytes;
        try (InputStream inputStream = URI.create(imageLink).toURL().openStream()) {
            resultImageBytes = IOUtils.toByteArray(inputStream);
        }

        Path downloadedFile;
        try {
            downloadedFile = Files.write(outputImage, resultImageBytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.printf("%s]: Downloaded cartoonized image at: %s\n", inputImage.getFileName(), downloadedFile);
        return downloadedFile;
    }

}
