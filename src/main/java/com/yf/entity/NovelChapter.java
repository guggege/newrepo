package com.yf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("x_novel_chapter")
public class NovelChapter implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private Integer chapterNo;
    private String chapterTitle;
    private String chapterUrl;
    private Integer wordCount;
    private Integer isVip;
    private Integer isEnable;
    private Date createTime;
    private Date updateTime;
}
