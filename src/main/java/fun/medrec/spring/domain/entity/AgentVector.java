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
public class AgentVector {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer agentId;
    private Integer vectorId;
}
