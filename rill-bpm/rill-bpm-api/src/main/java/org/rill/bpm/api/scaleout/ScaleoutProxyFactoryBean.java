package org.rill.bpm.api.scaleout;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.ClassUtils;

@SuppressWarnings("rawtypes")
public class ScaleoutProxyFactoryBean extends ScaleoutInterceptor 
	implements FactoryBean, InitializingBean {

	private Object serviceProxy;
	
	@Override
	public Object getObject() throws Exception {
		
		return this.serviceProxy;
	}

	@Override
	public Class getObjectType() {
		
		return getServiceInterface();
	}

	@Override
	public boolean isSingleton() {
		
		return true;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		
		super.afterPropertiesSet();
		
		// Build a proxy.
		ProxyFactory pf = new ProxyFactory();
		pf.addInterface(getServiceInterface());
		pf.addAdvice(this);
		this.serviceProxy = pf.getProxy(ClassUtils.getDefaultClassLoader());
		
	}

}
