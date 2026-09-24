package com.flashsale.common.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Defaults are deliberately conservative; Nacos may override each property. */
@ConfigurationProperties(prefix = "flashsale.cache")
public class CacheAsideProperties {
    private Duration ttl = Duration.ofMinutes(5);
    private Duration negativeTtl = Duration.ofSeconds(30);
    private Duration lockTtl = Duration.ofSeconds(5);
    private int retryCount = 3;
    private Duration retryBackoff = Duration.ofMillis(25);
    private Duration ttlJitter = Duration.ofSeconds(60);

    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
    public Duration getNegativeTtl() { return negativeTtl; }
    public void setNegativeTtl(Duration negativeTtl) { this.negativeTtl = negativeTtl; }
    public Duration getLockTtl() { return lockTtl; }
    public void setLockTtl(Duration lockTtl) { this.lockTtl = lockTtl; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Duration getRetryBackoff() { return retryBackoff; }
    public void setRetryBackoff(Duration retryBackoff) { this.retryBackoff = retryBackoff; }
    public Duration getTtlJitter() { return ttlJitter; }
    public void setTtlJitter(Duration ttlJitter) { this.ttlJitter = ttlJitter; }
}
