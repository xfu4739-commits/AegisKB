package org.aegiskb.core;

public interface ConsumerTask {

    void execute(String content);

    String topic();
}
