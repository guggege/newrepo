package com.yf.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.dao.NovelChapterMapper;
import com.yf.entity.NovelChapter;
import com.yf.service.NovelChapterService;
import org.springframework.stereotype.Service;

@Service
public class NovelChapterServiceImpl extends ServiceImpl<NovelChapterMapper, NovelChapter> implements NovelChapterService {
}
