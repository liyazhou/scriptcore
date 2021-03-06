package connectors.mongodb.annotations.handlers;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;

import script.groovy.runtime.ClassAnnotationHandler;
import script.groovy.runtime.GroovyRuntime;
import script.groovy.runtime.GroovyRuntime.ClassHolder;
import script.groovy.runtime.GroovyRuntime.MyGroovyClassLoader;
import chat.errors.CoreException;
import chat.logs.LoggerEx;
import chat.utils.ClassFieldsHolder;
import chat.utils.ClassFieldsHolder.FieldEx;
import chat.utils.ClassFieldsHolder.FieldIdentifier;
import chat.utils.HashTree;

import com.mongodb.MongoClient;

import connectors.mongodb.MongoClientHelper;
import connectors.mongodb.annotations.DBCollection;
import connectors.mongodb.annotations.DBDocument;
import connectors.mongodb.annotations.Database;
import connectors.mongodb.annotations.DocumentField;
import connectors.mongodb.codec.BaseObjectCodecProvider;
import connectors.mongodb.codec.DataObject;
import connectors.mongodb.codec.DataObjectCodecProvider;

public class MongoDBHandler implements ClassAnnotationHandler{
	public static final String VALUE = "VALUE";
	public static final String CLASS = "CLASS";
	private static final String TAG = MongoDBHandler.class.getSimpleName();

	private HashMap<Class<?>, com.mongodb.client.MongoDatabase> databaseMap = new HashMap<>();
	private HashMap<Class<?>, CollectionHolder> collectionMap = new HashMap<>();
	private HashMap<Class<?>, ClassFieldsHolder> documentMap = new HashMap<>();
	
	private static MongoDBHandler instance;
	
	private MongoClientHelper mongoClientHelper;
	
	public static class CollectionHolder {
		private com.mongodb.client.MongoCollection<DataObject> collection;
		private HashTree<String, String> filters;
		public com.mongodb.client.MongoCollection<DataObject> getCollection() {
			return collection;
		}
		public void setCollection(com.mongodb.client.MongoCollection<DataObject> collection) {
			this.collection = collection;
		}
		public HashTree<String, String> getFilters() {
			return filters;
		}
		public void setFilters(HashTree<String, String> filters) {
			this.filters = filters;
		}
	}

	private MongoDBHandler() {
		instance = this;
	}
	
	public static MongoDBHandler getInstance() {
		return instance;
	}
	
