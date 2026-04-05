package com.yf.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yf.common.Result;
import com.yf.entity.SpiderTask;
import com.yf.service.SpiderService;
import com.yf.service.SpiderTaskService;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/spider/task")
public class SpiderTaskController {

    @Resource
    private SpiderTaskService spiderTaskService;
    @Resource
    private SpiderService spiderService;

    @ApiOperation("爬虫任务分页")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(value = "taskName", required = false) String taskName,
                                            @RequestParam("pageNo") Long pageNo,
                                            @RequestParam("pageSize") Long pageSize) {
        LambdaQueryWrapper<SpiderTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(taskName != null && !taskName.trim().isEmpty(), SpiderTask::getTaskName, taskName);
        wrapper.orderByDesc(SpiderTask::getId);
        Page<SpiderTask> page = new Page<>(pageNo, pageSize);
        spiderTaskService.page(page, wrapper);
        Map<String, Object> data = new HashMap<>();
        data.put("total", page.getTotal());
        data.put("rows", page.getRecords());
        return Result.success(data);
    }

    @GetMapping("/getById/{id}")
    public Result<SpiderTask> getById(@PathVariable("id") Long id) {
        return Result.success(spiderTaskService.getById(id));
    }

    @PostMapping("/add")
    public Result<?> add(@RequestBody SpiderTask task) {
        if (task.getTaskStatus() == null) {
            task.setTaskStatus(0);
        }
        if (task.getSourceSite() == null || task.getSourceSite().trim().isEmpty()) {
            task.setSourceSite("dingdiann");
        }
        spiderTaskService.save(task);
        return Result.success("新增任务成功");
    }

    @PutMapping("/update")
    public Result<?> update(@RequestBody SpiderTask task) {
        spiderTaskService.updateById(task);
        return Result.success("修改任务成功");
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable("id") Long id) {
        spiderTaskService.removeById(id);
        return Result.success("删除任务成功");
    }

    @PostMapping("/run/{id}")
    public Result<?> runTask(@PathVariable("id") Long id) {
        spiderService.runTask(id);
        SpiderTask after = spiderTaskService.getById(id);
        return Result.success(after != null && after.getLastResult() != null ? after.getLastResult() : "已执行");
    }
}
