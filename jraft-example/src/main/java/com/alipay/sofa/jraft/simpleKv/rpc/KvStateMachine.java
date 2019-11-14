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
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.alipay.sofa.jraft.util.Utils;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counter state machine.
 *
 * @author boyan (boyan@alibaba-inc.com)
 *
 * 2018-Apr-09 4:52:31 PM
 */
@Data
public class KvStateMachine extends StateMachineAdapter {

    private static final Logger LOG        = LoggerFactory.getLogger(KvStateMachine.class);

    private Map<String, String> reSpo      = new ConcurrentHashMap<>();

    /**
     * Leader term
     */
    private final AtomicLong    leaderTerm = new AtomicLong(-1);

    public boolean isLeader() {
        return this.leaderTerm.get() > 0;
    }

    /**
     * Returns current value.
     */

    @Override
    public void onApply(final Iterator iter) {
        while (iter.hasNext()) {
            String current = "";
            Request request = null;
            KvClosure closure = null;
            if (iter.done() != null) {
                // This task is applied by this node, get value from closure to avoid additional parsing.
                closure = (KvClosure) iter.done();
                request = closure.getRequest();
            } else {
                // Have to parse FetchAddRequest from this user log.
                final ByteBuffer data = iter.getData();
                try {
                    request = SerializerManager.getSerializer(SerializerManager.Hessian2).deserialize(data.array(),
                        KvClosure.class.getName());
                } catch (final CodecException e) {
                    LOG.error("Fail to decode IncrementAndGetRequest", e);
                }
            }
            if (request != null) {
                switch (request.getOp()) {
                    case GET:
                        current = reSpo.get(request.getKey());
                        break;
                    case PUT:
                        reSpo.put(request.getKey(), request.getValue());
                        current = "put success";
                        break;
                }

                if (closure != null) {
                    closure.setResponse(new Response(true, current));
                    closure.run(Status.OK());
                }
            }
            iter.next();
        }
    }

    @Override
    public void onSnapshotSave(final SnapshotWriter writer, final Closure done) {
        Utils.runInThread(() -> {
            final KvSnapshotFile snapshot = new KvSnapshotFile(writer.getPath() + File.separator + "data");
            if (snapshot.save(this.reSpo)) {
                if (writer.addFile("data")) {
                    done.run(Status.OK());
                } else {
                    done.run(new Status(RaftError.EIO, "Fail to add file to writer"));
                }
            } else {
                done.run(new Status(RaftError.EIO, "Fail to save counter snapshot %s", snapshot.getPath()));
            }
        });
    }

    @Override
    public void onError(final RaftException e) {
        LOG.error("Raft error: %s", e, e);
    }

    @Override
    public boolean onSnapshotLoad(final SnapshotReader reader) {
        if (isLeader()) {
            LOG.warn("Leader is not supposed to load snapshot");
            return false;
        }
        if (reader.getFileMeta("data") == null) {
            LOG.error("Fail to find data file in {}", reader.getPath());
            return false;
        }
        final KvSnapshotFile snapshot = new KvSnapshotFile(reader.getPath() + File.separator + "data");
        try {
            reSpo = snapshot.load();
            return true;
        } catch (final IOException e) {
            LOG.error("Fail to load snapshot from {}", snapshot.getPath());
            return false;
        }

    }

    @Override
    public void onLeaderStart(final long term) {
        this.leaderTerm.set(term);
        super.onLeaderStart(term);

    }

    @Override
    public void onLeaderStop(final Status status) {
        this.leaderTerm.set(-1);
        super.onLeaderStop(status);
    }

}
