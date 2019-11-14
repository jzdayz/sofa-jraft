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

import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.entity.Task;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.util.BytesUtil;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

@Log4j2
@Data
public class KvServiceImpl implements KvService {

    ExecutorService executorService;
    Node            node;
    KvStateMachine  kvStateMachine;

    private boolean isLeader() {
        return kvStateMachine.isLeader();
    }

    private void applyOperation(Request request, final KvClosure closure) {
        if (!isLeader()) {
            handlerNotLeaderError(closure);
            return;
        }

        try {
            final Task task = new Task();
            task.setData(ByteBuffer
                .wrap(SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request)));
            task.setDone(closure);
            node.apply(task);
        } catch (CodecException e) {
            String errorMsg = "Fail to encode CounterOperation";
            log.error(errorMsg, e);
            closure.setResponse(new Response(true,""));
            closure.run(new Status(RaftError.EINTERNAL, errorMsg));
        }
    }

    private void handlerNotLeaderError(final KvClosure closure) {
        closure.setResponse(new Response(true,""));
        closure.run(new Status(RaftError.EPERM, "Not leader"));
    }

    @Override
    public void get(Request request, KvClosure closure) {
        this.node.readIndex(BytesUtil.EMPTY_BYTES, new ReadIndexClosure() {
            @Override
            public void run(Status status, long index, byte[] reqCtx) {
                if(status.isOk()){
                    closure.setResponse(
                            new Response(true,kvStateMachine.getReSpo().get(request.getKey()))
                    );
                    closure.run(Status.OK());
                    return;
                }
                executorService.execute(() -> {
                    if(isLeader()){
                        log.debug("Fail to get value with 'ReadIndex': {}, try to applying to the state machine.", status);
                        applyOperation(request, closure);
                    }else {
                        handlerNotLeaderError(closure);
                    }
                });
            }
        });
    }

    @Override
    public void put(Request request, KvClosure closure) {
        applyOperation(request, closure);
    }
}
