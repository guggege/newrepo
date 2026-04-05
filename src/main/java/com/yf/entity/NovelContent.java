package com.yf.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("x_novel_content")
public class NovelContent implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private Long chapterId;
    @TableField(insertStrategy = FieldStrategy.NOT_NULL, updateStrategy = FieldStrategy.NOT_NULL)
    private String contentText;
    private String contentHash;
    private Integer isDeleted;
    private Date createTime;
    private Date updateTime;
}
