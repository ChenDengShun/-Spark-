package com.movierecommender.business.service;

import com.movierecommender.business.model.domain.User;
import com.movierecommender.business.model.request.LoginUserRequest;
import com.movierecommender.business.model.request.RegisterUserRequest;
import com.movierecommender.business.utils.Constant;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.util.JSON;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class UserService {

    @Autowired
    private MongoClient mongoClient;

    @Autowired
    private ObjectMapper objectMapper;

    private MongoCollection<Document> userCollection;
    //获取MongDB中的用户表
    private MongoCollection<Document> getUserCollection(){
        if(null == userCollection) {
            userCollection = mongoClient.getDatabase(Constant.MONGODB_DATABASE).getCollection(Constant.MONGODB_USER_COLLECTION);
        }
        System.out.println(userCollection.find());
        return userCollection;
    }
    //注册
    public boolean registerUser(RegisterUserRequest request){
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setFirst(true);
        user.setTimestamp(System.currentTimeMillis());
        try{
            getUserCollection().insertOne(Document.parse(objectMapper.writeValueAsString(user)));
            return true;
        }catch (JsonProcessingException e){
            e.printStackTrace();
            return false;
        }
    }
    //登陆
    public User loginUser(LoginUserRequest request){
        //1. 根据用户的输入的用户名，
        User user = findByUsername(request.getUsername());
        if(null == user) {
            return null;
        }else if(!user.passwordMatch(request.getPassword())){
            return null;
        }
        return user;
    }

    private User documentToUser(Document document){
        try{
            return objectMapper.readValue(JSON.serialize(document),User.class);
        } catch (JsonParseException e) {
            e.printStackTrace();
            return null;
        } catch (JsonMappingException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean checkUserExist(String username){
        return null != findByUsername(username);
    }

    public User findByUsername(String username){
        //1. 从 用户表中 查看是否有用户名为 username，拿出第一个用户名为username
        Document user = getUserCollection().find(new Document("username",username)).first();
        //2. 如果 没有用户名为 "username变量" 的用户，那么返回null
        if(null == user || user.isEmpty()) {
            return null;
        }
        //3. 否则返回 用户名为 username变量 的 User对象
        return documentToUser(user);
    }

    public boolean updateUser(User user){
        getUserCollection().updateOne(Filters.eq("uid", user.getUid()), new Document().append("$set",new Document("first", user.isFirst())));
        getUserCollection().updateOne(Filters.eq("uid", user.getUid()), new Document().append("$set",new Document("prefGenres", user.getPrefGenres())));
        return true;
    }

    public User findByUID(int uid){
        Document user = getUserCollection().find(new Document("uid",uid)).first();
        if(null == user || user.isEmpty()) {
            return null;
        }
        return documentToUser(user);
    }

    public void removeUser(String username){
        getUserCollection().deleteOne(new Document("username",username));
    }

}
