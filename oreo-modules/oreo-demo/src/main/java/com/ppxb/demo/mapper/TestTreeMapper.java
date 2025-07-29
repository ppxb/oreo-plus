package com.ppxb.demo.mapper;
import com.ppxb.common.mybatis.annotation.DataColumn;
import com.ppxb.common.mybatis.annotation.DataPermission;
import com.ppxb.common.mybatis.core.mapper.BaseMapperPlus;
import com.ppxb.demo.domain.TestTree;
import com.ppxb.demo.domain.vo.TestTreeVo;
/**
 * 测试树表Mapper接口
 *
 * @author Lion Li
 * @date 2021-07-26
 */
@DataPermission({
    @DataColumn(key = "deptName", value = "dept_id"),
    @DataColumn(key = "userName", value = "user_id")
})
public interface TestTreeMapper extends BaseMapperPlus<TestTree, TestTreeVo> {

}
