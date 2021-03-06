package common.distribution

import chat.errors.CoreException
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoCursor
import com.mongodb.client.result.DeleteResult
import connectors.mongodb.MongoCollectionHelper
import connectors.mongodb.annotations.DBCollection
import connectors.mongodb.codec.DataObject
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import script.groovy.annotation.Bean

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@DBCollection(name = "article", databaseClass = ArticleDatabase.class)
@Bean
class ArticleService extends MongoCollectionHelper {
	public static final int ERRORCODE_ARTICLE_DELETE_FAILED = 600;
	private Article findArticle(Bson query) {
		MongoCollection<Article> collection = this.getMongoCollection();
		FindIterable<Article> iterable = collection.find(query);
		if(iterable != null) {
			MongoCursor<Article> cursor = iterable.iterator();
			if(cursor.hasNext()) {
				return cursor.next();
			}
		}
		return null;
	}
	private List<Article> findArticles(Bson query, Integer offset, Integer limit, AtomicLong total) {
		MongoCollection<Article> collection = this.getMongoCollection();
		FindIterable<Article> iterable = collection.find(query);
		if(iterable != null) {
			if(offset != null)
				iterable.skip(offset);
			if(limit != null)
				iterable.limit(limit);
			iterable.sort(new Document().append(Article.FIELD_CREATETIME, -1));

			MongoCursor<Article> cursor = iterable.iterator();
			if(total != null) {
				long count = collection.count(query);
				total.set(count);
			}
			List<Article> list = new ArrayList<>();
			while(cursor.hasNext()) {
				list.add(cursor.next());
			}
			return list;
		}
		return null;
	}

	public Article getArticle(String id) {
		return findArticle(new Document().append("_id", id));
	}

	public void addArticle(Article article) {
		if(article.getId() == null)
			article.setId(ObjectId.get().toString());
		article.setCreateTime(System.currentTimeMillis());
		article.setUpdateTime(article.getCreateTime());
		MongoCollection<Article> collection = this.getMongoCollection();
		collection.insertOne(article);
	}

	public void deleteArticle(String id) {
		MongoCollection<Article> collection = this.getMongoCollection();
		DeleteResult result = collection.deleteOne(new Document().append(DataObject.FIELD_ID, id));
		if(result.deletedCount == 0)
			throw new CoreException(ERRORCODE_ARTICLE_DELETE_FAILED, "Delete article " + id + " failed");
	}

	public List<Article> queryArticles(String companyId, String authorUserId, Integer offset, Integer limit, AtomicLong total) {
		Document query = new Document();
		if(companyId != null) {
			query.append(Article.FIELD_COMPANYID, companyId);
		}
		if(authorUserId != null) {
			query.append(Article.FIELD_USERID, authorUserId);
		}
		return findArticles(query, offset, limit, total);
	}

}
