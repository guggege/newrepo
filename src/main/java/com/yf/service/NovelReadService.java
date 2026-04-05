package com.yf.service;

import com.yf.entity.NovelChapter;
import com.yf.entity.NovelContent;

import java.util.Map;

public interface NovelReadService {
    Map<String, Object> readChapter(Long chapterId, Integer userId);

    NovelChapter getNextChapter(Long novelId, Integer chapterNo);

    NovelChapter getPrevChapter(Long novelId, Integer chapterNo);

    NovelContent getContentByChapterId(Long chapterId);
}
