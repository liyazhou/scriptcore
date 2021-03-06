package script.groovy.runtime;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;

import script.groovy.annotation.Bean;
import script.groovy.object.GroovyObjectEx;
import script.groovy.runtime.GroovyRuntime.MyGroovyClassLoader;

public class GroovyBeanFactory implements ClassAnnotationHandler {
	private static final String TAG = GroovyBeanFactory.class.getSimpleName();

	private ConcurrentHashMap<String, GroovyObjectEx> beanMap = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, Class<?>> proxyClassMap = new ConcurrentHashMap<>();
	
	private static GroovyBeanFactory instance;

	public static GroovyBeanFactory getInstance() {
		return instance;
	}

	public GroovyBeanFactory() {
		instance = this;
	}
	
	public Class<?> getProxyClass(String className) {
		return proxyClassMap.get(className);
	}
	
	public <T> GroovyObjectEx<T> getBean(String beanName) {
		if(beanMap != null) {
			return beanMap.get(beanName);
		}
		return null;
	}
	
	public <T> GroovyObjectEx<T> getBean(Class<?> c) {
		if(c == null)
			return null;
		String groovyPath = GroovyRuntime.path(c);
		return getBean(groovyPath);
	}
	
	public <T> GroovyObjectEx<T> getBean(Class<?> c, boolean forceCreate) {
		if(forceCreate)
			return getObject(c);
		else
			return getBean(c);
	}
	
	private <T> GroovyObjectEx<T> getObject(String beanName, Class<?> c, ConcurrentHashMap<String, GroovyObjectEx> beanMap) {
		if(beanMap == null) {
			beanMap = this.beanMap;
		}
		String groovyPath = GroovyRuntime.path(c);
		GroovyObjectEx<T> goe = beanMap.get(groovyPath);
		if(goe == null) {
			
			goe = GroovyRuntime.getInstance().create(groovyPath);
			if(beanName == null) {
				beanName = groovyPath;
			}
			if(goe != null) {
				GroovyObjectEx<T> oldgoe = beanMap.putIfAbsent(beanName, goe);
				if(oldgoe != null) 
					goe = oldgoe;
			}
		}
		return goe;
	}
	private <T> GroovyObjectEx<T> getObject(Class<?> c, ConcurrentHashMap<String, GroovyObjectEx> beanMap) {
		return getObject(null, c, beanMap);
	}
	
	private <T> GroovyObjectEx<T> getObject(Class<?> c) {
		return getObject(c, null);
	}

	@Override
	public Class<? extends Annotation> handleAnnotationClass(GroovyRuntime groovyRuntime) {
		return Bean.class;
	}

	@Override
	public void handleAnnotatedClasses(Map<String, Class<?>> annotatedClassMap,
			MyGroovyClassLoader classLoader) {
		ConcurrentHashMap<String, GroovyObjectEx> newBeanMap = new ConcurrentHashMap<>();
		ConcurrentHashMap<String, Class<?>> newProxyClassMap = new ConcurrentHashMap<>();
		if(annotatedClassMap != null) {
			Collection<Class<?>> values = annotatedClassMap.values();
			for(Class<?> groovyClass : values) {
				Bean bean = groovyClass.getAnnotation(Bean.class);
				String name = bean.name();
				if(StringUtils.isBlank(name)) {
					name = null;
				}
				Class<?> groovyObjectExProxyClass = newProxyClassMap.get(groovyClass.getName());
				if(groovyObjectExProxyClass == null) {
					String[] strs = new String[] {
							"package script.groovy.runtime;",
							"import script.groovy.object.GroovyObjectEx",
							"class GroovyObjectEx" + groovyClass.getSimpleName() + "Proxy extends " + groovyClass.getName() + " implements GroovyInterceptable{",
								"private GroovyObjectEx<?> groovyObject;",
								"public GroovyObjectEx" + groovyClass.getSimpleName() + "Proxy(GroovyObjectEx<?> groovyObject) {",
									"this.groovyObject = groovyObject;",
								"}",
								"def invokeMethod(String name, args) {",
									"Class<?> groovyClass = this.groovyObject.getGroovyClass();",
									"def calledMethod = groovyClass.metaClass.getMetaMethod(name, args);",
									"def returnObj = calledMethod?.invoke(this.groovyObject.getObject(), args);",
									"return returnObj;",
								"}",
							"}"
					};
					String proxyClassStr = StringUtils.join(strs, "\r\n"); 
					groovyObjectExProxyClass = GroovyRuntime.getInstance().getClassLoader().parseClass(proxyClassStr, 
							"/script/groovy/runtime/proxy/GroovyObjectEx" + groovyClass.getSimpleName() + "Proxy.groovy");
					
					newProxyClassMap.put(groovyClass.getName(), groovyObjectExProxyClass);
				}
				
				getObject(name, groovyClass, newBeanMap);
			}
		}
		ConcurrentHashMap<String, Class<?>> oldProxyClassMap = proxyClassMap;
		proxyClassMap = newProxyClassMap;
		if(oldProxyClassMap != null)
			oldProxyClassMap.clear();
		
		ConcurrentHashMap<String, GroovyObjectEx> oldBeanMap = beanMap;
		beanMap = newBeanMap;
		if(oldBeanMap != null)
			oldBeanMap.clear();
	}

}
