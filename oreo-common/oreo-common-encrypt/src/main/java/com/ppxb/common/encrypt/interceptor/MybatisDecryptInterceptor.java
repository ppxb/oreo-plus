package com.ppxb.common.encrypt.interceptor;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.*;
import com.ppxb.common.core.utils.StringUtils;
import com.ppxb.common.encrypt.annotation.EncryptField;
import com.ppxb.common.encrypt.core.EncryptContext;
import com.ppxb.common.encrypt.core.EncryptorManager;
import com.ppxb.common.encrypt.enumd.AlgorithmType;
import com.ppxb.common.encrypt.enumd.EncodeType;
import com.ppxb.common.encrypt.properties.EncryptorProperties;
import java.lang.reflect.Field;
import java.sql.Statement;
import java.util.*;

/**
 * 出参解密拦截器
 *
 * @author 老马
 * @version 4.6.0
 */
@Slf4j
@Intercepts({@Signature(
    type = ResultSetHandler.class,
    method = "handleResultSets",
    args = {Statement.class})
})
@AllArgsConstructor
public class MybatisDecryptInterceptor implements Interceptor {

    private final EncryptorManager encryptorManager;
    private final EncryptorProperties defaultProperties;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 开始进行参数解密
        ResultSetHandler resultSetHandler = (ResultSetHandler) invocation.getTarget();
        Field parameterHandlerField = resultSetHandler.getClass().getDeclaredField("parameterHandler");
        parameterHandlerField.setAccessible(true);
        Object target = parameterHandlerField.get(resultSetHandler);
        if (target instanceof ParameterHandler parameterHandler) {
            Object parameterObject = parameterHandler.getParameterObject();
            if (ObjectUtil.isNotNull(parameterObject) && !(parameterObject instanceof String)) {
                this.decryptHandler(parameterObject);
            }
        }
        // 获取执行mysql执行结果
        Object result = invocation.proceed();
        if (result == null) {
            return null;
        }
        this.decryptHandler(result);
        return result;
    }

    /**
     * 解密对象
     *
     * @param sourceObject 待加密对象
     */
    private void decryptHandler(Object sourceObject) {
        if (ObjectUtil.isNull(sourceObject)) {
            return;
        }
        if (sourceObject instanceof Map<?, ?> map) {
            new HashSet<>(map.values()).forEach(this::decryptHandler);
            return;
        }
        if (sourceObject instanceof List<?> list) {
            if(CollUtil.isEmpty(list)) {
                return;
            }
            // 判断第一个元素是否含有注解。如果没有直接返回，提高效率
            Object firstItem = list.get(0);
            if (ObjectUtil.isNull(firstItem) || CollUtil.isEmpty(encryptorManager.getFieldCache(firstItem.getClass()))) {
                return;
            }
            list.forEach(this::decryptHandler);
            return;
        }
        // 不在缓存中的类,就是没有加密注解的类(当然也有可能是typeAliasesPackage写错)
        Set<Field> fields = encryptorManager.getFieldCache(sourceObject.getClass());
        if(ObjectUtil.isNull(fields)){
            return;
        }
        try {
            for (Field field : fields) {
                field.set(sourceObject, this.decryptField(Convert.toStr(field.get(sourceObject)), field));
            }
        } catch (Exception e) {
            log.error("处理解密字段时出错", e);
        }
    }

    /**
     * 字段值进行加密。通过字段的批注注册新的加密算法
     *
     * @param value 待加密的值
     * @param field 待加密字段
     * @return 加密后结果
     */
    private String decryptField(String value, Field field) {
        if (ObjectUtil.isNull(value)) {
            return null;
        }
        EncryptField encryptField = field.getAnnotation(EncryptField.class);
        EncryptContext encryptContext = new EncryptContext();
        encryptContext.setAlgorithm(encryptField.algorithm() == AlgorithmType.DEFAULT ? defaultProperties.getAlgorithm() : encryptField.algorithm());
        encryptContext.setEncode(encryptField.encode() == EncodeType.DEFAULT ? defaultProperties.getEncode() : encryptField.encode());
        encryptContext.setPassword(StringUtils.isBlank(encryptField.password()) ? defaultProperties.getPassword() : encryptField.password());
        encryptContext.setPrivateKey(StringUtils.isBlank(encryptField.privateKey()) ? defaultProperties.getPrivateKey() : encryptField.privateKey());
        encryptContext.setPublicKey(StringUtils.isBlank(encryptField.publicKey()) ? defaultProperties.getPublicKey() : encryptField.publicKey());
        return this.encryptorManager.decrypt(value, encryptContext);
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {

    }
}
