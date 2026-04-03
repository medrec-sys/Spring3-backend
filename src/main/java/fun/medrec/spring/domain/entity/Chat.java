package fun.medrec.spring.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Chat {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 聊天内容
     */
    private String content;

    /**
     * 类型（user/assistant/system）
     */
    private String type;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
}
