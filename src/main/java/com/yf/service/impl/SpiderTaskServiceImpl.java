package com.yf.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yf.dao.SpiderTaskMapper;
import com.yf.entity.SpiderTask;
import com.yf.service.SpiderTaskService;
import org.springframework.stereotype.Service;

@Service
public class SpiderTaskServiceImpl extends ServiceImpl<SpiderTaskMapper, SpiderTask> implements SpiderTaskService {
}
