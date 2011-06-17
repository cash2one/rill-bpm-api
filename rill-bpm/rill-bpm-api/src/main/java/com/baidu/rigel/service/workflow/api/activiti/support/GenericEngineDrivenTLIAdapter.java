/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.baidu.rigel.service.workflow.api.activiti.support;

import com.baidu.rigel.service.workflow.api.WorkflowOperations;
import com.baidu.rigel.service.workflow.api.activiti.ActivitiAccessor;
import com.baidu.rigel.service.workflow.api.activiti.ActivitiTaskExecutionContext;
import com.baidu.rigel.service.workflow.api.exception.ProcessException;
import java.lang.reflect.Method;
import org.activiti.engine.impl.util.ReflectUtil;
import org.activiti.engine.task.Task;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Common engine driven TLI adapter.
 *
 * <p>
 * Obtain task service invoke expression from:
 * <ul>
 *  <li> from {@link com.baidu.rigel.service.workflow.api.activiti.TLITOIClassDelegateAdapter}'s configuration.
 *  <li> [taskName]Service.[taskName]Complete(DTO.class.getName()) that follow JavaEE6's [Configuration With Exception], only if {@link #isJavaEE6CWE() FLAG} is true.
 * 
 * @author mengran
 */
public class GenericEngineDrivenTLIAdapter extends EngineDrivenTLIAdapter<Object> implements BeanFactoryAware {

    private static final String UNKNOWN = GenericEngineDrivenTLIAdapter.class.getName() + ".UNKNOWN";

    private WorkflowOperations workflowAccessor;
    private BeanFactory beanFactory;

    private boolean javaEE6CWE = false;

    public boolean isJavaEE6CWE() {
        return javaEE6CWE;
    }

    public void setJavaEE6CWE(boolean javaEE6CWE) {
        this.javaEE6CWE = javaEE6CWE;
    }
    

    public WorkflowOperations getWorkflowAccessor() {
        return workflowAccessor;
    }

    public void setWorkflowAccessor(WorkflowOperations workflowAccessor) {
        this.workflowAccessor = workflowAccessor;
    }

    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {

        this.beanFactory = beanFactory;
    }

    private String obtainTaskServiceInvokeExpression(ActivitiTaskExecutionContext taskExecutionContext) {

        return getWorkflowAccessor().getTaskInstanceExtendAttrs(taskExecutionContext.getTaskInstanceId()).get(ActivitiAccessor.TASK_SERVICE_INVOKE_EXPRESSION);
    }

    private boolean isArray(Object obj) {

        return obj != null && obj.getClass().isArray();
    }

    @Override
    protected Object doEngineDriven(Object t, ActivitiTaskExecutionContext taskExecutionContext) {
        
        String expression = null;
        // Try to obtain from configuration.
        expression = obtainTaskServiceInvokeExpression(taskExecutionContext);
        if (!StringUtils.hasLength(expression)) {
            if (!isJavaEE6CWE()) {
                logger.fine("Can not find task service invoke expression from task extend attrs, please set flag to [true] for follow JavaEE6CWE");
                return null;
            }
            // Build follow JavaEE6's [Configuration With Exception].
            Task currentTask = taskExecutionContext.getCurrentTask();
            Assert.notNull(currentTask, "Can not find current task from execution context");
            expression = currentTask.getName() + "Service." + currentTask.getName() + "Complete(" + UNKNOWN + ")";

            logger.finest("Can not find task service invoke expression from task extend attrs, " +
                    "and we build one follow JavaEE6's [Configuration With Exception] pattern : " + expression);
        }
        logger.fine("Using task service invoke expression: " + expression);

        // Prepare invoke informations
        String[] beforeLeftBracket = expression.substring(0, expression.indexOf("(")).split("\\.");
        Assert.isTrue(beforeLeftBracket.length == 2, "The expression parttern is [taskName]Service.[taskName]Complete(DTO.class.getName()): " + expression);
        String serviceName = beforeLeftBracket[0].trim();
        String methodName = beforeLeftBracket[1].trim();
        String parameters = expression.substring(expression.indexOf("(") + 1, expression.indexOf(")")).trim();
        String[] params = parameters.split(",");
        Object serviceBean = this.beanFactory.getBean(serviceName);
//        Class<?> clazz = serviceBean instanceof SpringProxy ? ((Advised) serviceBean).getTargetClass() : serviceBean.getClass();
        // Direct return service bean's class, maybe proxied.
        Class<?> clazz = serviceBean.getClass();

        // Do invoke
        if (UNKNOWN.equals(parameters)) {
            // JavaEE6's [Configuration With Exception] pattern.
            throw new UnsupportedOperationException("Support soon...");
        } else if (!StringUtils.hasLength(parameters)) {
            // Empty parameter.
            Method method = ReflectionUtils.findMethod(clazz, methodName);
            // Not empty array or not null
            if ((isArray(t) && !ObjectUtils.isEmpty((Object[]) t)) || t != null) {
                logger.warning("Invoke expression: " + expression + ", and is not need any parameter.");
            }
            logger.fine("Reflect method: " + method + " on service bean: " + serviceBean + " for expression:" + expression);
            ReflectionUtils.makeAccessible(method);
            return ReflectionUtils.invokeMethod(method, serviceBean);
        } else {
            // Empty array or null object
            if ((isArray(t) && ObjectUtils.isEmpty((Object[]) t)) || t == null) {
                throw new ProcessException("Task service invoke expression parameter:" + parameters + ", so we need a actual parameter.");
            }
            // Prepare invoke parameter
            Class<?>[] parameterClasses = new Class<?>[params.length];
            Object[] parameterObject = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                parameterClasses[i] = ClassUtils.resolveClassName(params[i].trim(), ClassUtils.getDefaultClassLoader());
                if (ClassUtils.isPrimitiveOrWrapper(parameterClasses[i])) {
                    parameterObject[i] = ((Object[]) t)[i];
                } else {
                    parameterObject[i] = ReflectUtil.instantiate(params[i].trim());
                }
            }
            // Shallow copy
            if (isArray(t)) {
                parameterObject = (Object[]) t;
            } else {
                ReflectionUtils.shallowCopyFieldState(t, parameterObject[0]);
            }
            
            Method method = ReflectionUtils.findMethod(clazz, methodName, parameterClasses);
            ReflectionUtils.makeAccessible(method);
            logger.fine("Reflect method: " + method + " on service bean: " + serviceBean + " params: " + ObjectUtils.getDisplayString(parameterObject) + " for expression:" + expression);
            return ReflectionUtils.invokeMethod(method, serviceBean, parameterObject);
        }

    }

}
