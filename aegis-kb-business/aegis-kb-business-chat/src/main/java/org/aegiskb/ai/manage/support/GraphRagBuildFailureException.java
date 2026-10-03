package org.aegiskb.ai.manage.support;

import org.aegiskb.ai.manage.model.graph.GraphRagBuildResult;

public final class GraphRagBuildFailureException extends IllegalStateException {

    private final GraphRagBuildResult result;

    public GraphRagBuildFailureException(String message, GraphRagBuildResult result) {
        super(message);
        this.result = result;
    }

    public GraphRagBuildFailureException(String message, Throwable cause, GraphRagBuildResult result) {
        super(message, cause);
        this.result = result;
    }

    public GraphRagBuildResult getResult() {
        return result;
    }
}
