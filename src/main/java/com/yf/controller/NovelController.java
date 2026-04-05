package com.yf.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yf.common.Result;
import com.yf.dao.NovelContentMapper;
import com.yf.entity.Novel;
import com.yf.entity.NovelChapter;
import com.yf.entity.NovelContent;
import com.yf.entity.User;
import com.yf.service.NovelChapterService;
import com.yf.service.NovelReadService;
import com.yf.service.NovelService;
import com.yf.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/novel")
public class NovelController {

    @Resource
    private NovelService novelService;
    @Resource
    private NovelChapterService novelChapterService;
    @Resource
    private NovelReadService novelReadService;
    @Resource
    private UserService userService;
    @Resource
    private NovelContentMapper novelContentMapper;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(value = "name", required = false) String name,
                                            @RequestParam(value = "author", required = false) String author,
                                            @RequestParam("pageNo") Long pageNo,
                                            @RequestParam("pageSize") Long pageSize) {
        LambdaQueryWrapper<Novel> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(name != null && !name.trim().isEmpty(), Novel::getName, name)
                .like(author != null && !author.trim().isEmpty(), Novel::getAuthor, author)
                .orderByDesc(Novel::getId);
        Page<Novel> page = new Page<>(pageNo, pageSize);
        novelService.page(page, wrapper);
        Map<String, Object> data = new HashMap<>();
        data.put("total", page.getTotal());
        data.put("rows", page.getRecords());
        return Result.success(data);
    }

    @GetMapping("/getById/{id}")
    public Result<Novel> getById(@PathVariable("id") Long id) {
        return Result.success(novelService.getById(id));
    }

    @PutMapping("/update")
    public Result<?> update(@RequestBody Novel novel) {
        novelService.updateById(novel);
        return Result.success("修改成功");
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable("id") Long id) {
        novelService.removeById(id);
        return Result.success("删除成功");
    }

    @GetMapping("/chapters/{novelId}")
    public Result<List<NovelChapter>> chapters(@PathVariable("novelId") Long novelId) {
        LambdaQueryWrapper<NovelChapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NovelChapter::getNovelId, novelId).orderByAsc(NovelChapter::getChapterNo);
        return Result.success(novelChapterService.list(wrapper));
    }

    @PutMapping("/chapter/update")
    public Result<?> updateChapter(@RequestBody NovelChapter chapter) {
        if (chapter == null || chapter.getId() == null) {
            return Result.fail("章节ID不能为空");
        }
        novelChapterService.updateById(chapter);
        return Result.success("章节已保存");
    }

    @PutMapping("/content/update")
    public Result<?> updateContent(@RequestBody Map<String, Object> body) {
        Object cid = body.get("chapterId");
        if (cid == null) {
            return Result.fail("chapterId不能为空");
        }
        Long chapterId = Long.valueOf(cid.toString());
        String contentText = body.get("contentText") != null ? Objects.toString(body.get("contentText"), "") : "";
        NovelChapter chapter = novelChapterService.getById(chapterId);
        if (chapter == null) {
            return Result.fail("章节不存在");
        }
        LambdaQueryWrapper<NovelContent> w = new LambdaQueryWrapper<>();
        w.eq(NovelContent::getChapterId, chapterId);
        NovelContent nc = novelContentMapper.selectOne(w);
        if (nc == null) {
            nc = new NovelContent();
            nc.setNovelId(chapter.getNovelId());
            nc.setChapterId(chapterId);
            nc.setContentText(contentText);
            nc.setIsDeleted(0);
            novelContentMapper.insert(nc);
        } else {
            nc.setContentText(contentText);
            novelContentMapper.updateById(nc);
        }
        return Result.success("正文已保存");
    }

    @GetMapping("/read/{chapterId}")
    public Result<Map<String, Object>> read(@PathVariable("chapterId") Long chapterId,
                                            @RequestHeader(value = "X-Token", required = false) String token) {
        Integer userId = null;
        if (token != null && !token.trim().isEmpty()) {
            try {
                Map<String, Object> info = userService.getUserInfo(token);
                if (info != null) {
                    Object userObj = info.get("userList");
                    if (userObj instanceof User) {
                        userId = ((User) userObj).getId();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return Result.success(novelReadService.readChapter(chapterId, userId));
    }
}
