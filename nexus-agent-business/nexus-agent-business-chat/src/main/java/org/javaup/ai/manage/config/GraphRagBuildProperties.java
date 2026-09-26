package org.javaup.ai.manage.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;
import org.javaup.ai.manage.service.SystemConfigProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.graph-rag.build")
public class GraphRagBuildProperties {

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Autowired(required = false)
    private SystemConfigProvider systemConfigProvider;

    private boolean leaseEnabled = true;

    private int leaseTtlSeconds = 6000;

    private int maxAttempts = 2;

    private long retryBackoffMillis = 500L;

    public boolean isLeaseEnabled() { return managed().leaseEnabled; }
    public int getLeaseTtlSeconds() { return managed().leaseTtlSeconds; }
    public int getMaxAttempts() { return managed().maxAttempts; }
    public long getRetryBackoffMillis() { return managed().retryBackoffMillis; }

    private GraphRagBuildProperties managed() {
        if (systemConfigProvider == null) {
            return this;
        }
        GraphRagBuildProperties managed = systemConfigProvider.currentSnapshot().getGraphRagBuild();
        return managed == null || managed == this ? this : managed;
    }
}
