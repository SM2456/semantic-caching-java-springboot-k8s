package com.acme.cache;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CacheController {
    @GetMapping("/cache/lookup")
    public ResponseEntity<String> lookup(@RequestParam("key") String key) {
        return ResponseEntity.ok("miss:" + key);
    }
}