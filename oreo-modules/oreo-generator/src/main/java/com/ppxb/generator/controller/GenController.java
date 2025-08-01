package com.ppxb.generator.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.ppxb.common.core.domain.R;
import com.ppxb.common.idempotent.annotation.RepeatSubmit;
import com.ppxb.common.log.annotation.Log;
import com.ppxb.common.log.enums.BusinessType;
import com.ppxb.common.mybatis.core.page.PageQuery;
import com.ppxb.common.mybatis.core.page.TableDataInfo;
import com.ppxb.common.mybatis.helper.DataBaseHelper;
import com.ppxb.common.web.core.BaseController;
import com.ppxb.generator.domain.GenTable;
import com.ppxb.generator.domain.GenTableColumn;
import com.ppxb.generator.service.IGenTableService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成 操作处理
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/tool/gen")
public class GenController extends BaseController {

    private final IGenTableService genTableService;

    /**
     * 查询代码生成列表
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping("/list")
    public TableDataInfo<GenTable> genList(GenTable genTable, PageQuery pageQuery) {
        return genTableService.selectPageGenTableList(genTable, pageQuery);
    }

    /**
     * 修改代码生成业务
     *
     * @param tableId 表ID
     */
    @RepeatSubmit()
    @SaCheckPermission("tool:gen:query")
    @GetMapping(value = "/{tableId}")
    public R<Map<String, Object>> getInfo(@PathVariable Long tableId) {
        GenTable table = genTableService.selectGenTableById(tableId);
        List<GenTable> tables = genTableService.selectGenTableAll();
        List<GenTableColumn> list = genTableService.selectGenTableColumnListByTableId(tableId);
        Map<String, Object> map = new HashMap<>(3);
        map.put("info", table);
        map.put("rows", list);
        map.put("tables", tables);
        return R.ok(map);
    }

    /**
     * 查询数据库列表
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping("/db/list")
    public TableDataInfo<GenTable> dataList(GenTable genTable, PageQuery pageQuery) {
        return genTableService.selectPageDbTableList(genTable, pageQuery);
    }

    /**
     * 查询数据表字段列表
     *
     * @param tableId 表ID
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping(value = "/column/{tableId}")
    public TableDataInfo<GenTableColumn> columnList(@PathVariable("tableId") Long tableId) {
        List<GenTableColumn> list = genTableService.selectGenTableColumnListByTableId(tableId);
        return TableDataInfo.build(list);
    }

    /**
     * 导入表结构（保存）
     *
     * @param tables 表名串
     */
    @SaCheckPermission("tool:gen:import")
    @Log(title = "代码生成", businessType = BusinessType.IMPORT)
    @RepeatSubmit()
    @PostMapping("/importTable")
    public R<Void> importTableSave(String tables, String dataName) {
        String[] tableNames = Convert.toStrArray(tables);
        // 查询表信息
        List<GenTable> tableList = genTableService.selectDbTableListByNames(tableNames, dataName);
        genTableService.importGenTable(tableList, dataName);
        return R.ok();
    }

    /**
     * 修改保存代码生成业务
     */
    @SaCheckPermission("tool:gen:edit")
    @Log(title = "代码生成", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> editSave(@Validated @RequestBody GenTable genTable) {
        genTableService.validateEdit(genTable);
        genTableService.updateGenTable(genTable);
        return R.ok();
    }

    /**
     * 删除代码生成
     *
     * @param tableIds 表ID串
     */
    @SaCheckPermission("tool:gen:remove")
    @Log(title = "代码生成", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tableIds}")
    public R<Void> remove(@PathVariable Long[] tableIds) {
        genTableService.deleteGenTableByIds(tableIds);
        return R.ok();
    }

    /**
     * 预览代码
     *
     * @param tableId 表ID
     */
    @SaCheckPermission("tool:gen:preview")
    @GetMapping("/preview/{tableId}")
    public R<Map<String, String>> preview(@PathVariable("tableId") Long tableId) throws IOException {
        Map<String, String> dataMap = genTableService.previewCode(tableId);
        return R.ok(dataMap);
    }

    /**
     * 生成代码（下载方式）
     *
     * @param tableId 表ID
     */
    @SaCheckPermission("tool:gen:code")
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @GetMapping("/download/{tableId}")
    public void download(HttpServletResponse response, @PathVariable("tableId") Long tableId) throws IOException {
        byte[] data = genTableService.downloadCode(tableId);
        genCode(response, data);
    }

    /**
     * 生成代码（自定义路径）
     *
     * @param tableId 表ID
     */
    @SaCheckPermission("tool:gen:code")
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @GetMapping("/genCode/{tableId}")
    public R<Void> genCode(@PathVariable("tableId") Long tableId) {
        genTableService.generatorCode(tableId);
        return R.ok();
    }

    /**
     * 同步数据库
     *
     * @param tableId 表ID
     */
    @SaCheckPermission("tool:gen:edit")
    @Log(title = "代码生成", businessType = BusinessType.UPDATE)
    @Lock4j
    @GetMapping("/synchDb/{tableId}")
    public R<Void> synchDb(@PathVariable("tableId") Long tableId) {
        genTableService.synchDb(tableId);
        return R.ok();
    }

    /**
     * 批量生成代码
     *
     * @param tableIdStr 表ID串
     */
    @SaCheckPermission("tool:gen:code")
    @Log(title = "代码生成", businessType = BusinessType.GENCODE)
    @GetMapping("/batchGenCode")
    public void batchGenCode(HttpServletResponse response, String tableIdStr) throws IOException {
        String[] tableIds = Convert.toStrArray(tableIdStr);
        byte[] data = genTableService.downloadCode(tableIds);
        genCode(response, data);
    }

    /**
     * 生成zip文件
     */
    private void genCode(HttpServletResponse response, byte[] data) throws IOException {
        response.reset();
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.setHeader("Content-Disposition", "attachment; filename=\"ruoyi.zip\"");
        response.addHeader("Content-Length", "" + data.length);
        response.setContentType("application/octet-stream; charset=UTF-8");
        IoUtil.write(response.getOutputStream(), false, data);
    }

    /**
     * 查询数据源名称列表
     */
    @SaCheckPermission("tool:gen:list")
    @GetMapping(value = "/getDataNames")
    public R<Object> getCurrentDataSourceNameList(){
        return R.ok(DataBaseHelper.getDataSourceNameList());
    }
}
