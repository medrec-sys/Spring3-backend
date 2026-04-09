package fun.medrec.spring.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Agent {
    @TableId(type = IdType.AUTO)
    private Integer id;
    @NotBlank(message = "名称不能为空")
    private String name;
    @NotBlank(message = "描述不能为空")
    private String description;
    @NotBlank(message = "提示词不能为空")
    private String prompt;
    @NotNull(message = "温度不能为空")
    @DecimalMin(value = "0", message = "温度不能小于0")
    @DecimalMax(value = "1", message = "温度不能大于1")
    private Double temperature;
    @NotNull(message = "最大历史消息数不能为空")
    @Min(value = 0, message = "最小历史消息数不能小于0")
    @Max(value = 100, message = "最大历史消息数不能大于100")
    private Integer maxMessage;
    @NotNull(message = "检索返回的文档数不能为空")
    @Min(value = 0, message = "检索返回的文档数不能小于0")
    @Max(value = 100, message = "检索返回的文档数不能大于100")
    private Integer topK;
    @NotNull(message = "相似度不能为空")
    @DecimalMin(value = "0", message = "相似度不能小于0")
    @DecimalMax(value = "1", message = "相似度不能大于1")
    private Double similarity;
    private Integer createBy;
    private Date createTime;
}