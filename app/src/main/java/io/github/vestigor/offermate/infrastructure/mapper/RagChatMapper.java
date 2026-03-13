package io.github.vestigor.offermate.infrastructure.mapper;

import io.github.vestigor.offermate.modules.knowledgebase.model.dto.KnowledgeBaseListItemDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.RagChatDTO.MessageDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.RagChatDTO.SessionDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.RagChatDTO.SessionDetailDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.dto.RagChatDTO.SessionListItemDTO;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.KnowledgeBaseEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.RagChatMessageEntity;
import io.github.vestigor.offermate.modules.knowledgebase.model.entity.RagChatSessionEntity;

import org.mapstruct.*;

import java.util.Collection;
import java.util.List;

/**
 * RAG聊天相关实体到DTO的映射器
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = KnowledgeBaseMapper.class
)
public interface RagChatMapper {

    /**
     * 将会话实体转换为会话DTO
     */
    @Mapping(target = "knowledgeBaseIds", source = "session", qualifiedByName = "extractKnowledgeBaseIds")
    SessionDTO toSessionDTO(RagChatSessionEntity session);

    /**
     * 将消息实体转换为消息DTO
     */
    @Mapping(target = "type", source = "message", qualifiedByName = "getTypeString")
    MessageDTO toMessageDTO(RagChatMessageEntity message);

    /**
     * 将消息实体列表转换为消息DTO列表
     */
    List<MessageDTO> toMessageDTOList(List<RagChatMessageEntity> messages);

    /**
     * 将知识库实体集合转换为知识库名称列表
     */
    @Named("extractKnowledgeBaseNames")
    default List<String> extractKnowledgeBaseNames(Collection<KnowledgeBaseEntity> knowledgeBases) {
        return knowledgeBases.stream()
                .map(KnowledgeBaseEntity::getName)
                .toList();
    }

    /**
     * 从会话实体中提取知识库ID列表
     */
    @Named("extractKnowledgeBaseIds")
    default List<Long> extractKnowledgeBaseIds(RagChatSessionEntity session) {
        return session.getKnowledgeBaseIds();
    }

    /**
     * 获取消息类型字符串
     */
    @Named("getTypeString")
    default String getTypeString(RagChatMessageEntity message) {
        return message.getTypeString();
    }

    /**
     * 将会话实体转换为会话列表项DTO
     */
    @Mapping(target = "knowledgeBaseNames", source = "session.knowledgeBases", qualifiedByName = "extractKnowledgeBaseNames")
    @Mapping(target = "isPinned", source = "session.isPinned")
    SessionListItemDTO toSessionListItemDTO(RagChatSessionEntity session);

    /**
     * 将会话实体和消息列表转换为会话详情DTO
     */
    default SessionDetailDTO toSessionDetailDTO(
            RagChatSessionEntity session,
            List<RagChatMessageEntity> messages,
            List<KnowledgeBaseListItemDTO> knowledgeBases) {
        List<MessageDTO> messageDTOs = toMessageDTOList(messages);

        return new SessionDetailDTO(
                session.getId(),
                session.getTitle(),
                knowledgeBases,
                messageDTOs,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}