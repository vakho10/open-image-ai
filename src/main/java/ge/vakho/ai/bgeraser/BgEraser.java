package ge.vakho.ai.bgeraser;

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

public class BgEraser implements Callable<Path> {

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

    public String postUrl = "https://access2.bgeraser.com:8558/upload";
    public String statusUrl = "https://access2.bgeraser.com:8558/status/{identifier}";
    public String resultUrl = "https://access2.bgeraser.com:8889/results/{identifier}.png";

    private Path inputImage;
    private Path outputImage;

    public BgEraser(Path inputImage) {
        this.inputImage = inputImage;
        outputImage = inputImage.resolveSibling("output.png");
    }

    public BgEraser(Path inputImage, Path outputImage) {
        this(inputImage);
        this.outputImage = outputImage;
    }

    @Override
    public Path call() throws IOException {
        // (1) Upload image
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("Alg", "slow"); // This parameter has no effect, but the request still had it ¯\_(ツ)_/¯
        body.add("scaleRadio", "4"); // This is always 4 for bg remover

        byte[] imageBytes = Files.readAllBytes(inputImage);

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
                resultUrl, HttpMethod.GET, null, byte[].class, identifier);
        Path downloadedFile = Files.write(outputImage, resultResponse.getBody());

        System.out.printf("%s]: Identifier file: %s downloaded at: %s\n", inputImage.getFileName(), identifier, downloadedFile);
        return downloadedFile;
    }

}
