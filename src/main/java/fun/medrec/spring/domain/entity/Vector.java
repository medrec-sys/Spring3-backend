package fun.medrec.spring.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vector {
    @TableId(type = IdType.AUTO)
    private Integer id;
    @NotBlank(message = "名称不能为空")
    private String name;
    @NotBlank(message = "描述不能为空")
    private String description;
    @NotNull(message = "维度不能为空")
    @Min(value = 512, message = "维度在512-2048之间")
    @Max(value = 2048, message = "维度在512-2048之间")
    private Integer dim;
    @JsonIgnore
    private String indexName;
    @JsonIgnore
    private String prefix;
    private Integer createBy;
    private Date createTime;
}
