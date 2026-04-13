package com.hktools.config;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

public class HttpClientConfig {
    private static final OkHttpClient CLIENT;
    static {
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true);

        // Configure proxy if enabled in config.txt
        try {
            com.hktools.config.ConfigLoader cfg = com.hktools.config.ConfigLoader.getInstance();
            if (cfg.isProxyEnabled()) {
                String host = cfg.getProxyHost();
                int port = cfg.getProxyPort();
                builder.proxy(new Proxy(Proxy.Type.HTTP, new java.net.InetSocketAddress(host, port)));
            }
        } catch (Exception e) {
            // If anything goes wrong reading the config, fall back to no proxy
            e.printStackTrace();
        }

        CLIENT = setSslProxy(builder).build();
    }

    public static OkHttpClient getClient() {
        return CLIENT;
    }

    public static Response executeRequest(Request request) throws IOException {
        return CLIENT.newCall(request).execute();
    }

    public static OkHttpClient.Builder setSslProxy(OkHttpClient.Builder builder) {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };
            try {
                // Install the all-trusting trust manager
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                // Create an ssl socket factory with our all-trusting manager
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
                builder.hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });

            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        return builder;
    }
}

