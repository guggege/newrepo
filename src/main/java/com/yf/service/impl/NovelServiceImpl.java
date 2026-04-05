package com.yf.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.dao.NovelMapper;
import com.yf.entity.Novel;
import com.yf.service.NovelService;
import org.springframework.stereotype.Service;

@Service
public class NovelServiceImpl extends ServiceImpl<NovelMapper, Novel> implements NovelService {
}
