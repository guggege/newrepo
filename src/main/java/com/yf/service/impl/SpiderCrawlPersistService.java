package com.yf.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yf.dao.NovelChapterMapper;
import com.yf.dao.NovelContentMapper;
import com.yf.dao.NovelMapper;
import com.yf.entity.Novel;
import com.yf.entity.NovelChapter;
import com.yf.entity.NovelContent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SpiderCrawlPersistService {

    @Resource
    private NovelMapper novelMapper;
    @Resource
    private NovelChapterMapper novelChapterMapper;
    @Resource
    private NovelContentMapper novelContentMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveOrMergeNovel(Novel novel) {
        LambdaQueryWrapper<Novel> wrapper = new LambdaQueryWrapper<>();
        if (novel.getSourceBookId() != null && !novel.getSourceBookId().trim().isEmpty()) {
            wrapper.eq(Novel::getSourceSite, novel.getSourceSite())
                    .eq(Novel::getSourceBookId, novel.getSourceBookId());
        } else {
            wrapper.eq(Novel::getSourceSite, novel.getSourceSite())
                    .eq(Novel::getSourceUrl, novel.getSourceUrl());
        }
        wrapper.orderByDesc(Novel::getId);
        List<Novel> oldList = novelMapper.selectList(wrapper);
        Novel old = oldList.isEmpty() ? null : oldList.get(0);
        if (old == null) {
            novelMapper.insert(novel);
        } else {
            novel.setId(old.getId());
            novelMapper.updateById(novel);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveChapterAndContent(Long novelId, NovelChapter chapter, String contentText) {
        chapter.setNovelId(novelId);
        LambdaQueryWrapper<NovelChapter> cw = new LambdaQueryWrapper<>();
        cw.eq(NovelChapter::getNovelId, chapter.getNovelId())
                .eq(NovelChapter::getChapterNo, chapter.getChapterNo());
        NovelChapter oldCh = novelChapterMapper.selectOne(cw);
        if (oldCh == null) {
            novelChapterMapper.insert(chapter);
        } else {
            chapter.setId(oldCh.getId());
            novelChapterMapper.updateById(chapter);
        }
        if (chapter.getId() == null) {
            NovelChapter loaded = novelChapterMapper.selectOne(
                    new LambdaQueryWrapper<NovelChapter>()
                            .eq(NovelChapter::getNovelId, novelId)
                            .eq(NovelChapter::getChapterNo, chapter.getChapterNo())
                            .last("limit 1"));
            if (loaded != null) {
                chapter.setId(loaded.getId());
            }
        }
        Long chapterId = chapter.getId();
        if (chapterId == null) {
            throw new IllegalStateException("章节主键未回填，无法写入正文");
        }
        LambdaQueryWrapper<NovelContent> ctw = new LambdaQueryWrapper<>();
        ctw.eq(NovelContent::getChapterId, chapterId);
        NovelContent oldCt = novelContentMapper.selectOne(ctw);
        if (oldCt == null) {
            NovelContent nc = new NovelContent();
            nc.setNovelId(novelId);
            nc.setChapterId(chapterId);
            nc.setContentText(contentText);
            nc.setIsDeleted(0);
            novelContentMapper.insert(nc);
        } else {
            oldCt.setContentText(contentText);
            novelContentMapper.updateById(oldCt);
        }
        LambdaUpdateWrapper<Novel> uw = new LambdaUpdateWrapper<>();
        uw.eq(Novel::getId, novelId)
                .set(Novel::getLatestChapter, chapter.getChapterTitle())
                .set(Novel::getLatestChapterNo, chapter.getChapterNo());
        novelMapper.update(null, uw);
    }

    public Set<Integer> chapterNosWithFullContent(Long novelId) {
        if (novelId == null) {
            return Collections.emptySet();
        }
        LambdaQueryWrapper<NovelChapter> w = new LambdaQueryWrapper<>();
        w.eq(NovelChapter::getNovelId, novelId);
        List<NovelChapter> list = novelChapterMapper.selectList(w);
        Set<Integer> done = new HashSet<>();
        for (NovelChapter ch : list) {
            if (ch.getId() == null || ch.getChapterNo() == null) {
                continue;
            }
            NovelContent ct = novelContentMapper.selectOne(
                    new LambdaQueryWrapper<NovelContent>().eq(NovelContent::getChapterId, ch.getId()));
            if (ct != null && ct.getContentText() != null && !ct.getContentText().trim().isEmpty()) {
                done.add(ch.getChapterNo());
            }
        }
        return done;
    }
}
