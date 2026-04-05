package com.yf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("x_novel")
public class Novel implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String sourceSite;
    private String sourceUrl;
    private String sourceBookId;
    private String name;
    private String author;
    private String category;
    private String status;
    private String coverUrl;
    private String intro;
    private String latestChapter;
    private Integer latestChapterNo;
    private Integer wordCount;
    private Integer isEnable;
    private Integer isDeleted;
    private Date createTime;
    private Date updateTime;
}
