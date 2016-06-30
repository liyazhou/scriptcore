package connectors.mongodb.annotations.handlers;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import script.groovy.runtime.ClassAnnotationHandler;
import script.groovy.runtime.GroovyRuntime.MyGroovyClassLoader;
import connectors.mongodb.annotations.MongoDatabase;

public class MongoDatabaseAnnotationHolder extends ClassAnnotationHandler {
	private static final String TAG = MongoDatabaseAnnotationHolder.class.getSimpleName();

	private Map<Class<?>, MongoDatabase> dbClassMap = new LinkedHashMap<>();
	
	private static MongoDatabaseAnnotationHolder instance;
	public MongoDatabaseAnnotationHolder() {
		instance = this;
	}
	
	public static MongoDatabaseAnnotationHolder getInstance() {
		return instance;
	}
	
	public void init() {
	}
	
	@Override
	public Class<? extends Annotation> handleAnnotationClass() {
		return MongoDatabase.class;
	}

	@Override
	public void handleAnnotatedClasses(Map<String, Class<?>> annotatedClassMap,
			MyGroovyClassLoader classLoader) {
		if(annotatedClassMap != null) {
			Collection<Class<?>> values = annotatedClassMap.values();
			for(Class<?> groovyClass : values) {
				MongoDatabase mongoDatabase = groovyClass.getAnnotation(MongoDatabase.class);
				dbClassMap.put(groovyClass, mongoDatabase);
			}
		}
	}

	public Map<Class<?>, MongoDatabase> getDbClassMap() {
		return dbClassMap;
	}

}
