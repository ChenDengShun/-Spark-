package com.movierecommender.recommender

import java.io.{File, PrintWriter}

import org.apache.hadoop.fs.Path

import scala.io.Source


/**
 * @创建人 陈灯顺
 * @创建日期 2020/12/14
 * @描述
 */
case class User1(uid:Int,username:String,password:String,first:Int,genres:List[String],timestamp:Long)
case class User2(uid:Int,username:String,password:String,first:Boolean,genres:List[String],timestamp:Long)
object UserExange {
  def main(args: Array[String]): Unit = {
    val path1="E:\\MovieRecommendSystem1\\recommender\\DataLoader\\src\\main\\resources\\users.csv"
    val path2="E:\\MovieRecommendSystem1\\recommender\\DataLoader\\src\\main\\resources\\users2.csv"
    val file1=Source.fromFile(path1)
    val file2=new PrintWriter(new File(path2))

    for(line <- file1.getLines){
      val words=line.split(",")
      val word1="user"+words(0);
      val lines=words(0)+","+word1+","+words(2)+","+words(3).toString+","+words(4)+","+words(5)

      file2.println(lines)
    }
    file1.close()
    file2.close()
  }
}
