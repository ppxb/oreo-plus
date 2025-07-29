package com.ppxb.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ppxb.common.core.domain.dto.StartProcessReturnDTO;
import com.ppxb.common.core.domain.dto.UserDTO;
import com.ppxb.common.core.enums.BusinessStatusEnum;
import com.ppxb.common.core.exception.ServiceException;
import com.ppxb.common.core.service.UserService;
import com.ppxb.common.core.utils.StreamUtils;
import com.ppxb.common.core.utils.StringUtils;
import com.ppxb.common.core.utils.ValidatorUtils;
import com.ppxb.common.core.validate.AddGroup;
import com.ppxb.common.core.validate.EditGroup;
import com.ppxb.common.json.utils.JsonUtils;
import com.ppxb.common.mybatis.core.page.PageQuery;
import com.ppxb.common.mybatis.core.page.TableDataInfo;
import com.ppxb.common.satoken.utils.LoginHelper;
import com.ppxb.workflow.common.ConditionalOnEnable;
import com.ppxb.workflow.common.constant.FlowConstant;
import com.ppxb.workflow.common.enums.TaskAssigneeType;
import com.ppxb.workflow.common.enums.TaskStatusEnum;
import com.ppxb.workflow.domain.bo.*;
import com.ppxb.workflow.domain.vo.FlowHisTaskVo;
import com.ppxb.workflow.domain.vo.FlowTaskVo;
import com.ppxb.workflow.mapper.FlwCategoryMapper;
import com.ppxb.workflow.mapper.FlwTaskMapper;
import com.ppxb.workflow.service.IFlwCommonService;
import com.ppxb.workflow.service.IFlwNodeExtService;
import com.ppxb.workflow.service.IFlwTaskAssigneeService;
import com.ppxb.workflow.service.IFlwTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.entity.*;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.service.*;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.orm.entity.*;
import org.dromara.warm.flow.orm.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.ppxb.workflow.common.constant.FlowConstant.*;

/**
 * 任务服务实现
 *
 * @author ppxb
 * @since 1.0.0
 */
@Slf4j
@Service
@ConditionalOnEnable
@RequiredArgsConstructor
public class FlwTaskServiceImpl implements IFlwTaskService {

    private final TaskService taskService;
    private final InsService insService;
    private final DefService defService;
    private final HisTaskService hisTaskService;
    private final NodeService nodeService;
    private final FlowInstanceMapper flowInstanceMapper;
    private final FlowTaskMapper flowTaskMapper;
    private final FlowHisTaskMapper flowHisTaskMapper;
    private final IdentifierGenerator identifierGenerator;
    private final UserService userService;
    private final FlwTaskMapper flwTaskMapper;
    private final FlwCategoryMapper flwCategoryMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final IFlwTaskAssigneeService flwTaskAssigneeService;
    private final IFlwCommonService flwCommonService;
    private final IFlwNodeExtService flwNodeExtService;
    private final FlowDefinitionMapper flowDefinitionMapper;

