package org.aegiskb.ai.manage.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.aegiskb.ai.manage.config.DocumentManageProperties;
import org.aegiskb.ai.manage.mq.message.DocumentIndexBuildMessage;
import org.aegiskb.ai.manage.mq.message.DocumentParseRouteMessage;
import org.aegiskb.core.SpringUtil;
import org.aegiskb.enums.DocumentManageCode;
import org.aegiskb.exception.SuperAgentFrameException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class DocumentKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper;

    private final DocumentManageProperties properties;

    public void sendParseRoute(DocumentParseRouteMessage message) {

        // documentId 作为 key，使同一文档的消息稳定落到同一分区。
        send(SpringUtil.getPrefixDistinctionName() + "-" + properties.getKafka().getParseTopic(), String.valueOf(message.getDocumentId()), message);
    }

    public void sendIndexBuild(DocumentIndexBuildMessage message) {

        send(SpringUtil.getPrefixDistinctionName() + "-" + properties.getKafka().getIndexTopic(), String.valueOf(message.getDocumentId()), message);
    }

    private void send(String topic, String key, Object message) {
        try {

            String payload = objectMapper.writeValueAsString(message);

            // get() 会等待 broker 确认；调用方在发送成功或抛错后才继续。
            kafkaTemplate.send(topic, key, payload).get();
        } catch (Exception exception) {
            throw new SuperAgentFrameException(DocumentManageCode.KAFKA_SEND_FAILED.getCode(),
                "Kafka 消息发送失败: " + exception.getMessage(), exception);
        }
    }
}
