package com.movierecommender.business.model.request;
/**
 * 用户登陆请求
 */
public class LoginUserRequest {

    private String username;

    private String password;


    public LoginUserRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