    /**
     * 启动任务
     *
     * @param startProcessBo 启动流程参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StartProcessReturnDTO startWorkFlow(StartProcessBo startProcessBo) {
        String businessId = startProcessBo.getBusinessId();
        if (StringUtils.isBlank(businessId)) {
            throw new ServiceException("启动工作流时必须包含业务ID");
        }
        // 启动流程实例（提交申请）
        Map<String, Object> variables = startProcessBo.getVariables();
        // 流程发起人
        variables.put(INITIATOR, LoginHelper.getUserIdStr());
        // 发起人部门id
        variables.put(INITIATOR_DEPT_ID, LoginHelper.getDeptId());
        // 业务id
        variables.put(BUSINESS_ID, businessId);
        FlowInstance flowInstance = flowInstanceMapper.selectOne(new LambdaQueryWrapper<>(FlowInstance.class)
                .eq(FlowInstance::getBusinessId, businessId));
        if (ObjectUtil.isNotNull(flowInstance)) {
            BusinessStatusEnum.checkStartStatus(flowInstance.getFlowStatus());
            List<Task> taskList = taskService.list(new FlowTask().setInstanceId(flowInstance.getId()));
            taskService.mergeVariable(flowInstance, variables);
            insService.updateById(flowInstance);
            StartProcessReturnDTO dto = new StartProcessReturnDTO();
            dto.setProcessInstanceId(taskList.get(0).getInstanceId());
            dto.setTaskId(taskList.get(0).getId());
            return dto;
        }
        // 将流程定义内的扩展参数设置到变量中
        Definition definition = FlowEngine.defService().getPublishByFlowCode(startProcessBo.getFlowCode());
        Dict dict = JsonUtils.parseMap(definition.getExt());
        boolean autoPass = !ObjectUtil.isNull(dict) && dict.getBool(FlowConstant.AUTO_PASS);
        variables.put(FlowConstant.AUTO_PASS, autoPass);

        FlowParams flowParams = FlowParams.build()
                .flowCode(startProcessBo.getFlowCode())
                .variable(startProcessBo.getVariables())
                .flowStatus(BusinessStatusEnum.DRAFT.getStatus());
        Instance instance;
        try {
            instance = insService.start(businessId, flowParams);
        } catch (Exception e) {
            throw new ServiceException(e.getMessage());
        }
        // 申请人执行流程
        List<Task> taskList = taskService.list(new FlowTask().setInstanceId(instance.getId()));
        if (taskList.size() > 1) {
            throw new ServiceException("请检查流程第一个环节是否为申请人！");
        }
        StartProcessReturnDTO dto = new StartProcessReturnDTO();
        dto.setProcessInstanceId(instance.getId());
        dto.setTaskId(taskList.get(0).getId());
        return dto;
    }

    /**
     * 办理任务
     *
     * @param completeTaskBo 办理任务参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeTask(CompleteTaskBo completeTaskBo) {
        try {
            // 获取任务ID并查询对应的流程任务和实例信息
            Long taskId = completeTaskBo.getTaskId();
            List<String> messageType = completeTaskBo.getMessageType();
            String notice = completeTaskBo.getNotice();
            // 获取抄送人
            List<FlowCopyBo> flowCopyList = completeTaskBo.getFlowCopyList();
            // 设置抄送人
            Map<String, Object> variables = completeTaskBo.getVariables();
            variables.put(FlowConstant.FLOW_COPY_LIST, flowCopyList);
            // 消息类型
            variables.put(FlowConstant.MESSAGE_TYPE, messageType);
            // 消息通知
            variables.put(FlowConstant.MESSAGE_NOTICE, notice);


            FlowTask flowTask = flowTaskMapper.selectById(taskId);
            if (ObjectUtil.isNull(flowTask)) {
                throw new ServiceException("流程任务不存在或任务已审批！");
            }
            Instance ins = insService.getById(flowTask.getInstanceId());
            // 检查流程状态是否为草稿、已撤销或已退回状态，若是则执行流程提交监听
            if (BusinessStatusEnum.isDraftOrCancelOrBack(ins.getFlowStatus())) {
                variables.put(FlowConstant.SUBMIT, true);
            }
            // 设置弹窗处理人
            Map<String, Object> assigneeMap = setPopAssigneeMap(completeTaskBo.getAssigneeMap(), ins.getVariableMap());
            if (CollUtil.isNotEmpty(assigneeMap)) {
                variables.putAll(assigneeMap);
            }
            // 构建流程参数，包括变量、跳转类型、消息、处理人、权限等信息
            FlowParams flowParams = FlowParams.build()
                    .variable(variables)
                    .skipType(SkipType.PASS.getKey())
                    .message(completeTaskBo.getMessage())
                    .flowStatus(BusinessStatusEnum.WAITING.getStatus())
                    .hisStatus(TaskStatusEnum.PASS.getStatus())
                    .hisTaskExt(completeTaskBo.getFileId());
            Boolean autoPass = Convert.toBool(variables.getOrDefault(AUTO_PASS, false));
            skipTask(taskId, flowParams, flowTask.getInstanceId(), autoPass);
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * 流程办理
     *
     * @param taskId     任务ID
     * @param flowParams 参数
     * @param instanceId 实例ID
     * @param autoPass   自动审批
     */
    private void skipTask(Long taskId, FlowParams flowParams, Long instanceId, Boolean autoPass) {
        // 执行任务跳转，并根据返回的处理人设置下一步处理人
        taskService.skip(taskId, flowParams);
        List<FlowTask> flowTaskList = selectByInstId(instanceId);
        if (CollUtil.isEmpty(flowTaskList)) {
            return;
        }
        List<User> userList = FlowEngine.userService()
                .getByAssociateds(StreamUtils.toList(flowTaskList, FlowTask::getId));
        if (CollUtil.isEmpty(userList)) {
            return;
        }
        for (FlowTask task : flowTaskList) {
            if (!task.getId().equals(taskId) && autoPass) {
                List<User> users = StreamUtils.filter(userList, e -> ObjectUtil.equals(task.getId(), e.getAssociated()) && ObjectUtil.equal(e.getProcessedBy(), LoginHelper.getUserIdStr()));
                if (CollUtil.isEmpty(users)) {
                    continue;
                }
                flowParams.
                        message("流程引擎自动审批！").
                        variable(Map.of(
                                FlowConstant.SUBMIT, false,
                                FlowConstant.FLOW_COPY_LIST, Collections.emptyList(),
                                FlowConstant.MESSAGE_NOTICE, StringUtils.EMPTY));
                skipTask(task.getId(), flowParams, instanceId, true);
            }
        }
    }

