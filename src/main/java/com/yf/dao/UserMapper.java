package com.yf.dao;

import com.yf.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 
 * @since 2023-07-10
 */
public interface UserMapper extends BaseMapper<User> {

    public List<String> getRoleNamesByUserId(Integer userId);


    //@Select("SELECT DATE_FORMAT(createdate, '%Y-%m-%d') AS orderDate, SUM(totalprice) AS totalAmount " +
    //        "FROM sys_order " +
    //        "GROUP BY orderDate")
    //List<SysOrder> getorder();

}
