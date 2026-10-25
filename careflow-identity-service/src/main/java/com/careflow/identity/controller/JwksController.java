package com.careflow.identity.controller;
import com.careflow.identity.security.JwtSigningKeys;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class JwksController {
    private final JwtSigningKeys keys;
    public JwksController(JwtSigningKeys keys) { this.keys = keys; }
    @GetMapping("/api/auth/jwks")
    public Map<String,Object> keys() { return keys.jwks(); }
}
