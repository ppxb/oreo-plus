package com.ppxb.generator.mapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.ppxb.common.mybatis.core.mapper.BaseMapperPlus;
import com.ppxb.generator.domain.GenTableColumn;
/**
 * 业务字段 数据层
 *
 * @author Lion Li
 */
@InterceptorIgnore(dataPermission = "true", tenantLine = "true")
public interface GenTableColumnMapper extends BaseMapperPlus<GenTableColumn, GenTableColumn> {

}
