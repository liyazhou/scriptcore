package script.groovy.servlets;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;

import script.groovy.object.GroovyObjectEx;
import script.groovy.runtime.ClassAnnotationHandler;
import script.groovy.runtime.GroovyRuntime;
import script.groovy.runtime.GroovyRuntime.MyGroovyClassLoader;
import script.groovy.servlet.annotation.ControllerMapping;
import script.groovy.servlet.annotation.RequestMapping;
import chat.errors.ChatErrorCodes;
import chat.errors.CoreException;
import chat.logs.LoggerEx;
import chat.utils.ChatUtils;
import chat.utils.HashTree;
public class GroovyServletManager extends ClassAnnotationHandler {
	public static final String RESPONSETYPE_JSON = "json";
	public static final String RESPONSETYPE_DOWNLOAD = "download";
	
	public static final String VARIABLE = "VARIABLE";
	private static final String TAG = GroovyServletManager.class
			.getSimpleName();
	private HashTree<String, RequestURIWrapper> servletTree;
	private HashMap<String, GroovyObjectEx<RequestIntercepter>> interceptorMap;

	private static GroovyServletManager instance;

	public static GroovyServletManager getInstance() {
		return instance;
	}

	public GroovyServletManager() {
		instance = this;
	}

	private void handleRequestUri(String groovyPath, RequestURI requestUri, RequestURIWrapper requestUriWrapper, HashTree<String, RequestURIWrapper> tree, StringBuilder uriLogs) {
		String[] uris = requestUri.getUri();
		String requestMethod = requestUri.getMethod();
		String groovyMethod = requestUri.getGroovyMethod();
		HashTree<String, RequestURIWrapper> theTree = tree;
		if (groovyMethod != null && groovyPath != null
				&& requestMethod != null && uris != null) {
			for (String uri : uris) {
				HashTree<String, RequestURIWrapper> childrenTree = null;
				if (uri.startsWith("{") && uri.endsWith("}")) {
					childrenTree = theTree.getChildren(VARIABLE, true);
					uri = uri.substring(1, uri.length() - 1);
					String key = VARIABLE + "_" + requestMethod;
					Object params = childrenTree.getParameter(key);
					HashSet<String> uriSet = null;
					if(params == null) {
						uriSet = new HashSet<String>();
						childrenTree.setParameter(key, uriSet);
					} else if(params instanceof HashSet) {
						uriSet = (HashSet<String>) params;
					}
					if(uriSet != null && !uriSet.contains(uri)) {
						if(!uriSet.isEmpty()) 
							LoggerEx.warn(TAG, uri + " in " + ChatUtils.toString(uris) + " is occupied by other path variables " + ChatUtils.toString(uriSet) + ", please avoid these url design, this may cause bad performance issue.");
						uriSet.add(uri);
					}
				} else {
					childrenTree = theTree.getChildren(uri, true);
				}
				theTree = childrenTree;
			}
			
			RequestURIWrapper old = theTree.get(requestMethod);
			if (old == null) {
				requestUriWrapper.setMethod(groovyMethod);
				//TODO analyze parameters here.
				theTree.put(requestMethod, requestUriWrapper);
				uriLogs.append("Mapped " + ChatUtils.toString(uris, "/") + "#" + requestMethod + ": " + groovyPath + "#" + groovyMethod + "\r\n");
			} else {
				LoggerEx.error(TAG, "The uri " + ChatUtils.toString(uris)
						+ " has already mapped on " + old.getGroovyPath()
						+ "#" + old.getMethod() + ", the newer "
						+ groovyPath + "#" + requestMethod
						+ " is given up...");
			}
		}		
	}

