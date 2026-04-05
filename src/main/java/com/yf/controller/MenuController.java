package com.yf.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yf.common.Result;
import com.yf.entity.Menu;
import com.yf.service.MenuService;
import io.swagger.annotations.ApiOperation;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 
 * @since 2023-07-10
 */
@RestController
@RequestMapping("/menu")
public class MenuController {

    @Resource
    private MenuService menuService;

    @ApiOperation("查询所有菜单数据")
    @GetMapping("/getAllMenu")
    public Result<List<Menu>> getAllMenu(){
        List<Menu> menuList = menuService.getAllMenu();
        return Result.success(menuList);
    }

    @GetMapping("/list")
    public Result<Map<String,Object>> getUserList(@RequestParam(value = "title",required = false) String title,
                                                  @RequestParam(value = "pageNo") Long pageNo,
                                                  @RequestParam(value = "pageSize") Long pageSize){
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasLength(title),Menu::getTitle,title);
        wrapper.orderByDesc(Menu::getMenuId);

        Page<Menu> page = new Page<>(pageNo,pageSize);
        menuService.page(page, wrapper);

        Map<String,Object> data = new HashMap<>();
        data.put("total",page.getTotal());
        data.put("rows",page.getRecords());

        return Result.success(data);

    }

    @GetMapping("/getMenuById/{id}")
    public Result<Menu> getMenuById(@PathVariable("id") Integer id){
        Menu menu = menuService.getById(id);
        return Result.success(menu);
    }


    @PostMapping("/addRole")
    public Result<?> addRole(@RequestBody Menu menu){
        menuService.save(menu);
        return Result.success("新增菜单成功");
    }

    @PutMapping("/updateRole")
    public Result<?> updateRole(@RequestBody Menu menu){
        menuService.updateById(menu);
        return Result.success("修改菜单成功");
    }

    @DeleteMapping("/deleteMenuById/{id}")
    public Result<Menu> deleteMenuById(@PathVariable("id") Integer id){
        menuService.deleteMenuById(id);
        return Result.success("删除菜单成功");
    }


}

