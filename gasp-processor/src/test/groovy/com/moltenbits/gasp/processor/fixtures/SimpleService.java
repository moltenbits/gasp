package com.moltenbits.gasp.processor.fixtures;

import com.moltenbits.gasp.annotation.GraphQLApi;
import com.moltenbits.gasp.annotation.GraphQLArgument;
import com.moltenbits.gasp.annotation.GraphQLMutation;
import com.moltenbits.gasp.annotation.GraphQLQuery;

@GraphQLApi
public class SimpleService {

    @GraphQLQuery
    public String hello() {
        return "world";
    }

    @GraphQLQuery
    public String greet(@GraphQLArgument(name = "name") String name) {
        return "Hello, " + name + "!";
    }

    @GraphQLMutation
    public String setMessage(@GraphQLArgument(name = "msg") String msg) {
        return msg;
    }
}
