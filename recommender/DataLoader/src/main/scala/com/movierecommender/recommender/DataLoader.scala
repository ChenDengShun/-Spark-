package com.movierecommender.recommender

import java.net.InetAddress

import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.TransportAddress
import org.elasticsearch.transport.client.PreBuiltTransportClient

/**
 * @创建人 陈灯顺
 * @创建日期 2020/11/14
 * @描述
 */

//定义样例类
case class Movie(mid:Int,name:String,descri:String,timelong:String,issue:String,shoot:String
                ,language:String,genres:String,actors:String,directors:String)

case class Rating(uid:Int,mid:Int,score:Double,timestamp:Int)

case class Tag(uid:Int,mid:Int,tag:String,timestamp:Int)

case class User(uid:Int,username:String,password:String,first:Boolean,genres:List[String],timestamp:Long)

case class MongoConfig(uri:String,db:String)

case class ESConfig(httpHosts:String,transportHosts:String,index:String,clustername:String)

/**
 * 数据加载程序主体实现
 */
object DataLoader {
  //以windwo下为例，需替换成自己的路径，linux下为/YOUR_PATH/resources/movies.csv
  val MOVIE_DATA_PATH="recommender\\DataLoader\\src\\main\\resources\\movies.csv"
  val RATING_DATA_PATH="recommender\\DataLoader\\src\\main\\resources\\ratings.csv"
  val TAG_DATA_PATH="recommender\\DataLoader\\src\\main\\resources\\tags.csv"
  val USER_DATA_PATH="E:\\MovieRecommendSystem1\\recommender\\DataLoader\\src\\main\\resources\\users.csv"
  //E:\MovieRecommendSystem1\recommender\DataLoader\src\main\resources
  val MONGODB_MOVIE_COLLECTION="Movie"
  val MONGODB_RATING_COLLECTION="Rating"
  val MONGODB_TAG_COLLECTION="Tag"
  val MONGODB_USER_COLLECTION="User"
  val ES_MOVIE_INDEX="Movie"

  /**
   * 主程序的入口
   * @param args
   */
  def main(args: Array[String]): Unit = {

    //定义用到的配置参数
    val config=Map(
      "spark.cores"->"local[*]",
      "mongo.uri"->"mongodb://192.168.1.101:27017/recommender",
      "mongo.db"->"recommender",
      "es.httpHosts"->"192.168.1.101:9200",
      "es.transportHosts"->"192.168.1.101:9300",
      "es.index"->"recommender",
      "es.cluster.name"->"esCluster"
    )
    // 创建一个SparkConf配置
    val sparkConf=new SparkConf()
      .setAppName("DataLoader")
      .setMaster(config("spark.cores"))
      .set("spark.serializer","org.apache.spark.serializer.KryoSerializer")
      .set("spark.es.port","9200")
      .set("es.nodes.wan.only","true")
      .set("spark.es.nodes","192.168.1.101")
    // 创建一个SparkSession
    val spark=SparkSession.builder().config(sparkConf).getOrCreate()
    // 在对DataFrame和Dataset进行操作许多操作都需要这个包进行支持
    import spark.implicits._

    // 将Movie，Rating，Tag,User数据集加载进来
    val movieRDD=spark.sparkContext.textFile(MOVIE_DATA_PATH)

    //将MovieRDD转换为DataFrame
    val movieDF=movieRDD.map(item=>{
      val attr=item.split("\\^")
      Movie(attr(0).toInt,attr(1).trim,attr(2).trim,attr(3).trim,attr(4).trim,
          attr(5).trim,attr(6).trim,attr(7).trim,attr(8).trim,attr(9).trim)
    }).toDF()


    val userRDD=spark.sparkContext.textFile(USER_DATA_PATH)
    // 将UserRDD转换为DataFrame
    val userDF=userRDD.map(item=>{
      val attr=item.split(",")
      val tagCollection=attr(4).split("|").toList
      User(attr(0).toInt,attr(1).toString,attr(2).toString,attr(3).toBoolean,tagCollection,attr(5).toLong)
    }).toDF

    val ratingRDD=spark.sparkContext.textFile(RATING_DATA_PATH)

    // 将ratingRDD转换为DataFrame
    val ratingDF=ratingRDD.map(item=>{
      val attr=item.split(",")
      Rating(attr(0).toInt,attr(1).toInt,attr(2).toDouble,attr(3).toInt)
    }).toDF()

    val tagRDD=spark.sparkContext.textFile(TAG_DATA_PATH)
    // 将tagRDD转换为DataFrame
    val tagDF=tagRDD.map(item=>{
      val attr=item.split(",")
      Tag(attr(0).toInt,attr(1).toInt,attr(2).trim,attr(3).toInt)
    }).toDF()

    // 声明一个隐式的配置对象
    implicit val mongoConfig=MongoConfig(config.get("mongo.uri").get,config.get("mongo.db").get)

    // 将数据保存到MongoDB中
    storeDataInMongoDB(movieDF,ratingDF,tagDF,userDF)

    //数据预处理，把movie对应的tag信息添加进去，加一列tag1|tag2|tag3...
    import org.apache.spark.sql.functions._

    /**
     * mid,tags
     *
     * tags:tag1|tag2|tag3...
     */
    val newTag=tagDF.groupBy($"mid")
      .agg(concat_ws("|",collect_set($"tag")).as("tags"))
      .select("mid","tags")

    //需要将处理后的Tag数据，和Movie数据融合，产生新的Movie数据
    val movieWithTagsDF=movieDF.join(newTag,Seq("mid"),"left")

    //声明了一个ES配置的隐式参数
    implicit val esConfig=ESConfig(config.get("es.httpHosts").get,
      config.get("es.transportHosts").get,
      config.get("es.index").get,
      config.get("es.cluster.name").get)

    //需要将新的Movie数据保存到ES中
    storeDataInES(movieWithTagsDF)

    //关闭Spark
    spark.stop()
  }


