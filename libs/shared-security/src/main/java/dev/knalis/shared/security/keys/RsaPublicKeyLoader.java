package dev.knalis.shared.security.keys;

import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RsaPublicKeyLoader {
    
    private final ResourceLoader resourceLoader;
    
    public RsaPublicKeyLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    
    public RSAPublicKey load(String resourcePath) {
        try {
            String pem = resourceLoader.getResource(resourcePath)
                    .getContentAsString(StandardCharsets.UTF_8);
            
            String content = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            
            byte[] decoded = Base64.getDecoder().decode(content);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load RSA public key from: " + resourcePath, ex);
        }
    }
}
