package com.walmartlabs.concord.plugins.http;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.IOException;
import java.util.Map;

import static com.walmartlabs.concord.plugins.http.HttpTask.HttpTaskConstant.*;

/**
 * Http task to support the direct http calls from concord.yml file. It uses the Apache HttpClient to
 * call the restful endpoints. This task is capable of storing the response in temporary file and returning the
 * response in string or JSON format.
 */
@Named("http")
public class HttpTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(HttpTask.class);

    private static final String DEFAULT_OUT_VAR = "response";

    @Override
    public void execute(Context ctx) throws Exception {
        Configuration config = Configuration.custom().build(ctx);

        setOutVariable(ctx, executeRequest(config));
    }

    /**
     * Method to get the resource from the given URL as a String.
     *
     * @param url resource URL
     * @return Response as {@link String}
     * @throws IOException exception
     */
    public String asString(String url) throws Exception {
        Configuration config = Configuration.custom().withUrl(url).build();

        return (String) executeRequest(config).get(CONTENT_KEY);
    }

    /**
     * Method to execute the request from the given configuration
     *
     * @param config {@link Configuration}
     * @return Map
     * @throws Exception exception
     */
    private Map<String, Object> executeRequest(Configuration config) throws Exception {
        log.info("Request method: {}", config.getMethodType());

        Map<String, Object> response = SimpleHttpClient.create(config).execute().getResponse();
        log.info("Success response: {}", response.get(SUCCESS_KEY));

        return response;
    }

    /**
     * Method to set the response in the output variable
     *
     * @param ctx            {@link Context}
     * @param returnResponse response returned from endpoint
     */
    private void setOutVariable(Context ctx, Map<String, Object> returnResponse) {
        String key = (String) ctx.getVariable(OUT_KEY);
        if (key == null) {
            key = DEFAULT_OUT_VAR;
        }
        ctx.setVariable(key, returnResponse);
    }

    public enum RequestMethodType {
        DELETE,
        GET,
        POST,
        PUT,
        PATCH;

        public static boolean isMember(String name) {
            for (RequestMethodType t : values()) {
                if (t.name().equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    public enum RequestType {
        JSON,
        FILE,
        STRING,
        FORM;

        public static boolean isMember(String aName) {
            RequestType[] requestTypes = RequestType.values();
            for (RequestType requestType : requestTypes) {
                if (requestType.name().equalsIgnoreCase(aName)) {
                    return true;
                }
            }
            return false;
        }
    }

    public enum ResponseType {
        JSON,
        FILE,
        STRING,
        ANY;

        public static boolean isMember(String aName) {
            ResponseType[] responseTypes = ResponseType.values();
            for (ResponseType responseType : responseTypes) {
                if (responseType.name().equalsIgnoreCase(aName)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Http Task Constant Class.
     */
    final class HttpTaskConstant {

        static final String AUTH_KEY = "auth";
        static final String BASIC_KEY = "basic";
        static final String BODY_KEY = "body";
        static final String CONNECT_TIMEOUT_KEY = "connectTimeout";
        static final String CONTENT_KEY = "content";
        static final String DEBUG_KEY = "debug";
        static final String HEADERS_KEY = "headers";
        static final String QUERY_KEY = "query";
        static final String IGNORE_ERRORS_KEY = "ignoreErrors";
        static final String METHOD_KEY = "method";
        static final String OUT_KEY = "out";
        static final String PASSWORD_KEY = "password"; // NOSONAR
        static final String PROXY_KEY = "proxy";
        static final String REQUEST_KEY = "request";
        static final String REQUEST_TIMEOUT_KEY = "requestTimeout";
        static final String REQUEST_POST_KEY = "POST";
        static final String REQUEST_PUT_KEY = "PUT";
        static final String RESPONSE_KEY = "response";
        static final String SOCKET_TIMEOUT_KEY = "socketTimeout";
        static final String SUCCESS_KEY = "success";
        static final String TOKEN_KEY = "token";
        static final String URL_KEY = "url";
        static final String USERNAME_KEY = "username";

        private HttpTaskConstant() {
        }
    }
}
