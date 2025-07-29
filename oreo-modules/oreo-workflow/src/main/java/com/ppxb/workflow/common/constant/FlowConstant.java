package com.ppxb.workflow.common.constant;

/**
 * 工作流常量
 *
 * @author ppxb
 * @since 1.0.0
 */
public interface FlowConstant {

    /**
     * 流程发起人
     */
    String INITIATOR = "initiator";

    /**
     * 业务 id
     */
    String BUSINESS_ID = "businessId";

    /**
     * 部门 id
     */
    String INITIATOR_DEPT_ID = "initiatorDeptId";

    /**
     * 委托
     */
    String DELEGATE_TASK = "delegateTask";

    /**
     * 转办
     */
    String TRANSFER_TASK = "transferTask";

    /**
     * 加签
     */
    String ADD_SIGNATURE = "addSignature";

    /**
     * 减签
     */
    String REDUCTION_SIGNATURE = "reductionSignature";

    /**
     * 流程分类id 转名称
     */
    String CATEGORY_ID_TO_NAME = "category_id_to_name";

    /**
     * 流程分类名称
     */
    String FLOW_CATEGORY_NAME = "flow_category_name#30d";

    /**
     * 默认租户 OA 申请分类 id
     */
    Long FLOW_CATEGORY_ID = 100L;

    /**
     * 是否为申请人提交常量
     */
    String SUBMIT = "submit";

    /**
     * 抄送常量
     */
    String FLOW_COPY_LIST = "flowCopyList";

    /**
     * 消息类型常量
     */
    String MESSAGE_TYPE = "messageType";

    /**
     * 消息通知常量
     */
    String MESSAGE_NOTICE = "messageNotice";

    /**
     * 任务状态
     */
    String WF_TASK_STATUS = "wf_task_status";

    /**
     * 自动通过
     */
    String AUTO_PASS = "autoPass";
}
