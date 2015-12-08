package com.johnfonte.blupoint.api;

import java.util.List;
import com.johnfonte.blupoint.object.Person;
import com.johnfonte.blupoint.object.Report;
import com.johnfonte.blupoint.object.Token;
import com.squareup.okhttp.ResponseBody;

import retrofit.Call;
import retrofit.Response;
import retrofit.http.*;

public interface BluPointWeb {
    @GET("/")
    Call<String> status();

    @GET("/ids/")
    Call<List<String>> all_active_ids();

    @GET("/person/{person_id}")
    Call<Person> get_person_by_id( @Path (value = "person_id")int person_id );

    @POST("/signup/{name}")
    Call<Token> signup( @Path (value = "name")String name );

    @PUT("/report/id/{person_id}")
    Call<ResponseBody> report( @Body Report report, @Path (value = "person_id") Integer person_id );
}
