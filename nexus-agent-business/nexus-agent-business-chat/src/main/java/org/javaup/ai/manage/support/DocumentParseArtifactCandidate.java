package org.javaup.ai.manage.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParseArtifactCandidate {

    private String artifactType;

    private String fileName;

    private String contentType;

    private String contentBase64;

    private String contentHash;

    private String parserName;

    private String parserVersion;
}
