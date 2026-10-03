package org.aegiskb.lockinfo.impl;

import org.aegiskb.lockinfo.AbstractLockInfoHandle;

public class RepeatExecuteLimitLockInfoHandle extends AbstractLockInfoHandle {

    public static final String PREFIX_NAME = "REPEAT_EXECUTE_LIMIT";

    @Override
    protected String getLockPrefixName() {
        return PREFIX_NAME;
    }
}