    /**
     * 设置弹窗处理人
     *
     * @param assigneeMap  处理人
     * @param variablesMap 变量
     */
    private Map<String, Object> setPopAssigneeMap(Map<String, Object> assigneeMap, Map<String, Object> variablesMap) {
        Map<String, Object> map = new HashMap<>();
        if (CollUtil.isEmpty(assigneeMap)) {
            return map;
        }
        for (Map.Entry<String, Object> entry : assigneeMap.entrySet()) {
            if (variablesMap.containsKey(entry.getKey())) {
                String userIds = variablesMap.get(entry.getKey()).toString();
                if (StringUtils.isNotBlank(userIds)) {
                    Set<String> hashSet = new HashSet<>();
                    //弹窗传入的选人
                    List<String> popUserIds = Arrays.asList(entry.getValue().toString().split(StringUtils.SEPARATOR));
                    //已有的选人
                    List<String> variableUserIds = Arrays.asList(userIds.split(StringUtils.SEPARATOR));
                    hashSet.addAll(popUserIds);
                    hashSet.addAll(variableUserIds);
                    map.put(entry.getKey(), String.join(StringUtils.SEPARATOR, hashSet));
                }
            } else {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return map;
    }

    /**
     * 添加抄送人
     *
     * @param task         任务信息
     * @param flowCopyList 抄送人
     */
    @Override
    public void setCopy(Task task, List<FlowCopyBo> flowCopyList) {
        if (CollUtil.isEmpty(flowCopyList)) {
            return;
        }
        // 添加抄送人记录
        FlowHisTask flowHisTask = flowHisTaskMapper.selectList(
                new LambdaQueryWrapper<>(FlowHisTask.class)
                        .eq(FlowHisTask::getTaskId, task.getId())).get(0);
        FlowNode flowNode = new FlowNode();
        flowNode.setNodeCode(flowHisTask.getTargetNodeCode());
        flowNode.setNodeName(flowHisTask.getTargetNodeName());
        //生成新的任务id
        long taskId = identifierGenerator.nextId(null).longValue();
        task.setId(taskId);
        task.setNodeName("【抄送】" + task.getNodeName());
        Date updateTime = new Date(flowHisTask.getUpdateTime().getTime() - 1000);
        FlowParams flowParams = FlowParams.build()
                .skipType(SkipType.NONE.getKey())
                .hisStatus(TaskStatusEnum.COPY.getStatus())
                .message("【抄送给】" + StreamUtils.join(flowCopyList, FlowCopyBo::getUserName));
        HisTask hisTask = hisTaskService.setSkipHisTask(task, flowNode, flowParams);
        hisTask.setCreateTime(updateTime);
        hisTask.setUpdateTime(updateTime);
        hisTaskService.save(hisTask);
        List<User> userList = StreamUtils.toList(flowCopyList, x ->
                new FlowUser()
                        .setType(TaskAssigneeType.COPY.getCode())
                        .setProcessedBy(String.valueOf(x.getUserId()))
                        .setAssociated(taskId));
        // 批量保存抄送人员
        FlowEngine.userService().saveBatch(userList);
    }

    /**
     * 查询当前用户的待办任务
     *
     * @param flowTaskBo 参数
     * @param pageQuery  分页
     */
    @Override
    public TableDataInfo<FlowTaskVo> pageByTaskWait(FlowTaskBo flowTaskBo, PageQuery pageQuery) {
        QueryWrapper<FlowTaskBo> queryWrapper = buildQueryWrapper(flowTaskBo);
        queryWrapper.eq("t.node_type", NodeType.BETWEEN.getKey());
        queryWrapper.in("t.processed_by", LoginHelper.getUserIdStr());
        queryWrapper.in("t.flow_status", BusinessStatusEnum.WAITING.getStatus());
        Page<FlowTaskVo> page = flwTaskMapper.getListRunTask(pageQuery.build(), queryWrapper);
        this.wrapAssigneeInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 查询当前用户的已办任务
     *
     * @param flowTaskBo 参数
     * @param pageQuery  分页
     */
    @Override
    public TableDataInfo<FlowHisTaskVo> pageByTaskFinish(FlowTaskBo flowTaskBo, PageQuery pageQuery) {
        QueryWrapper<FlowTaskBo> queryWrapper = buildQueryWrapper(flowTaskBo);
        queryWrapper.eq("t.node_type", NodeType.BETWEEN.getKey());
        queryWrapper.in("t.approver", LoginHelper.getUserIdStr());
        queryWrapper.orderByDesc("t.create_time").orderByDesc("t.update_time");
        Page<FlowHisTaskVo> page = flwTaskMapper.getListFinishTask(pageQuery.build(), queryWrapper);
        return TableDataInfo.build(page);
    }

    /**
     * 查询待办任务
     *
     * @param flowTaskBo 参数
     * @param pageQuery  分页
     */
    @Override
    public TableDataInfo<FlowTaskVo> pageByAllTaskWait(FlowTaskBo flowTaskBo, PageQuery pageQuery) {
        QueryWrapper<FlowTaskBo> queryWrapper = buildQueryWrapper(flowTaskBo);
        queryWrapper.eq("t.node_type", NodeType.BETWEEN.getKey());
        Page<FlowTaskVo> page = flwTaskMapper.getListRunTask(pageQuery.build(), queryWrapper);
        this.wrapAssigneeInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 为流程任务列表封装处理人 ID（assigneeIds）
     *
     * @param taskList 流程任务列表
     */
    private void wrapAssigneeInfo(List<FlowTaskVo> taskList) {
        if (CollUtil.isEmpty(taskList)) {
            return;
        }
        List<User> associatedUsers = FlowEngine.userService().getByAssociateds(StreamUtils.toList(taskList, FlowTaskVo::getId));
        Map<Long, List<User>> taskUserMap = StreamUtils.groupByKey(associatedUsers, User::getAssociated);
        // 组装用户数据回任务列表
        for (FlowTaskVo task : taskList) {
            List<User> users = taskUserMap.get(task.getId());
            task.setAssigneeIds(StreamUtils.join(users, User::getProcessedBy));
        }
    }

    /**
     * 查询已办任务
     *
     * @param flowTaskBo 参数
     * @param pageQuery  分页
     */
    @Override
    public TableDataInfo<FlowHisTaskVo> pageByAllTaskFinish(FlowTaskBo flowTaskBo, PageQuery pageQuery) {
        QueryWrapper<FlowTaskBo> queryWrapper = buildQueryWrapper(flowTaskBo);
        Page<FlowHisTaskVo> page = flwTaskMapper.getListFinishTask(pageQuery.build(), queryWrapper);
        return TableDataInfo.build(page);
    }

    /**
     * 查询当前用户的抄送
     *
     * @param flowTaskBo 参数
     * @param pageQuery  分页
     */
    @Override
    public TableDataInfo<FlowTaskVo> pageByTaskCopy(FlowTaskBo flowTaskBo, PageQuery pageQuery) {
        QueryWrapper<FlowTaskBo> queryWrapper = buildQueryWrapper(flowTaskBo);
        queryWrapper.in("t.processed_by", LoginHelper.getUserIdStr());
        Page<FlowTaskVo> page = flwTaskMapper.getTaskCopyByPage(pageQuery.build(), queryWrapper);
        return TableDataInfo.build(page);
    }

    private QueryWrapper<FlowTaskBo> buildQueryWrapper(FlowTaskBo flowTaskBo) {
        QueryWrapper<FlowTaskBo> wrapper = Wrappers.query();
        wrapper.like(StringUtils.isNotBlank(flowTaskBo.getNodeName()), "t.node_name", flowTaskBo.getNodeName());
        wrapper.like(StringUtils.isNotBlank(flowTaskBo.getFlowName()), "t.flow_name", flowTaskBo.getFlowName());
        wrapper.like(StringUtils.isNotBlank(flowTaskBo.getFlowCode()), "t.flow_code", flowTaskBo.getFlowCode());
        wrapper.in(CollUtil.isNotEmpty(flowTaskBo.getCreateByIds()), "t.create_by", flowTaskBo.getCreateByIds());
        if (StringUtils.isNotBlank(flowTaskBo.getCategory())) {
            List<Long> categoryIds = flwCategoryMapper.selectCategoryIdsByParentId(Convert.toLong(flowTaskBo.getCategory()));
            wrapper.in("t.category", StreamUtils.toList(categoryIds, Convert::toStr));
        }
        wrapper.orderByDesc("t.create_time");
        return wrapper;
    }

    /**
     * 驳回任务
     *
     * @param bo 参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean backProcess(BackProcessBo bo) {
        try {
            Long taskId = bo.getTaskId();
            String notice = bo.getNotice();
            List<String> messageType = bo.getMessageType();
            String message = bo.getMessage();
            FlowTask task = flowTaskMapper.selectById(taskId);
            if (ObjectUtil.isNull(task)) {
                throw new ServiceException("任务不存在！");
            }
            Instance inst = insService.getById(task.getInstanceId());
            BusinessStatusEnum.checkBackStatus(inst.getFlowStatus());
            Long definitionId = task.getDefinitionId();
            String applyNodeCode = flwCommonService.applyNodeCode(definitionId);

            Map<String, Object> variable = new HashMap<>();
            // 消息类型
            variable.put("messageType", messageType);
            // 消息通知
            variable.put("notice", notice);

            FlowParams flowParams = FlowParams.build()
                    .nodeCode(bo.getNodeCode())
                    .variable(variable)
                    .message(message)
                    .skipType(SkipType.REJECT.getKey())
                    .flowStatus(applyNodeCode.equals(bo.getNodeCode()) ? TaskStatusEnum.BACK.getStatus() : TaskStatusEnum.WAITING.getStatus())
                    .hisStatus(TaskStatusEnum.BACK.getStatus())
                    .hisTaskExt(bo.getFileId());
            taskService.skip(task.getId(), flowParams);
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * 获取可驳回的前置节点
     *
     * @param taskId      任务id
     * @param nowNodeCode 当前节点
     */
    @Override
    public List<Node> getBackTaskNode(Long taskId, String nowNodeCode) {
        FlowTask task = flowTaskMapper.selectById(taskId);
        List<Node> nodeCodes = nodeService.getByNodeCodes(Collections.singletonList(nowNodeCode), task.getDefinitionId());
        if (!CollUtil.isNotEmpty(nodeCodes)) {
            return nodeCodes;
        }
        List<User> userList = FlowEngine.userService()
                .getByAssociateds(Collections.singletonList(task.getId()), UserType.DEPUTE.getKey());
        if (CollUtil.isNotEmpty(userList)) {
            return nodeCodes;
        }
        //判断是否配置了固定驳回节点
        Node node = nodeCodes.get(0);
        if (StringUtils.isNotBlank(node.getAnyNodeSkip())) {
            return nodeService.getByNodeCodes(Collections.singletonList(node.getAnyNodeSkip()), task.getDefinitionId());
        }
        //获取可驳回的前置节点
        List<Node> nodes = nodeService.previousNodeList(task.getDefinitionId(), nowNodeCode);
        if (CollUtil.isNotEmpty(nodes)) {
            return StreamUtils.filter(nodes, e -> NodeType.BETWEEN.getKey().equals(e.getNodeType()));
        }
        return nodes;
    }

    /**
     * 终止任务
     *
     * @param bo 参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean terminationTask(FlowTerminationBo bo) {
        try {
            Long taskId = bo.getTaskId();
            Task task = taskService.getById(taskId);
            if (task == null) {
                throw new ServiceException("任务不存在！");
            }
            Instance instance = insService.getById(task.getInstanceId());
            if (ObjectUtil.isNotNull(instance)) {
                BusinessStatusEnum.checkInvalidStatus(instance.getFlowStatus());
            }
            FlowParams flowParams = FlowParams.build()
                    .message(bo.getComment())
                    .flowStatus(BusinessStatusEnum.TERMINATION.getStatus())
                    .hisStatus(TaskStatusEnum.TERMINATION.getStatus());
            taskService.termination(taskId, flowParams);
            return true;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * 按照任务id查询任务
     *
     * @param taskIdList 任务id
     */
    @Override
    public List<FlowTask> selectByIdList(List<Long> taskIdList) {
        return flowTaskMapper.selectList(new LambdaQueryWrapper<>(FlowTask.class).in(FlowTask::getId, taskIdList));
    }

    /**
     * 按照任务id查询任务
     *
     * @param taskId 任务id
     */
    @Override
    public FlowTaskVo selectById(Long taskId) {
        Task task = taskService.getById(taskId);
        if (ObjectUtil.isNull(task)) {
            return null;
        }
        FlowTaskVo flowTaskVo = BeanUtil.toBean(task, FlowTaskVo.class);
        Instance instance = insService.getById(task.getInstanceId());
        Definition definition = defService.getById(task.getDefinitionId());
        flowTaskVo.setFlowStatus(instance.getFlowStatus());
        flowTaskVo.setVersion(definition.getVersion());
        flowTaskVo.setFlowCode(definition.getFlowCode());
        flowTaskVo.setFlowName(definition.getFlowName());
        flowTaskVo.setBusinessId(instance.getBusinessId());
        FlowNode flowNode = this.getByNodeCode(flowTaskVo.getNodeCode(), instance.getDefinitionId());
        if (ObjectUtil.isNull(flowNode)) {
            throw new NullPointerException("当前【" + flowTaskVo.getNodeCode() + "】节点编码不存在");
        }
        //设置按钮权限
        flowTaskVo.setButtonList(flwNodeExtService.buildButtonPermissionsFromExt(flowNode.getExt()));
        flowTaskVo.setNodeRatio(flowNode.getNodeRatio());
        flowTaskVo.setApplyNode(flowNode.getNodeCode().equals(flwCommonService.applyNodeCode(task.getDefinitionId())));
        return flowTaskVo;
    }

    /**
     * 获取下一节点信息
     *
     * @param bo 参数
     */
    @Override
    public List<FlowNode> getNextNodeList(FlowNextNodeBo bo) {
        Long taskId = bo.getTaskId();
        Map<String, Object> variables = bo.getVariables();
        Task task = taskService.getById(taskId);
        Instance instance = insService.getById(task.getInstanceId());
        Definition definition = defService.getById(task.getDefinitionId());
        Map<String, Object> mergeVariable = MapUtil.mergeAll(instance.getVariableMap(), variables);
        // 获取下一节点列表
        List<Node> nextNodeList = nodeService.getNextNodeList(task.getDefinitionId(), task.getNodeCode(), null, SkipType.PASS.getKey(), mergeVariable);
        List<FlowNode> nextFlowNodes = BeanUtil.copyToList(nextNodeList, FlowNode.class);
        // 只获取中间节点
        nextFlowNodes = StreamUtils.filter(nextFlowNodes, node -> NodeType.BETWEEN.getKey().equals(node.getNodeType()));
        if (CollUtil.isNotEmpty(nextNodeList)) {
            //构建以下节点数据
            List<Task> buildNextTaskList = StreamUtils.toList(nextNodeList, node -> taskService.addTask(node, instance, definition, FlowParams.build()));
            //办理人变量替换
            ExpressionUtil.evalVariable(buildNextTaskList, FlowParams.build().variable(mergeVariable));
            for (FlowNode flowNode : nextFlowNodes) {
                Task first = StreamUtils.findFirst(buildNextTaskList, t -> t.getNodeCode().equals(flowNode.getNodeCode()));
                if (ObjectUtil.isNotNull(first) && CollUtil.isNotEmpty(first.getPermissionList())) {
                    List<UserDTO> users = flwTaskAssigneeService.fetchUsersByStorageIds(String.join(StringUtils.SEPARATOR, first.getPermissionList()));
                    if (CollUtil.isNotEmpty(users)) {
                        flowNode.setPermissionFlag(StreamUtils.join(users, e -> String.valueOf(e.getUserId())));
                    }
                }

            }
        }
        return nextFlowNodes;
    }

    /**
     * 按照任务id查询任务
     *
     * @param taskId 任务id
     * @return 结果
     */
    @Override
    public FlowHisTask selectHisTaskById(Long taskId) {
        return flowHisTaskMapper.selectOne(new LambdaQueryWrapper<>(FlowHisTask.class).eq(FlowHisTask::getId, taskId));
    }

    /**
     * 按照实例id查询任务
     *
     * @param instanceId 流程实例id
     */
    @Override
    public List<FlowTask> selectByInstId(Long instanceId) {
        return flowTaskMapper.selectList(new LambdaQueryWrapper<>(FlowTask.class).eq(FlowTask::getInstanceId, instanceId));
    }

    /**
     * 按照实例id查询任务
     *
     * @param instanceIds 流程实例id
     */
    @Override
    public List<FlowTask> selectByInstIds(List<Long> instanceIds) {
        return flowTaskMapper.selectList(new LambdaQueryWrapper<>(FlowTask.class).in(FlowTask::getInstanceId, instanceIds));
    }

    /**
     * 判断流程是否已结束（即该流程实例下是否还有未完成的任务）
     *
     * @param instanceId 流程实例ID
     * @return true 表示任务已全部结束；false 表示仍有任务存在
     */
    @Override
    public boolean isTaskEnd(Long instanceId) {
        boolean exists = flowTaskMapper.exists(new LambdaQueryWrapper<FlowTask>().eq(FlowTask::getInstanceId, instanceId));
        return !exists;
    }

    /**
     * 任务操作
     *
     * @param bo            参数
     * @param taskOperation 操作类型，委派 delegateTask、转办 transferTask、加签 addSignature、减签 reductionSignature
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean taskOperation(TaskOperationBo bo, String taskOperation) {
        FlowParams flowParams = FlowParams.build().message(bo.getMessage());
        if (LoginHelper.isSuperAdmin() || LoginHelper.isTenantAdmin()) {
            flowParams.ignore(true);
        }

        // 根据操作类型构建 FlowParams
        switch (taskOperation) {
            case DELEGATE_TASK, TRANSFER_TASK -> {
                ValidatorUtils.validate(bo, AddGroup.class);
                flowParams.addHandlers(Collections.singletonList(bo.getUserId()));
            }
            case ADD_SIGNATURE -> {
                ValidatorUtils.validate(bo, EditGroup.class);
                flowParams.addHandlers(bo.getUserIds());
            }
            case REDUCTION_SIGNATURE -> {
                ValidatorUtils.validate(bo, EditGroup.class);
                flowParams.reductionHandlers(bo.getUserIds());
            }
            default -> {
                log.error("Invalid operation type:{} ", taskOperation);
                throw new ServiceException("Invalid operation type " + taskOperation);
            }
        }

        Long taskId = bo.getTaskId();
        Task task = taskService.getById(taskId);
        FlowNode flowNode = getByNodeCode(task.getNodeCode(), task.getDefinitionId());
        if ("addSignature".equals(taskOperation) || "reductionSignature".equals(taskOperation)) {
            if (flowNode.getNodeRatio().compareTo(BigDecimal.ZERO) == 0) {
                throw new ServiceException(task.getNodeName() + "不是会签节点！");
            }
        }
        // 设置任务状态并执行对应的任务操作
        switch (taskOperation) {
            //委派任务
            case DELEGATE_TASK -> {
                flowParams.hisStatus(TaskStatusEnum.DEPUTE.getStatus());
                return taskService.depute(taskId, flowParams);
            }
            //转办任务
            case TRANSFER_TASK -> {
                flowParams.hisStatus(TaskStatusEnum.TRANSFER.getStatus());
                return taskService.transfer(taskId, flowParams);
            }
            //加签，增加办理人
            case ADD_SIGNATURE -> {
                flowParams.hisStatus(TaskStatusEnum.SIGN.getStatus());
                return taskService.addSignature(taskId, flowParams);
            }
            //减签，减少办理人
            case REDUCTION_SIGNATURE -> {
                flowParams.hisStatus(TaskStatusEnum.SIGN_OFF.getStatus());
                return taskService.reductionSignature(taskId, flowParams);
            }
            default -> {
                log.error("Invalid operation type:{} ", taskOperation);
                throw new ServiceException("Invalid operation type " + taskOperation);
            }
        }
    }

    /**
     * 修改任务办理人（此方法将会批量修改所有任务的办理人）
     *
     * @param taskIdList 任务id
     * @param userId     用户id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAssignee(List<Long> taskIdList, String userId) {
        if (CollUtil.isEmpty(taskIdList)) {
            return false;
        }
        try {
            List<FlowTask> flowTasks = this.selectByIdList(taskIdList);
            // 批量删除现有任务的办理人记录
            if (CollUtil.isNotEmpty(flowTasks)) {
                FlowEngine.userService().deleteByTaskIds(StreamUtils.toList(flowTasks, FlowTask::getId));
                List<User> userList = StreamUtils.toList(flowTasks, flowTask ->
                        new FlowUser()
                                .setType(TaskAssigneeType.APPROVER.getCode())
                                .setProcessedBy(userId)
                                .setAssociated(flowTask.getId()));
                if (CollUtil.isNotEmpty(userList)) {
                    FlowEngine.userService().saveBatch(userList);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException(e.getMessage());
        }
        return true;
    }

    /**
     * 获取当前任务的所有办理人
     *
     * @param taskIds 任务id
     */
    @Override
    public List<UserDTO> currentTaskAllUser(List<Long> taskIds) {
        // 获取与当前任务关联的用户列表
        List<User> userList = FlowEngine.userService().getByAssociateds(taskIds);
        if (CollUtil.isEmpty(userList)) {
            return Collections.emptyList();
        }
        return userService.selectListByIds(StreamUtils.toList(userList, e -> Convert.toLong(e.getProcessedBy())));
    }

    /**
     * 按照节点编码查询节点
     *
     * @param nodeCode     节点编码
     * @param definitionId 流程定义id
     */
    @Override
    public FlowNode getByNodeCode(String nodeCode, Long definitionId) {
        return flowNodeMapper.selectOne(new LambdaQueryWrapper<FlowNode>()
                .eq(FlowNode::getNodeCode, nodeCode)
                .eq(FlowNode::getDefinitionId, definitionId));
    }

    /**
     * 催办任务
     *
     * @param bo 参数
     */
    @Override
    public boolean urgeTask(FlowUrgeTaskBo bo) {
        try {
            if (CollUtil.isEmpty(bo.getTaskIdList())) {
                return false;
            }
            List<UserDTO> userList = this.currentTaskAllUser(bo.getTaskIdList());
            if (CollUtil.isEmpty(userList)) {
                return false;
            }
            List<String> messageType = bo.getMessageType();
            String message = bo.getMessage();
            flwCommonService.sendMessage(messageType, message, "单据审批提醒", userList);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new ServiceException(e.getMessage());
        }
        return true;
    }
}
