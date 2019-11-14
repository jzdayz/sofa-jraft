/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.jraft.simpleKv.rpc;

import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;
import com.alipay.sofa.jraft.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestProcessor extends AsyncUserProcessor<Request> {

    private static final Logger LOG = LoggerFactory.getLogger(RequestProcessor.class);

    private final KvService     service;

    public RequestProcessor(KvService service) {
        this.service = service;
    }

    @Override
    public void handleRequest(BizContext bizCtx, AsyncContext asyncCtx, Request request) {
        final KvClosure closure = new KvClosure() {
            @Override
            public void run(Status status) {
                asyncCtx.sendResponse(getResponse());
            }
        };
        switch (request.getOp()) {
            case GET: {
                service.get(request, closure);
                break;
            }
            case PUT: {
                service.put(request, closure);
                break;
            }
            default:
                throw new RuntimeException();
        }
    }

    @Override
    public String interest() {
        return Request.class.getName();
    }
}
