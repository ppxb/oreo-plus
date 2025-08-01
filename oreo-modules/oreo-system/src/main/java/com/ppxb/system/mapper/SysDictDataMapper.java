package com.ppxb.system.mapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ppxb.common.mybatis.core.mapper.BaseMapperPlus;
import com.ppxb.system.domain.SysDictData;
import com.ppxb.system.domain.vo.SysDictDataVo;
import java.util.List;

/**
 * 字典表 数据层
 *
 * @author Lion Li
 */
public interface SysDictDataMapper extends BaseMapperPlus<SysDictData, SysDictDataVo> {

    /**
     * 根据字典类型查询字典数据列表
     *
     * @param dictType 字典类型
     * @return 符合条件的字典数据列表
     */
    default List<SysDictDataVo> selectDictDataByType(String dictType) {
        return selectVoList(
            new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .orderByAsc(SysDictData::getDictSort));
    }
}
