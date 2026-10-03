package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.SystemConfigSnapshot;

public interface SystemConfigProvider {

    SystemConfigSnapshot currentSnapshot();

    default void invalidate() {
    }
}
