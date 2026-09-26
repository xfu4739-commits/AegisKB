package org.javaup.ai.manage.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.javaup.ai.chatagent.rag.model.RagRuntimeOptions;
import org.javaup.ai.manage.config.SystemConfigRegistry;
import org.javaup.ai.manage.model.SystemConfigSnapshot;
import org.javaup.enums.BaseCode;
import org.javaup.exception.SuperAgentFrameException;
import org.springframework.stereotype.Component;

@Component
public class SystemConfigSnapshotCodec {

    private static final int CURRENT_SCHEMA_VERSION = 2;
    private static final int LEGACY_SCHEMA_VERSION = 1;
    private final ObjectMapper objectMapper;
    private final SystemConfigRegistry registry;

    public SystemConfigSnapshotCodec(ObjectMapper objectMapper, SystemConfigRegistry registry) {
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    public String serialize(SystemConfigSnapshot snapshot) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION);
        ObjectNode values = root.putObject("values");
        registry.definitions().forEach(definition ->
            values.set(definition.key(), objectMapper.valueToTree(registry.read(snapshot, definition))));
        try {
            return objectMapper.writeValueAsString(root);
        }
        catch (JsonProcessingException exception) {
            throw new SuperAgentFrameException("系统配置快照序列化失败", exception);
        }
    }

    public String serialize(RagRuntimeOptions options) {
        SystemConfigSnapshot snapshot = SystemConfigSnapshot.defaults();
        snapshot.setRagRuntime(options == null ? RagRuntimeOptions.defaults() : options.deepCopy());
        return serialize(snapshot);
    }

    public SystemConfigSnapshot deserialize(String json, SystemConfigSnapshot fallback) {
        if (json == null || json.isBlank()) {
            throw systemConfigError("系统配置快照不能为空");
        }
        SystemConfigSnapshot snapshot = registry.copy(fallback);
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode schemaVersion = root == null ? null : root.get("schemaVersion");
            if (schemaVersion == null || !schemaVersion.canConvertToInt()
                || (schemaVersion.intValue() != LEGACY_SCHEMA_VERSION
                && schemaVersion.intValue() != CURRENT_SCHEMA_VERSION)) {
                throw systemConfigError("不支持的系统配置快照版本");
            }
            JsonNode values = root == null ? null : root.get("values");
            if (values == null || !values.isObject()) {
                throw systemConfigError("系统配置快照缺少 values。");
            }
            for (SystemConfigRegistry.ConfigDefinition definition : registry.definitions()) {
                JsonNode value = values.get(definition.key());
                if (value != null) {
                    registry.applyStoredValue(snapshot, definition, value);
                }
            }
            registry.validateSnapshot(snapshot);
            return snapshot;
        }
        catch (JsonProcessingException exception) {
            throw new SuperAgentFrameException("系统配置快照解析失败", exception);
        }
    }

    public RagRuntimeOptions deserialize(String json, RagRuntimeOptions fallback) {
        SystemConfigSnapshot snapshotFallback = SystemConfigSnapshot.defaults();
        snapshotFallback.setRagRuntime(fallback == null ? RagRuntimeOptions.defaults() : fallback.deepCopy());
        return deserialize(json, snapshotFallback).getRagRuntime();
    }

    private SuperAgentFrameException systemConfigError(String message) {
        return new SuperAgentFrameException(BaseCode.SYSTEM_ERROR.getCode(), message);
    }
}
