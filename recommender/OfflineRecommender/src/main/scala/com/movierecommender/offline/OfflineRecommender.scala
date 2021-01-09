package com.movierecommender.offline

import org.apache.spark.SparkConf
import org.apache.spark.mllib.recommendation.{ALS, Rating}
import org.apache.spark.sql.SparkSession
import org.jblas.DoubleMatrix

/**
 * @创建人 陈灯顺
 * @创建日期 2020/12/2
 * @描述   项目采用 ALS 作为协同过滤算法，分别根据 MongoDB 中的用户评分表和电影
 *       数据集计算用户电影推荐矩阵以及电影相似度矩阵
 */

//基于评分数据的LFM，只需要rating数据
case class MovieRating(uid:Int,mid:Int,score:Double,timestamp:Int)
//MongoDB配置的uri和数据库名
case class MongoConfig(uri:String,db:String)
//标准推荐对象，mid，score
case class Recommendation(mid:Int,score:Double)
//用户推荐列表，定义基于预处理评分的用户推荐列表
case class UserRecs(uid:Int,recs:Seq[Recommendation])
//电影相似度（电影推荐）列表，定义基于LFM电影特征向量的电影相似度列表
case class MovieRecs(mid:Int,recs:Seq[Recommendation])
object OfflineRecommender {
  //定义常量
  val MONGODB_RATING_COLLECTION="Rating"

  //推荐表的名称
  val USER_RECS="UserRecs"
  val MOVIE_RECS="MovieRecs"
  val USER_MAX_RECOMENDATION=20

  def main(args: Array[String]): Unit = {
    //定义配置
    val config=Map(
      "spark.cores"->"local[*]",
      "mongo.uri"->"mongodb://192.168.1.101:27017/recommender",
      "mongo.db"->"recommender"
    )
    //创建spark session
    val sparkConf=new SparkConf().setMaster(config("spark.cores")).setAppName("StatisticsRecommender")
    val spark=SparkSession.builder().config(sparkConf).getOrCreate()
    implicit  val mongoConfig=MongoConfig(config("mongo.uri"),config("mongo.db"))
    import spark.implicits._
    //读取mongoDB中的业务数据
    val ratingRDD=spark
      .read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[MovieRating]
      .rdd
      .map(rating=>(rating.uid,rating.mid,rating.score))
      .cache()

    //用户的数据集RDD[Int]
    val userRDD=ratingRDD.map(_._1).distinct()
    //电影数据集RDD[Int]
    val movieRDD=ratingRDD.map(_._2).distinct()

    //创建训练隐语义数据集,(用户，电影，评分）
    val trainData=ratingRDD.map(x=>Rating(x._1,x._2,x._3))

    //rank是模型中隐语义因子的个数，iterations是迭代的次数，lambda是ALS的正则化参数
    val (rank,iterations,lambda)=(50,5,0.01)

    //调用ALS算法训练隐语义模型
    val model=ALS.train(trainData,rank,iterations,lambda)

    //TODO 计算用户推荐矩阵
    //1. UserId 和 MovieID 做笛卡尔积，产生（uid，mid）的元组
    val userMovies=userRDD.cartesian(movieRDD)

    //2. 通过模型预测（uid，mid）的元组。
    //model已训练好，把id传进去就可以得到预测评分列表RDD[Rating](uid,mid,rating)
    val preRatings=model.predict(userMovies)

    //3. 将预测结果通过预测分值进行排序。
    //4. 返回分值最大的 K 个电影，作为当前用户的推荐。
    val userRecs=preRatings
      .filter(_.rating>0)
      .map(rating=>(rating.user,(rating.product,rating.rating)))
      .groupByKey()
      .map{
        case (uid,recs)=>UserRecs(uid,recs.toList.sortWith(_._2>_._2).take(USER_MAX_RECOMENDATION)
          .map(x=>Recommendation(x._1,x._2)))
      }.toDF()

    userRecs.write
      .option("uri",mongoConfig.uri)
      .option("collection",USER_RECS)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()



    //TODO：计算电影相似度矩阵

    /**
     *    通过 ALS 计算电影见相似度矩阵，该矩阵用于查询当前电影的相似电影并为实
     * 时推荐系统服务。
     *    离线计算的 ALS 算法，算法最终会为用户、电影分别生成最终的特征矩阵，
     * 分别是表示用户特征矩阵的 U(m x k)矩阵，每个用户由 k 个特征描述；表示物品特
     * 征矩阵的 V(n x k)矩阵，每个物品也由 k 个特征描述。
     */
    //基于电影的隐特征，计算电影相似度矩阵
    //获取电影的特征矩阵，数据格式RDD[(scala.Int,scala.Array[scala.Double])]

    //spark.mllib.recommendation.MatrixFactorizationModel.productFeatures:返回一个包含元组的 RDD，
    // 每个元组第一个元素是物品，第二个元素是和这个物品相关的物品列表
    val movieFeatures=model.productFeatures.map{case (mid,features)=>
      (mid,new DoubleMatrix(features))}
    //计算笛卡尔积并过滤合并
    val movieRecs=movieFeatures.cartesian(movieFeatures)
      .filter{case (a,b)=>a._1!=b._1}
      .map{
        case (a,b)=>
          val simScore=this.consinSim(a._2,b._2)  //求余弦相似度
          (a._1,(b._1,simScore))
      }.filter(_._2._2>0.6)
      .groupByKey()
      .map{
        case (mid,items)=>MovieRecs(mid,items.toList.map(x=>Recommendation(x._1,x._2)))
      }.toDF()

    movieRecs
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MOVIE_RECS)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //关闭spark
    spark.stop()
  }
  //计算两个电影之间的余弦相似度
  def consinSim(movie1:DoubleMatrix,movie2:DoubleMatrix):Double={
    movie1.dot(movie2)/(movie1.norm2()*movie2.norm2())
  }
}
