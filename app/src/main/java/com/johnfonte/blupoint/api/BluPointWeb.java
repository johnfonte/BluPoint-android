package com.johnfonte.blupoint.api;

import java.util.List;
import com.johnfonte.blupoint.object.Person;
import com.johnfonte.blupoint.object.Token;

import retrofit.Call;
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
    Call<String> report( @Path (value = "person_id")int person_id );
}
