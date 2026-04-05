package com.yf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("x_spider_task")
public class SpiderTask implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String taskName;
    private String sourceSite;
    private String startUrl;
    private String listSelector;
    private String nameSelector;
    private String authorSelector;
    private String introSelector;
    private String chapterListSelector;
    private String chapterTitleSelector;
    private String chapterContentSelector;
    private Integer threadCount;
    private Integer maxPage;
    private Integer taskStatus;
    private Date lastRunTime;
    private String lastResult;
    private Integer isDeleted;
    private Integer createBy;
    private Date createTime;
    private Date updateTime;
}
