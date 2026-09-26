package org.javaup.ai.manage.service;

import org.javaup.ai.manage.model.SystemConfigSnapshot;

public interface SystemConfigProvider {

    SystemConfigSnapshot currentSnapshot();

    default void invalidate() {
    }
}
