package com.ppxb.system.domain.vo;

import com.ppxb.common.translation.annotation.Translation;
import com.ppxb.common.translation.constant.TransConstant;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息 VO
 *
 * @author ppxb
 * @since 1.0.0
 */
@Data
public class ProfileUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 租户 id
     */
    private String tenantId;

    /**
     * 部门 id
     */
    private Long deptId;

    /**
     * 账号
     */
    private String userName;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 用户类型
     */
    private String userType;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号码
     */
    private String phonenumber;

    /**
     * 性别（0-男；1-女；2-未知）
     */
    private String sex;

    /**
     * 头像地址
     */
    @Translation(type = TransConstant.OSS_ID_TO_URL)
    private Long avatar;

    /**
     * 最后登录 ip
     */
    private String loginIp;

    /**
     * 最后登录时间
     */
    private LocalDateTime loginDate;

    /**
     * 部门名称
     */
    @Translation(type = TransConstant.DEPT_ID_TO_NAME, mapper = "deptId")
    private String deptName;
}
