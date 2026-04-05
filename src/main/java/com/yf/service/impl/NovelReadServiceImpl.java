package com.yf.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yf.dao.NovelChapterMapper;
import com.yf.dao.NovelContentMapper;
import com.yf.dao.ReadHistoryMapper;
import com.yf.entity.Novel;
import com.yf.entity.NovelChapter;
import com.yf.entity.NovelContent;
import com.yf.entity.ReadHistory;
import com.yf.service.NovelReadService;
import com.yf.service.NovelService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class NovelReadServiceImpl implements NovelReadService {

    @Resource
    private NovelChapterMapper novelChapterMapper;
    @Resource
    private NovelContentMapper novelContentMapper;
    @Resource
    private ReadHistoryMapper readHistoryMapper;
    @Resource
    private NovelService novelService;

    @Override
    public Map<String, Object> readChapter(Long chapterId, Integer userId) {
        NovelChapter chapter = novelChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new RuntimeException("章节不存在");
        }
        NovelContent content = getContentByChapterId(chapterId);
        NovelChapter prev = getPrevChapter(chapter.getNovelId(), chapter.getChapterNo());
        NovelChapter next = getNextChapter(chapter.getNovelId(), chapter.getChapterNo());

        if (userId != null) {
            LambdaQueryWrapper<ReadHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReadHistory::getUserId, userId).eq(ReadHistory::getNovelId, chapter.getNovelId());
            ReadHistory history = readHistoryMapper.selectOne(wrapper);
            if (history == null) {
                history = new ReadHistory();
                history.setUserId(userId);
                history.setNovelId(chapter.getNovelId());
                history.setChapterId(chapterId);
                history.setReadProgress(0);
                history.setLastReadTime(new Date());
                readHistoryMapper.insert(history);
            } else {
                history.setChapterId(chapterId);
                history.setLastReadTime(new Date());
                readHistoryMapper.updateById(history);
            }
        }

        Novel novel = novelService.getById(chapter.getNovelId());
        Map<String, Object> data = new HashMap<>();
        data.put("novel", novel);
        data.put("chapter", chapter);
        data.put("content", content);
        data.put("prevChapter", prev);
        data.put("nextChapter", next);
        return data;
    }

    @Override
    public NovelChapter getNextChapter(Long novelId, Integer chapterNo) {
        LambdaQueryWrapper<NovelChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NovelChapter::getNovelId, novelId)
                .gt(NovelChapter::getChapterNo, chapterNo)
                .orderByAsc(NovelChapter::getChapterNo)
                .last("limit 1");
        return novelChapterMapper.selectOne(wrapper);
    }

    @Override
    public NovelChapter getPrevChapter(Long novelId, Integer chapterNo) {
        LambdaQueryWrapper<NovelChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NovelChapter::getNovelId, novelId)
                .lt(NovelChapter::getChapterNo, chapterNo)
                .orderByDesc(NovelChapter::getChapterNo)
                .last("limit 1");
        return novelChapterMapper.selectOne(wrapper);
    }

    @Override
    public NovelContent getContentByChapterId(Long chapterId) {
        LambdaQueryWrapper<NovelContent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NovelContent::getChapterId, chapterId);
        return novelContentMapper.selectOne(wrapper);
    }
}