	@Override
	public void handleAnnotatedClasses(Map<String, Class<?>> arg0,
			MyGroovyClassLoader arg1) {
		GroovyRuntime groovyRuntime = GroovyRuntime.getInstance();
		MongoDatabaseAnnotationHolder databaseHolder = MongoDatabaseAnnotationHolder.getInstance();
		MongoCollectionAnnotationHolder collectionHolder = MongoCollectionAnnotationHolder.getInstance();
		MongoDocumentAnnotationHolder documentHolder = MongoDocumentAnnotationHolder.getInstance();
		if(databaseHolder == null || collectionHolder == null || documentHolder == null) {
			LoggerEx.info(TAG, "Information is insufficient, databaseHolder = " + databaseHolder + ", collectionHolder = " + collectionHolder + ", documentHolder = " + documentHolder);
			return;
		}
		
		Map<Class<?>, Database> databaseMap = databaseHolder.getDbClassMap();
		Map<Class<?>, DBCollection> collectionMap = collectionHolder.getCollectionClassMap();
		Map<Class<?>, DBDocument> documentMap = documentHolder.getDocumentClassMap();
		if((databaseMap == null || databaseMap.isEmpty()) || (collectionMap == null || collectionMap.isEmpty()) || (documentMap == null || documentMap.isEmpty())) {
			LoggerEx.info(TAG, "Information is insufficient, databaseHolder = " + databaseMap + ", collectionMap = " + collectionMap + ", documentMap = " + documentMap);
			return;
		}
		
		try {
			mongoClientHelper.connect();
		} catch (CoreException e) {
			e.printStackTrace();
			LoggerEx.error(TAG, "Connect mongodb failed, " + mongoClientHelper.getHosts() + " error, " + e.getMessage());
		}
		
		HashMap<Class<?>, com.mongodb.client.MongoDatabase> newDatabaseMap = new HashMap<>();
		HashMap<Class<?>, CollectionHolder> newCollectionMap = new HashMap<>();
		HashMap<Class<?>, ClassFieldsHolder> newDocumentMap = new HashMap<>();
		
		Collection<Class<?>> databaseClasses = databaseMap.keySet();
		for(Class<?> databaseClass : databaseClasses) {
			Database mongoDatabase = databaseMap.get(databaseClass);
			if(mongoDatabase != null) {
				String dbName = mongoDatabase.name();
				if(dbName != null) {
					com.mongodb.client.MongoDatabase database = mongoClientHelper.getMongoDatabase(dbName);
					newDatabaseMap.put(databaseClass, database);
				}
			}
		}
		Collection<Class<?>> collectionClasses = collectionMap.keySet();
		for(Class<?> collectionClass : collectionClasses) {
			DBCollection mongoCollection = collectionMap.get(collectionClass);
			if(mongoCollection != null) {
				String collectionName = mongoCollection.name();
				String dClass = mongoCollection.databaseClass();
				Class<?> databaseClass = groovyRuntime.getClass(dClass);
				if(databaseClass == null)
					continue;
				if(collectionName != null && databaseClass != null) {
					com.mongodb.client.MongoDatabase database = newDatabaseMap.get(databaseClass);
					if(database != null) {
						CodecRegistry codecRegistry = CodecRegistries.fromRegistries(CodecRegistries.fromProviders(new DataObjectCodecProvider(collectionClass), new BaseObjectCodecProvider()), MongoClient.getDefaultCodecRegistry());
						
						com.mongodb.client.MongoCollection<DataObject> collection = database.getCollection(collectionName, DataObject.class).withCodecRegistry(codecRegistry);
						CollectionHolder cHolder = new CollectionHolder();
						cHolder.collection = collection;
						newCollectionMap.put(collectionClass, cHolder);
					}
				}
			}
		}
		Collection<Class<?>> documentClasses = documentMap.keySet();
		for(Class<?> documentClass : documentClasses) {
			DBDocument mongoDocument = documentMap.get(documentClass);
			if(mongoDocument != null) {
				String[] filters = mongoDocument.filters();
				Class<?> collectionClass = groovyRuntime.getClass(mongoDocument.collectionClass());
				CollectionHolder holder = null;
				if(collectionClass != null) {
					holder = newCollectionMap.get(collectionClass);
				}
				if(holder != null) {
					Object value = null;
					HashTree<String, String> tree = holder.filters;
					if(tree == null) {
						tree = new HashTree<>();
						holder.filters = tree;
					}
					for(int i = 0; i < filters.length; i++) {
						if(StringUtils.isBlank(filters[i])) 
							break;
						HashTree<String, String> children = null;
						if(i >= filters.length - 1) {
							//This is the last one in filter array. 
							value = filters[i];
							children = tree.getChildren(value.toString(), true);
						} else {
							children = tree.getChildren(filters[i], true);
						}
						tree = children;
					}
					tree.setParameter(CLASS, documentClass);
					tree.setParameter(VALUE, value);
					ClassFieldsHolder fieldHolder = new ClassFieldsHolder(documentClass, new MyFieldIdentifier());
//					tree.setParameter(FIELDS, fieldHolder);
					newDocumentMap.put(documentClass, fieldHolder);
				} else {
					ClassFieldsHolder fieldHolder = new ClassFieldsHolder(documentClass, new MyFieldIdentifier());
					newDocumentMap.put(documentClass, fieldHolder);
				}
			}
		}
		if(!newDatabaseMap.isEmpty() && !newCollectionMap.isEmpty()) {
			this.databaseMap = newDatabaseMap;
			this.collectionMap = newCollectionMap;
			this.documentMap = newDocumentMap;
		}
	}

	public class MyFieldIdentifier extends FieldIdentifier {
		@Override
		public String getFieldKey(Field field) {
			DocumentField documentField = field.getAnnotation(DocumentField.class);
			if(documentField != null) 
				return documentField.key();
			return null;
		}
		
		@Override
		public FieldEx field(Field field) {
			DocumentField documentField = field.getAnnotation(DocumentField.class);
			if(documentField != null) {
//				String key = documentField.key();
				String mapKey = documentField.mapKey();
				FieldEx fieldEx = new FieldEx(field);
				if(!StringUtils.isBlank(mapKey))
					fieldEx.put(MAPKEY, mapKey);
				return fieldEx;
			}
			return super.field(field);
		}
	}
	
	@Override
	public Class<? extends Annotation> handleAnnotationClass(GroovyRuntime groovyRuntime) {
		return null;
	}

	public HashMap<Class<?>, com.mongodb.client.MongoDatabase> getDatabaseMap() {
		return databaseMap;
	}

	public HashMap<Class<?>, CollectionHolder> getCollectionMap() {
		return collectionMap;
	}

	public MongoClientHelper getMongoClientHelper() {
		return mongoClientHelper;
	}

	public void setMongoClientHelper(MongoClientHelper mongoClientHelper) {
		this.mongoClientHelper = mongoClientHelper;
	}

	public HashMap<Class<?>, ClassFieldsHolder> getDocumentMap() {
		return documentMap;
	}

	public void setDocumentMap(HashMap<Class<?>, ClassFieldsHolder> documentMap) {
		this.documentMap = documentMap;
	}
}
