package com.nextechar.holograms;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HologramsServer {

    public String username;
    public String password;

    private Boolean connected = false;
    private String token;

    public HologramsServer(String username, String password)
    {
        this.username = username;
        this.password = password;
    }

    public Boolean login(String username, String password)
    {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("application/json");
        String payload = "{\r\n\"username\":\"" + username
                + "\",\r\n\"password\":\"" + password + "\"\r\n}";
        RequestBody body = RequestBody.create(mediaType, payload);
        Request request = new Request.Builder()
                .url("https://holograms-admin.nextechar.com/holox/v1/auth/login")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        try {
            Response response = client.newCall(request).execute();
            byte[] responseBytes = response.body().bytes();
            String responseString = new String(responseBytes, StandardCharsets.UTF_8);
            JSONObject responseObject = new JSONObject(responseString);
            token = responseObject.getString("authorization");
            connected = true;
            return true;
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public JSONObject getUserData(String username)
    {
        if (!connected) { return null; }

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        Request request = new Request.Builder()
                .url("https://holograms-admin.nextechar.com/holox/v1/auth/fetch/username/" + username)
                .method("GET", null)
                .addHeader("Authorization", token)
                .build();
        try {
            Response response = client.newCall(request).execute();
            byte[] responseBytes = response.body().bytes();
            String responseString = new String(responseBytes, StandardCharsets.UTF_8);
            JSONObject responseObject = new JSONObject(responseString);
            return responseObject;
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getUserProfileImage(String username)
    {
        try {
            JSONObject userData = getUserData(username);
            JSONArray profileImgArray = userData.getJSONArray("profileimg");
            JSONObject profileImgObject = profileImgArray.getJSONObject(0);
            String fileURL = profileImgObject.getString("filepath");
            return fileURL;
        } catch (JSONException e){
            e.printStackTrace();
            return null;
        }
    }

}
