package com.ppxb.workflow.service.impl;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ppxb.common.core.domain.dto.UserDTO;
import com.ppxb.common.core.utils.SpringUtils;
import com.ppxb.common.core.utils.StreamUtils;
import com.ppxb.common.core.utils.StringUtils;
import com.ppxb.common.mail.utils.MailUtils;
import com.ppxb.common.sse.dto.SseMessageDto;
import com.ppxb.common.sse.utils.SseMessageUtils;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.NodeService;
import org.dromara.warm.flow.orm.entity.FlowTask;
import com.ppxb.workflow.common.ConditionalOnEnable;
import com.ppxb.workflow.common.enums.MessageTypeEnum;
import com.ppxb.workflow.service.IFlwCommonService;
import com.ppxb.workflow.service.IFlwTaskService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;


/**
 * 工作流工具
 *
 * @author LionLi
 */
@ConditionalOnEnable
@Slf4j
@RequiredArgsConstructor
@Service
public class FlwCommonServiceImpl implements IFlwCommonService {
    private final NodeService nodeService;

    /**
     * 发送消息
     *
     * @param flowName    流程定义名称
     * @param messageType 消息类型
     * @param message     消息内容，为空则发送默认配置的消息内容
     */
    @Override
    public void sendMessage(String flowName, Long instId, List<String> messageType, String message) {
        IFlwTaskService flwTaskService = SpringUtils.getBean(IFlwTaskService.class);
        List<FlowTask> list = flwTaskService.selectByInstId(instId);
        if (StringUtils.isBlank(message)) {
            message = "有新的【" + flowName + "】单据已经提交至您，请您及时处理。";
        }
        List<UserDTO> userList = flwTaskService.currentTaskAllUser(StreamUtils.toList(list, FlowTask::getId));
        if (CollUtil.isEmpty(userList)) {
            return;
        }
        sendMessage(messageType, message, "单据审批提醒", userList);
    }

    /**
     * 发送消息
     *
     * @param messageType 消息类型
     * @param message     消息内容
     * @param subject     邮件标题
     * @param userList    接收用户
     */
    @Override
    public void sendMessage(List<String> messageType, String message, String subject, List<UserDTO> userList) {
        for (String code : messageType) {
            MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByCode(code);
            if (ObjectUtil.isEmpty(messageTypeEnum)) {
                continue;
            }
            switch (messageTypeEnum) {
                case SYSTEM_MESSAGE -> {
                    SseMessageDto dto = new SseMessageDto();
                    dto.setUserIds(StreamUtils.toList(userList, UserDTO::getUserId).stream().distinct().collect(Collectors.toList()));
                    dto.setMessage(message);
                    SseMessageUtils.publishMessage(dto);
                }
                case EMAIL_MESSAGE -> {
                    MailUtils.sendText(StreamUtils.join(userList, UserDTO::getEmail), subject, message);
                }
                case SMS_MESSAGE -> {
                    //todo 短信发送
                }
                default -> throw new IllegalStateException("Unexpected value: " + messageTypeEnum);
            }
        }
    }


    /**
     * 申请人节点编码
     *
     * @param definitionId 流程定义id
     * @return 申请人节点编码
     */
    @Override
    public String applyNodeCode(Long definitionId) {
        Node startNode = nodeService.getStartNode(definitionId);
        Node nextNode = nodeService.getNextNode(definitionId, startNode.getNodeCode(), null, SkipType.PASS.getKey());
        return nextNode.getNodeCode();
    }
}
