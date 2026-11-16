package com.homeia.integration;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;

/**
 * Crea el SSLSocketFactory para conectar al broker por TLS con certificados PEM (los que genera
 * docker/mosquitto/pki.sh). Con certificado de cliente se obtiene autenticación mutua (mTLS).
 */
public final class MqttTls {

    private MqttTls() {
    }

    public static SSLSocketFactory socketFactory(String caPemPath, String clientCertPemPath, String clientKeyPemPath)
            throws Exception {
        TrustManagerFactory tmf = null;
        if (caPemPath != null && !caPemPath.isBlank()) {
            KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
            trust.load(null, null);
            int i = 0;
            for (Certificate cert : certificates(Path.of(caPemPath))) {
                trust.setCertificateEntry("ca-" + i++, cert);
            }
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trust);
        }
        KeyManagerFactory kmf = null;
        if (clientCertPemPath != null && !clientCertPemPath.isBlank()) {
            if (clientKeyPemPath == null || clientKeyPemPath.isBlank()) {
                throw new IllegalArgumentException("MQTT_CLIENT_KEY es obligatorio cuando se define MQTT_CLIENT_CERT");
            }
            char[] password = new char[0];
            KeyStore keys = KeyStore.getInstance("PKCS12");
            keys.load(null, null);
            Collection<? extends Certificate> chain = certificates(Path.of(clientCertPemPath));
            keys.setKeyEntry("client", privateKey(Path.of(clientKeyPemPath)), password, chain.toArray(Certificate[]::new));
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keys, password);
        }
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(kmf == null ? null : kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), null);
        return context.getSocketFactory();
    }

    static Collection<? extends Certificate> certificates(Path pem) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (var in = new ByteArrayInputStream(Files.readAllBytes(pem))) {
            Collection<? extends Certificate> certs = factory.generateCertificates(in);
            if (certs.isEmpty()) throw new IllegalArgumentException("No hay certificados en " + pem);
            return certs;
        }
    }

    /** Clave privada PEM PKCS#8 ("BEGIN PRIVATE KEY"), RSA o EC. */
    static PrivateKey privateKey(Path pem) throws Exception {
        String text = Files.readString(pem, StandardCharsets.US_ASCII);
        if (!text.contains("BEGIN PRIVATE KEY")) {
            throw new IllegalArgumentException("La clave debe estar en PKCS#8 (BEGIN PRIVATE KEY). Convierte con: "
                    + "openssl pkcs8 -topk8 -nocrypt -in clave.pem -out clave.pk8.pem");
        }
        String base64 = text.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64));
        for (String algorithm : new String[]{"EC", "RSA"}) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(spec);
            } catch (Exception ignored) {
                // probar el siguiente algoritmo
            }
        }
        throw new IllegalArgumentException("Clave privada no soportada (se aceptan EC y RSA)");
    }
}
