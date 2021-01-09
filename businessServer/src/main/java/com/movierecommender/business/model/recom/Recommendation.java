package com.movierecommender.business.model.recom;

/**
 * @创建人 陈灯顺
 * @创建日期 2020/12/5
 * @描述
 */

/**
 * 推荐项目的包装
 */
public class Recommendation {

    // 电影ID
    private int mid;

    // 电影的推荐得分
    private Double score;

    public Recommendation() {
    }

    public Recommendation(int mid, Double score) {
        this.mid = mid;
        this.score = score;
    }

    public int getMid() {
        return mid;
    }

    public void setMid(int mid) {
        this.mid = mid;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
