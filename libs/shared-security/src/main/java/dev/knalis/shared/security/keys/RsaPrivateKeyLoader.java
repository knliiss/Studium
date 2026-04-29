package dev.knalis.shared.security.keys;

import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class RsaPrivateKeyLoader {
    
    private final ResourceLoader resourceLoader;
    
    public RsaPrivateKeyLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    
    public RSAPrivateKey load(String resourcePath) {
        try {
            String pem = resourceLoader.getResource(resourcePath)
                    .getContentAsString(StandardCharsets.UTF_8);
            
            String content = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            
            byte[] decoded = Base64.getDecoder().decode(content);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load RSA private key from: " + resourcePath, ex);
        }
    }
}
