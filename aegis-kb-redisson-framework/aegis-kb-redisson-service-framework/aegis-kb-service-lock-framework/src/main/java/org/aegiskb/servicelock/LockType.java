package org.aegiskb.servicelock;

public enum LockType {

    Reentrant,

    Fair,

    Read,

    Write;

    LockType() {
    }

}
