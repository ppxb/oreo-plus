package com.ppxb.system.mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ppxb.common.mybatis.core.mapper.BaseMapperPlus;
import com.ppxb.system.domain.SysRoleMenu;
import java.util.List;

/**
 * 角色与菜单关联表 数据层
 *
 * @author Lion Li
 */
public interface SysRoleMenuMapper extends BaseMapperPlus<SysRoleMenu, SysRoleMenu> {

    /**
     * 根据菜单ID串删除关联关系
     *
     * @param menuIds 菜单ID串
     * @return 结果
     */
    default int deleteByMenuIds(List<Long> menuIds) {
        return this.delete(new LambdaUpdateWrapper<SysRoleMenu>().in(SysRoleMenu::getMenuId, menuIds));
    }

}
