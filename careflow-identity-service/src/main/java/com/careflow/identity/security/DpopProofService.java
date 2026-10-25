package com.careflow.identity.security;

import com.careflow.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/** DPoP proof validation shared by authentication and resource introspection. */
@Service
public class DpopProofService {
    private final ObjectMapper mapper;
    private final DpopReplayStore replays;
    private final Set<String> origins;
    public DpopProofService(ObjectMapper mapper, DpopReplayStore replays,
            @Value("${security.dpop.public-origins:https://careflow-demo.online}") String configuredOrigins) {
        this.mapper = mapper; this.replays = replays;
        origins = Set.copyOf(Arrays.asList(configuredOrigins.split(",")));
        for (String origin : origins) {
            URI uri = URI.create(origin);
            if (!("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost())))
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) throw new IllegalArgumentException("DPoP origins must be HTTPS or explicit loopback origins without a path");
        }
    }
    public String verify(String proof, String method, String path, String accessToken, String expectedJkt) {
        try {
            if (proof == null || proof.length() > 8192 || method == null || path == null || !path.startsWith("/")) throw denied();
            String[] parts = proof.split("\\.", -1);
            if (parts.length != 3) throw denied();
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode jwk = header.path("jwk");
            if (!"dpop+jwt".equals(header.path("typ").asText()) || !"ES256".equals(header.path("alg").asText())
                    || !"EC".equals(jwk.path("kty").asText()) || !"P-256".equals(jwk.path("crv").asText()) || jwk.has("d")) throw denied();
            String x = jwk.path("x").asText(), y = jwk.path("y").asText();
            byte[] xb = Base64.getUrlDecoder().decode(x), yb = Base64.getUrlDecoder().decode(y);
            if (xb.length != 32 || yb.length != 32) throw denied();
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC"); parameters.init(new ECGenParameterSpec("secp256r1"));
            PublicKey key = KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new java.security.spec.ECPoint(new BigInteger(1,xb),new BigInteger(1,yb)), parameters.getParameterSpec(ECParameterSpec.class)));
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initVerify(key); signature.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) throw denied();
            String canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\""+x+"\",\"y\":\""+y+"\"}";
            String jkt = base64(sha256(canonical));
            if (expectedJkt != null && !constant(expectedJkt,jkt)) throw denied();
            URI uri = URI.create(claims.path("htu").asText());
            String origin = uri.getScheme()+"://"+uri.getRawAuthority();
            if (!origins.contains(origin) || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !path.equals(uri.getRawPath()) || !method.equals(claims.path("htm").asText())) throw denied();
            long issued = claims.path("iat").asLong(Long.MIN_VALUE), now = Instant.now().getEpochSecond();
            String jti = claims.path("jti").asText();
            if (issued < now - 60 || issued > now + 5 || jti.length() < 16 || jti.length() > 128) throw denied();
            if (accessToken != null && !constant(base64(sha256(accessToken)), claims.path("ath").asText())) throw denied();
            replays.consume(java.util.HexFormat.of().formatHex(sha256(jkt+":"+jti)));
            return jkt;
        } catch (BusinessException exception) { throw exception; }
        catch (org.springframework.dao.DataAccessException exception) { throw new BusinessException(503,"Không thể kiểm tra replay token"); }
        catch (Exception exception) { throw denied(); }
    }
    private boolean constant(String a,String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)); }
    private byte[] sha256(String value) throws GeneralSecurityException { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
    private String base64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private BusinessException denied() { return new BusinessException(401,"DPoP proof không hợp lệ hoặc không khớp khóa client/request"); }
}
