package fun.medrec.spring.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@TableName
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Knowledge {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String path;
    private Integer vectorId;
    private Integer createBy;
    private Date createTime;
}
