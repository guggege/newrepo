package com.yf.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yf.common.Result;
import com.yf.entity.User;
import com.yf.entity.UserRole;
import com.yf.service.UserRoleService;
import com.yf.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.crypto.password.PasswordEncoder;
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
@Api(tags = {"用户接口列表"})
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;


    @Resource
    private UserRoleService userRoleService;


    @Resource
    private PasswordEncoder passwordEncoder;

    @GetMapping("/all")
    public Result<List<User>> getAllUser(){
        List<User> list = userService.list();
        return Result.success(list,"查询成功");
    }

    @ApiOperation("用户登录")
    @PostMapping("/login")
    public Result<Map<String,Object>> login(@RequestBody User user){
        Map<String,Object> data =  userService.login(user);
        if(data != null){
            return Result.success(data);
        }
        return Result.fail(20002,"用户名或密码错误");
    }


    @GetMapping("/info")
    public Result<Map<String,Object>> getUserInfo(@RequestParam("token") String token){
        //根据token获取用户信息 redis
        Map<String,Object> data =  userService.getUserInfo(token);
        if(data != null){
            return Result.success(data);
        }
        return Result.fail(20003,"用户登录信息无效,请重新登录");
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody User user){
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        userService.addUser(user);

        userRoleService.save(new UserRole(null,user.getId(),3));
        return Result.success("用户注册成功");
    }

    @PostMapping("/logout")
    public Result<?> logout(@RequestHeader("X-token") String token){
        userService.logout(token);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<?> getUserListPage(@RequestParam(value = "username", required = false) String username,
                                     @RequestParam(value = "phone", required = false) String phone,
                                     @RequestParam("pageNo") Long pageNo,
                                     @RequestParam("pageSize") Long pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper();
        wrapper.orderByDesc(User::getId);
        if(username != ""){
            wrapper.eq(username != null, User::getUsername, username);
        }
        if(phone != ""){
            wrapper.eq(phone != null, User::getPhone, phone);
        }
        Page<User> page = new Page<>(pageNo, pageSize);
        userService.page(page, wrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("total", page.getTotal());
        data.put("rows", page.getRecords());

        return Result.success(data);
    }

    @PostMapping("/addUser")
    public Result<?> addUser(@RequestBody User user){
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userService.addUser(user);
        return Result.success("新增用户成功");
    }

    @PutMapping("/updateMyUser")
    public Result<?> updateMyUser(@RequestBody User user){
        if(user.getPassword() != null){
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userService.updateById(user);
        return Result.success("用户信息修改完毕!");
    }

    @PutMapping("/updateUser")
    public Result<?> updateUser(@RequestBody User user){
        user.setPassword(null);
        userService.updateUser(user);
        return Result.success("修改用户成功");
    }

    @GetMapping("/getUserById/{id}")
    public Result<User> getUserById(@PathVariable("id") Integer id){
        User user = userService.getUserById(id);
        return Result.success(user);
    }

    @DeleteMapping("/deleteUserById/{id}")
    public Result<User> deleteUserById(@PathVariable("id") Integer id){
        userService.deleteUserById(id);
        return Result.success("删除用户成功");
    }



    //@GetMapping("/getorder")
    //public Result<List<SysOrder>> getorder(){
    //    List<SysOrder> roleList = service.getorder();
    //    return Result.success(roleList);
    //}

}

