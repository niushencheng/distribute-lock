package com.niushencheng.interceptor;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 上下文环境
 * <p>
 * CreateTime: 2017-07-08 12:40:33
 *
 * @author zicheng.liang
 * @version 1.0
 * @since JDK 1.7
 */
class LockEvaluationContext extends MethodBasedEvaluationContext {

    private final List<String> unavailableVariables;

    LockEvaluationContext(Object rootObject, Method method, Object[] args,
                          ParameterNameDiscoverer paramDiscoverer) {

        super(rootObject, method, args, paramDiscoverer);
        this.unavailableVariables = new ArrayList<String>();
    }

    /**
     * Add the specified variable name as unavailable for that context. Any expression trying
     * to access this variable should lead to an exception.
     * <p>This permits the validation of expressions that could potentially a variable even
     * when such variable isn't available yet. Any expression trying to use that variable should
     * therefore fail to evaluate.
     */
    public void addUnavailableVariable(String name) {
        this.unavailableVariables.add(name);
    }


    /**
     * Load the param information only when needed.
     */
    @Override
    public Object lookupVariable(String name) {
        if (this.unavailableVariables.contains(name)) {
            throw new VariableNotAvailableException(name);
        }
        return super.lookupVariable(name);
    }

}
