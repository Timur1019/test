package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;


import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi  {

    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
        this.semaphore = new Semaphore(requestLimit);

        long delay = timeUnit.toMillis(1);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.scheduler.scheduleAtFixedRate(() -> semaphore.release(Math.min(requestLimit, semaphore.availablePermits() + 1)), delay, delay, TimeUnit.MILLISECONDS);
    }


    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();
        try {
            HttpPost post = new HttpPost(API_URL);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Signature", signature);

            String json = objectMapper.writeValueAsString(document);
            post.setEntity(new StringEntity(json));

            try(CloseableHttpResponse response = httpClient.execute(post)) {
                System.out.println(response.getStatusLine().getStatusCode());
            }
        }finally {
            semaphore.release();
        }
    }

    public void shutdown() {
        this.scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)){
                scheduler.shutdownNow();
            }
        }catch (InterruptedException e){
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try{
            httpClient.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }


    public static class Document {
        public Description description;
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public boolean importRequest;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public Product[] products;
        public String reg_date;
        public String reg_number;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificate_document;
            public String certificate_document_date;
            public String certificate_document_number;
            public String owner_inn;
            public String producer_inn;
            public String production_date;
            public String tnved_code;
            public String uit_code;
            public String uitu_code;
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);

        Document document = new Document();
        document.description = new Document.Description();
        document.description.participantInn = "1234567890";
        document.doc_id = "doc12345";
        document.doc_status = "NEW";
        document.doc_type = "LP_INTRODUCE_GOODS";
        document.importRequest = true;
        document.owner_inn = "1234567890";
        document.participant_inn = "1234567890";
        document.producer_inn = "0987654321";
        document.production_date = "2020-01-23";
        document.production_type = "OWN_PRODUCTION";
        document.reg_date = "2020-01-23";
        document.reg_number = "reg12345";

        Document.Product product = new Document.Product();
        product.certificate_document = "cert_doc";
        product.certificate_document_date = "2020-01-23";
        product.certificate_document_number = "cert12345";
        product.owner_inn = "1234567890";
        product.producer_inn = "0987654321";
        product.production_date = "2020-01-23";
        product.tnved_code = "123456";
        product.uit_code = "uit12345";
        product.uitu_code = "uitu12345";

        document.products = new Document.Product[]{product};

        String signature = "your-signature-here";

        api.createDocument(document, signature);

        api.shutdown();
    }


}