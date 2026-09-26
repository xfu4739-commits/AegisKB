package org.javaup.ai.manage.service;

import org.javaup.ai.manage.dto.DocumentIndexBuildDto;
import org.javaup.ai.manage.dto.DocumentChunkQueryDto;
import org.javaup.ai.manage.dto.DocumentChunkDetailQueryDto;
import org.javaup.ai.manage.dto.DocumentDetailQueryDto;
import org.javaup.ai.manage.dto.DocumentDeleteDto;
import org.javaup.ai.manage.dto.DocumentPageQueryDto;
import org.javaup.ai.manage.dto.DocumentIndexBuildProgressQueryDto;
import org.javaup.ai.manage.dto.DocumentParseArtifactContentQueryDto;
import org.javaup.ai.manage.dto.DocumentParseArtifactQueryDto;
import org.javaup.ai.manage.dto.DocumentParseRouteProgressQueryDto;
import org.javaup.ai.manage.dto.DocumentStrategyConfirmDto;
import org.javaup.ai.manage.dto.DocumentStrategyPlanQueryDto;
import org.javaup.ai.manage.dto.DocumentTaskLogQueryDto;
import org.javaup.ai.manage.dto.DocumentUploadDto;
import org.javaup.ai.manage.vo.DocumentIndexBuildVo;
import org.javaup.ai.manage.vo.DocumentChunkQueryVo;
import org.javaup.ai.manage.vo.DocumentChunkDetailVo;
import org.javaup.ai.manage.vo.DocumentDeleteVo;
import org.javaup.ai.manage.vo.DocumentListItemVo;
import org.javaup.ai.manage.vo.DocumentPageQueryVo;
import org.javaup.ai.manage.vo.DocumentIndexBuildProgressVo;
import org.javaup.ai.manage.vo.DocumentParseArtifactContentVo;
import org.javaup.ai.manage.vo.DocumentParseArtifactDownloadVo;
import org.javaup.ai.manage.vo.DocumentParseArtifactListVo;
import org.javaup.ai.manage.vo.DocumentParseRouteProgressVo;
import org.javaup.ai.manage.vo.DocumentStrategyConfirmVo;
import org.javaup.ai.manage.vo.DocumentStrategyPlanQueryVo;
import org.javaup.ai.manage.vo.DocumentTaskLogQueryVo;
import org.javaup.ai.manage.vo.DocumentUploadVo;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentManageService {

    /**
     * 上传文档原文件：校验文件后写入对象存储，创建文档主记录与解析路由任务，并投递异步解析消息。
     *
     * @param file 上传的文件
     * @param dto  文档名称、所属知识库及操作人等信息
     * @return 文档 ID、任务 ID 及当前解析/策略/索引状态
     */
    DocumentUploadVo upload(MultipartFile file, DocumentUploadDto dto);

    /**
     * 分页查询文档列表，支持按文档名称或原始文件名关键字模糊搜索。
     *
     * @param dto 分页参数与关键字
     * @return 分页结果及文档摘要列表
     */
    DocumentPageQueryVo queryDocumentPage(DocumentPageQueryDto dto);

    /**
     * 查询单个文档详情，包含最新任务状态。
     *
     * @param dto 文档 ID
     * @return 文档详情
     */
    DocumentListItemVo queryDocumentDetail(DocumentDetailQueryDto dto);

    /**
     * 删除文档及其关联数据，包括对象存储、向量/图/RAPTOR 索引、切块、任务与策略方案等。
     * 存在进行中的任务时不允许删除。
     *
     * @param dto 文档 ID
     * @return 已删除文档的 ID 与名称
     */
    DocumentDeleteVo deleteDocument(DocumentDeleteDto dto);

    /**
     * 查询文档当前策略方案及解析、策略、索引状态；方案未就绪时 {@code planReady} 为 false。
     *
     * @param dto 文档 ID
     * @return 文档状态与当前策略方案（如有）
     */
    DocumentStrategyPlanQueryVo queryStrategyPlan(DocumentStrategyPlanQueryDto dto);

    /**
     * 确认或调整切块策略：在解析成功后规范化父/子块流水线，必要时生成新版本方案并更新文档策略状态。
     *
     * @param dto 基础方案 ID、父/子块步骤及调整说明
     * @return 生效方案 ID、版本及确认后的流水线
     */
    DocumentStrategyConfirmVo confirmStrategy(DocumentStrategyConfirmDto dto);

    /**
     * 创建索引构建任务：要求文档已完成解析且策略已确认，并基于最近一次成功的解析任务冻结输入。
     *
     * @param dto 文档 ID、方案 ID 及操作人
     * @return 新建任务 ID 及当前索引构建状态
     */
    DocumentIndexBuildVo buildIndex(DocumentIndexBuildDto dto);

    /**
     * 查询索引构建进度；优先读取缓存，未命中时回查文档与构建任务及任务日志。
     *
     * @param dto 文档 ID，可选指定任务 ID
     * @return 当前阶段、进度与日志摘要
     */
    DocumentIndexBuildProgressVo queryIndexBuildProgress(DocumentIndexBuildProgressQueryDto dto);

    /**
     * 查询解析路由进度；优先读取缓存，未命中时回查文档与解析路由任务及任务日志。
     *
     * @param dto 文档 ID，可选指定任务 ID
     * @return 当前阶段、进度与日志摘要
     */
    DocumentParseRouteProgressVo queryParseRouteProgress(DocumentParseRouteProgressQueryDto dto);

    /**
     * 列出指定解析任务下的产物元数据（不下载正文），未指定任务时使用文档最近一次解析任务。
     *
     * @param dto 文档 ID，可选指定任务 ID
     * @return 产物列表及关联任务 ID
     */
    DocumentParseArtifactListVo queryParseArtifacts(DocumentParseArtifactQueryDto dto);

    /**
     * 预览可查看类型的解析产物内容（文本、JSON、Markdown 或图片），不支持预览的类型需走下载接口。
     *
     * @param dto 文档 ID、任务 ID 与产物 ID
     * @return 按类型组装后的预览内容
     */
    DocumentParseArtifactContentVo queryParseArtifactContent(DocumentParseArtifactContentQueryDto dto);

    /**
     * 下载解析产物原始字节流，保留推断后的文件名与 Content-Type。
     *
     * @param dto 文档 ID、任务 ID 与产物 ID
     * @return 原始文件内容与下载元信息
     */
    DocumentParseArtifactDownloadVo downloadParseArtifact(DocumentParseArtifactContentQueryDto dto);

    /**
     * 分页查询文档切块列表，未指定任务时使用当前生效方案对应的切块任务。
     *
     * @param dto 文档 ID、可选任务 ID 及分页参数
     * @return 切块摘要分页结果
     */
    DocumentChunkQueryVo queryDocumentChunks(DocumentChunkQueryDto dto);

    /**
     * 查询单个切块的完整详情，包含父块关联信息。
     *
     * @param dto 文档 ID、切块 ID，可选任务 ID
     * @return 切块详情
     */
    DocumentChunkDetailVo queryDocumentChunkDetail(DocumentChunkDetailQueryDto dto);

    /**
     * 分页查询指定任务的操作日志，按创建时间升序排列。
     *
     * @param dto 任务 ID 及分页参数
     * @return 任务日志分页结果
     */
    DocumentTaskLogQueryVo queryTaskLogs(DocumentTaskLogQueryDto dto);
}
