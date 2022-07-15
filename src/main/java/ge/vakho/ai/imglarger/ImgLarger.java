package ge.vakho.ai.imglarger;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;

public class ImgLarger implements Callable<Path> {

    private static final RestTemplate REST_TEMPLATE;

    static {
        REST_TEMPLATE = new RestTemplate();
        TrustStrategy acceptingTrustStrategy = (x509Certificates, s) -> true;
        SSLContext sslContext;
        try {
            sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(csf).build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);
        REST_TEMPLATE.setRequestFactory(requestFactory);
    }

    public String postUrl = "https://access2.imglarger.com:8999/upload";
    public String statusUrl = "https://access2.imglarger.com:8999/status/{identifier}";
    public String RESULT_URL = "http://access2.imglarger.com:8889/results/{identifier}_{scale}x.jpg";

    private Path inputImage;
    private Path outputImage;
    private Scale scale = Scale.X2;

    public ImgLarger(Path inputImage) {
        this.inputImage = inputImage;
        outputImage = inputImage.resolveSibling("output.png");
    }

    public ImgLarger(Path inputImage, Scale scale) {
        this(inputImage);
        this.scale = scale;
    }

    public ImgLarger(Path inputImage, Path outputImage) {
        this(inputImage);
        this.outputImage = outputImage;
    }

    public ImgLarger(Path inputImage, Path outputImage, Scale scale) {
        this(inputImage, scale);
        this.outputImage = outputImage;
    }

    @Override
    public Path call() {
        long start = System.currentTimeMillis();
        // (1) Upload image
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("Alg", "slow"); // This parameter has no effect, but the request still had it ¯\_(ツ)_/¯
        body.add("scaleRadio", scale.getValue());

        byte[] imageBytes;
        try {
            imageBytes = Files.readAllBytes(inputImage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        body.add("myfile", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return inputImage.getFileName().toString();
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> identifierResponse = REST_TEMPLATE.postForEntity(postUrl, requestEntity, String.class);
        String identifier = identifierResponse.getBody();
        System.out.printf("%s]: Uploaded image and got identifier: %s\n", inputImage.getFileName(), identifier);

        // (2) Poll status
        ResponseEntity<String> statusResponse;
        do {
            statusResponse = REST_TEMPLATE.exchange(statusUrl, HttpMethod.GET, null, String.class, identifier);
        }
        while (!statusResponse.getBody().equals("success"));
        System.out.printf("%s]: Got success for identifier: %s\n", inputImage.getFileName(), identifier);

        // (3) Download image
        ResponseEntity<byte[]> resultResponse = REST_TEMPLATE.exchange(
                RESULT_URL, HttpMethod.GET, null, byte[].class, identifier, scale.getValue());
        Path downloadedFile;
        try {
            downloadedFile = Files.write(outputImage, resultResponse.getBody());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        long stop = System.currentTimeMillis() - start;
        System.out.printf("%s]: Identifier file: %s downloaded at: %s, in %f seconds\n", inputImage.getFileName(), identifier, downloadedFile, stop / 1000f);
        return downloadedFile;
    }

}
