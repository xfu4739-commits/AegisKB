package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentDocumentBlock;
import org.aegiskb.ai.manage.data.SuperAgentDocumentParseArtifact;
import org.aegiskb.ai.manage.support.DocumentTableCandidate;

import java.util.List;

public interface DocumentParseArtifactService {

    void replaceTaskArtifacts(Long documentId,
                              Long taskId,
                              List<SuperAgentDocumentParseArtifact> artifactList,
                              List<SuperAgentDocumentBlock> blockList,
                              List<DocumentTableCandidate> tableCandidates);

    void saveArtifacts(Long documentId, Long taskId, List<SuperAgentDocumentParseArtifact> artifactList);

    void saveBlocks(Long documentId, Long taskId, List<SuperAgentDocumentBlock> blockList);

    List<SuperAgentDocumentParseArtifact> listArtifacts(Long documentId, Long taskId);

    List<SuperAgentDocumentBlock> listBlocks(Long documentId, Long taskId);

    List<String> listObjectNamesByDocumentId(Long documentId);

    void deleteByTask(Long documentId, Long taskId);

    void deleteByDocumentId(Long documentId);
}
