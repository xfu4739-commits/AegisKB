package org.aegiskb.lockinfo.impl;

import org.aegiskb.lockinfo.AbstractLockInfoHandle;

public class ServiceLockInfoHandle extends AbstractLockInfoHandle {

    private static final String LOCK_PREFIX_NAME = "SERVICE_LOCK";

    @Override
    protected String getLockPrefixName() {
        return LOCK_PREFIX_NAME;
    }
}
