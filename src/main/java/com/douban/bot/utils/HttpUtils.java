package com.douban.bot.utils;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.util.Random;

public class HttpUtils {

    private static final Random random = new Random();
    
    public static CloseableHttpClient createHttpClient() {
        return HttpClients.createDefault();
    }

    public static String getUserAgent() {
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.71 Safari/537.36";
    }

    public static void randomSleep(int minMs, int maxMs) {
        if (maxMs <= minMs) {
            sleep(minMs);
            return;
        }
        int sleepTime = random.nextInt(maxMs - minMs) + minMs;
        sleep(sleepTime);
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String fetchContent(String url, String cookie) throws IOException {
        try (CloseableHttpClient client = createHttpClient()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", getUserAgent());
            request.setHeader("Cookie", cookie);
            
            try (CloseableHttpResponse response = client.execute(request)) {
                if (response.getCode() != 200) {
                    throw new IOException("HTTP request failed with status: " + response.getCode());
                }
                try {
                    return EntityUtils.toString(response.getEntity());
                } catch (ParseException e) {
                    throw new IOException("Failed to parse response entity", e);
                }
            }
        }
    }
}