  def storeDataInES(movieDF:DataFrame)(implicit eSConfig:ESConfig):Unit={
    //新建一个配置
    val settings:Settings=Settings.builder()
      .put("cluster.name",eSConfig.clustername)
      .build()
    //新建一个ES的客户端
    val esClient=new PreBuiltTransportClient(settings)
    //需要将TransportHosts添加到esClient中
    val REGEX_HOST_PORT="(.+):(\\d+)".r
    eSConfig.transportHosts.split(",").foreach{
      case REGEX_HOST_PORT(host:String,port:String)=>{
        esClient.addTransportAddress(
          new TransportAddress(InetAddress.getByName(host),port.toInt))
      }
    }
    //需要清除掉ES中遗留的数据
    if(esClient.admin().indices().exists(
      new IndicesExistsRequest(eSConfig.index))
    .actionGet().isExists){
      esClient.admin().indices().delete(new DeleteIndexRequest(eSConfig.index))
    }
    esClient.admin().indices().create(new CreateIndexRequest(eSConfig.index))
    //将数据写入到ES中
    movieDF
      .write
//      .option("spark.es.nodes",eSConfig.httpHosts)
      .option("spark.es.mapping.id","mid")
//      .option("es.nodes.wan.only","true")
      .mode("overwrite")
      .format("org.elasticsearch.spark.sql")
      .save(eSConfig.index+"/"+ES_MOVIE_INDEX)
  }
  /**
   * 将数据写入到MongoDB
   * @param movieDF
   * @param ratingDF
   * @param tagDF
   * @param mongoConfig
   */

  def storeDataInMongoDB(movieDF:DataFrame,ratingDF:DataFrame,tagDF:DataFrame,userDF:DataFrame)(implicit mongoConfig: MongoConfig): Unit ={

    //新建一个到MongoDB的连接
    val mongoClient=MongoClient(MongoClientURI(mongoConfig.uri))
    //如果MongoDB中有对应的数据库，那么应该删除
    mongoClient(mongoConfig.db)(MONGODB_MOVIE_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_USER_COLLECTION).dropCollection()
    //将当前数据写入到MongoDB
    movieDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_MOVIE_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    ratingDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATING_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    tagDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_TAG_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    userDF
        .write
        .option("uri",mongoConfig.uri)
        .option("collection",MONGODB_USER_COLLECTION)
        .mode("overwrite")
        .format("com.mongodb.spark.sql")
        .save()

    //对数据表建索引
    mongoClient(mongoConfig.db)(MONGODB_MOVIE_COLLECTION)
      .createIndex(MongoDBObject("mid"->1))
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION)
      .createIndex(MongoDBObject("uid"->1))
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION)
      .createIndex(MongoDBObject("mid"->1))
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION)
      .createIndex(MongoDBObject("uid"->1))
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION)
      .createIndex(MongoDBObject("mid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_USER_COLLECTION)
        .createIndex(MongoDBObject("uid"->1))
    mongoClient(mongoConfig.db)(MONGODB_USER_COLLECTION)
      .createIndex(MongoDBObject("username"->1))
    //关闭MongoDB的连接
    mongoClient.close()
  }
}