	public RequestHolder parseUri(HttpServletRequest request,
			HttpServletResponse response) throws CoreException {
		if(this.servletTree == null)
			throw new CoreException(ChatErrorCodes.ERROR_GROOVYSERVLET_SERVLET_NOT_INITIALIZED, "Groovy servlet is not ready");
		String uri = request.getRequestURI();
		String method = request.getMethod();
		if (uri == null)
			return null;
		if (uri.startsWith("/")) {
			uri = uri.substring(1);
		}
		String[] uriStrs = uri.split("/");
		HashTree<String, RequestURIWrapper> theTree = this.servletTree;
		HashMap<String, String> parameters = null;
		for (String uriStr : uriStrs) {
			HashTree<String, RequestURIWrapper> children = theTree
					.getChildren(uriStr);
			if (children == null) {
				children = theTree.getChildren(VARIABLE);
				if(children != null) {
					String key = VARIABLE + "_" + method;
					Object params = children.getParameter(key);
					if(params != null && params instanceof HashSet) {
						HashSet<String> uriSet = (HashSet<String>) params;
						if (parameters == null)
							parameters = new HashMap<String, String>();
						for(String variable : uriSet) {
							parameters.put(variable, uriStr);
						}
					}
				}
			}
			if (children == null)
				return null;
			else
				theTree = children;
		}
		if (theTree != null) {
			RequestURIWrapper obj = theTree.get(method);
			if(obj != null) {
				GroovyObjectEx<RequestIntercepter> interceptor = null;
				if(interceptorMap != null) {
					interceptor = interceptorMap.get(obj.getGroovyPath());
				}
				return new RequestHolder(obj, request, response, parameters, interceptor);
			}
		}
		return null;
	}

	public HashMap<String, GroovyObjectEx<RequestIntercepter>> getInterceptorMap() {
		return interceptorMap;
	}

	@Override
	public Class<? extends Annotation> handleAnnotationClass() {
		return ControllerMapping.class;
	}

	@Override
	public void handleAnnotatedClasses(Map<String, Class<?>> annotatedClassMap,
			MyGroovyClassLoader classLoader) {
		GroovyRuntime groovyRuntime = getGroovyRuntime();
		if(annotatedClassMap != null && !annotatedClassMap.isEmpty()) {
			StringBuilder uriLogs = new StringBuilder("\r\n---------------------------------------\r\n");
			HashTree<String, RequestURIWrapper> tree = new HashTree<String, RequestURIWrapper>();
			HashMap<String, GroovyObjectEx<RequestIntercepter>> iMap = new HashMap<String, GroovyObjectEx<RequestIntercepter>>();
			
			Set<String> keys = annotatedClassMap.keySet();
			for (String key : keys) {
				Class<?> groovyClass = annotatedClassMap.get(key);
				RequestURI requestUri = null;
				GroovyObjectEx<GroovyServlet> groovyServlet = groovyRuntime
						.create(groovyClass);
				
//					Class<GroovyServlet> groovyClass = groovyServlet.getGroovyClass();
				if(groovyClass != null) {
					//Handle RequestIntercepting
					ControllerMapping requestIntercepting = groovyClass.getAnnotation(ControllerMapping.class);
					if(requestIntercepting != null) {
						String interceptClass = requestIntercepting.interceptClass();
						if(!StringUtils.isBlank(interceptClass)) {
							GroovyObjectEx<RequestIntercepter> groovyInterceptor = groovyRuntime
									.create(interceptClass);
							if(groovyInterceptor != null) {
								iMap.put(groovyServlet.getGroovyPath(), groovyInterceptor);
							}
						}
					}
					
					//Handle RequestMapping
					Method[] methods = groovyClass.getDeclaredMethods();
					if(methods != null) {
						for(Method method : methods) {
							if(Modifier.isPublic(method.getModifiers())) {
								if(method.isAnnotationPresent(RequestMapping.class)) {
									RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
									if(requestMapping != null) {
										requestUri = new RequestURI(requestMapping.uri(), requestMapping.method(), key, method.getName());
										
										RequestURIWrapper requestUriWrapper = new RequestURIWrapper(groovyServlet);
										requestUriWrapper.analyzeMethod(method);
										requestUriWrapper.setResponseType(requestMapping.responseType());
										handleRequestUri(key, requestUri, requestUriWrapper, tree, uriLogs);
//											requestUriWrapper.setGroovyObject(groovyServlet);
									}
								}
							}
						}
					}
				}
			}
			this.servletTree = tree;
			this.interceptorMap = iMap;
			uriLogs.append("---------------------------------------");
			LoggerEx.info(TAG, uriLogs.toString());
		}
	}

}
