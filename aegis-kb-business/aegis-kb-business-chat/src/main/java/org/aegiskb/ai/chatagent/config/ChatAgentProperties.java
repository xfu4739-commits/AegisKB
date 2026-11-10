package org.aegiskb.ai.chatagent.config;

import org.aegiskb.ai.manage.service.SystemConfigProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat")
public class ChatAgentProperties {

    @Autowired(required = false)
    private SystemConfigProvider systemConfigProvider;

    private boolean recommendationEnabled = true;
    private int maxModelCallsPerRun = 8;
    private int maxModelCallsPerThread = 40;
    private int maxToolCallsPerRun = 6;
    private int maxToolCallsPerThread = 30;
    private int historyPreviewTurns = 4;
    private long recommendationTimeoutMs = 3000L;
    private int sagMaxModelCallsPerRun = 10;
    private int sagMaxModelCallsPerThread = 60;
    private int sagMaxKbSearchCallsPerRun = 6;
    private int sagMaxKbSearchCallsPerThread = 30;
    private int sagMaxWebSearchCallsPerRun = 4;
    private int sagMaxWebSearchCallsPerThread = 20;

    public boolean isRecommendationEnabled() {
        return managed().recommendationEnabled;
    }

    public void setRecommendationEnabled(boolean recommendationEnabled) {
        this.recommendationEnabled = recommendationEnabled;
    }

    public int getMaxModelCallsPerRun() {
        return managed().maxModelCallsPerRun;
    }

    public void setMaxModelCallsPerRun(int maxModelCallsPerRun) {
        this.maxModelCallsPerRun = maxModelCallsPerRun;
    }

    public int getMaxModelCallsPerThread() {
        return managed().maxModelCallsPerThread;
    }

    public void setMaxModelCallsPerThread(int maxModelCallsPerThread) {
        this.maxModelCallsPerThread = maxModelCallsPerThread;
    }

    public int getMaxToolCallsPerRun() {
        return managed().maxToolCallsPerRun;
    }

    public void setMaxToolCallsPerRun(int maxToolCallsPerRun) {
        this.maxToolCallsPerRun = maxToolCallsPerRun;
    }

    public int getMaxToolCallsPerThread() {
        return managed().maxToolCallsPerThread;
    }

    public void setMaxToolCallsPerThread(int maxToolCallsPerThread) {
        this.maxToolCallsPerThread = maxToolCallsPerThread;
    }

    public int getHistoryPreviewTurns() {
        return managed().historyPreviewTurns;
    }

    public void setHistoryPreviewTurns(int historyPreviewTurns) {
        this.historyPreviewTurns = historyPreviewTurns;
    }

    public long getRecommendationTimeoutMs() {
        return managed().recommendationTimeoutMs;
    }

    public void setRecommendationTimeoutMs(long recommendationTimeoutMs) {
        this.recommendationTimeoutMs = recommendationTimeoutMs;
    }

    public int getSagMaxModelCallsPerRun() {
        return managed().sagMaxModelCallsPerRun;
    }

    public void setSagMaxModelCallsPerRun(int sagMaxModelCallsPerRun) {
        this.sagMaxModelCallsPerRun = sagMaxModelCallsPerRun;
    }

    public int getSagMaxModelCallsPerThread() {
        return managed().sagMaxModelCallsPerThread;
    }

    public void setSagMaxModelCallsPerThread(int sagMaxModelCallsPerThread) {
        this.sagMaxModelCallsPerThread = sagMaxModelCallsPerThread;
    }

    public int getSagMaxKbSearchCallsPerRun() {
        return managed().sagMaxKbSearchCallsPerRun;
    }

    public void setSagMaxKbSearchCallsPerRun(int sagMaxKbSearchCallsPerRun) {
        this.sagMaxKbSearchCallsPerRun = sagMaxKbSearchCallsPerRun;
    }

    public int getSagMaxKbSearchCallsPerThread() {
        return managed().sagMaxKbSearchCallsPerThread;
    }

    public void setSagMaxKbSearchCallsPerThread(int sagMaxKbSearchCallsPerThread) {
        this.sagMaxKbSearchCallsPerThread = sagMaxKbSearchCallsPerThread;
    }

    public int getSagMaxWebSearchCallsPerRun() {
        return managed().sagMaxWebSearchCallsPerRun;
    }

    public void setSagMaxWebSearchCallsPerRun(int sagMaxWebSearchCallsPerRun) {
        this.sagMaxWebSearchCallsPerRun = sagMaxWebSearchCallsPerRun;
    }

    public int getSagMaxWebSearchCallsPerThread() {
        return managed().sagMaxWebSearchCallsPerThread;
    }

    public void setSagMaxWebSearchCallsPerThread(int sagMaxWebSearchCallsPerThread) {
        this.sagMaxWebSearchCallsPerThread = sagMaxWebSearchCallsPerThread;
    }

    private ChatAgentProperties managed() {
        if (systemConfigProvider == null) {
            return this;
        }
        ChatAgentProperties managed = systemConfigProvider.currentSnapshot().getChat();
        return managed == null || managed == this ? this : managed;
    }
}
