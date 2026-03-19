package fun.medrec.spring.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Agent {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String description;
    private String prompt;
    private Double temperature;
    private int maxMessage;
    private int topK;
    private Double similarity;
    private Integer createBy;
}