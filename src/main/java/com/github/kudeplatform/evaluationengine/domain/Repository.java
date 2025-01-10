package com.github.kudeplatform.evaluationengine.domain;

import org.springframework.util.StringUtils;

public record
Repository(String name, String url, String username, String token) {

    public String getRepositoryUrlWithCredentials() {
        if (StringUtils.hasText(this.username) && StringUtils.hasText(this.token)) {
            return this.url.replace("https://", "https://" + this.username + ":" + this.token + "@");
        }
        return this.url;
    }

}
