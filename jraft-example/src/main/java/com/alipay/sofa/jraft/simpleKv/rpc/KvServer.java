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

import com.alipay.remoting.rpc.RpcServer;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;
import com.alipay.sofa.jraft.rpc.RaftRpcServerFactory;
import lombok.Data;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;

@Data
public class KvServer {

    private RaftGroupService raftGroupService;
    private Node             node;
    private KvStateMachine   fsm;

    public KvServer(final String dataPath, final String groupId, final PeerId serverId, final NodeOptions nodeOptions)
                                                                                                                      throws IOException {
        // 初始化路径
        FileUtils.forceMkdir(new File(dataPath));

        // 这里让 raft RPC 和业务 RPC 使用同一个 RPC server, 通常也可以分开
        final RpcServer rpcServer = new RpcServer(serverId.getPort());
        RaftRpcServerFactory.addRaftRequestProcessors(rpcServer);
        // 注册业务处理器
        KvServiceImpl service = new KvServiceImpl();

        rpcServer.registerUserProcessor(new RequestProcessor(service));
        // 初始化状态机
        this.fsm = new KvStateMachine();
        // 设置状态机到启动参数
        nodeOptions.setFsm(this.fsm);
        // 设置存储路径
        // 日志, 必须
        nodeOptions.setLogUri(dataPath + File.separator + "log");
        // 元信息, 必须
        nodeOptions.setRaftMetaUri(dataPath + File.separator + "raft_meta");
        // snapshot, 可选, 一般都推荐
        nodeOptions.setSnapshotUri(dataPath + File.separator + "snapshot");
        // 初始化 raft group 服务框架
        this.raftGroupService = new RaftGroupService(groupId, serverId, nodeOptions, rpcServer);
        // 启动
        this.node = this.raftGroupService.start();

        service.setNode(node);
        service.setExecutorService(Executors.newCachedThreadPool());
        service.setKvStateMachine(fsm);
    }

    public static void main(String[] args) throws IOException {
        int nodeNum = 3;
        args = new String[] { "/tmp/server"+nodeNum, "test", "127.0.0.1:808"+nodeNum,
                                "127.0.0.1:8081,127.0.0.1:8082,127.0.0.1:8083"};
        if (args.length != 4) {
            System.out
                .println("Useage : java com.alipay.sofa.jraft.example.counter.CounterServer {dataPath} {groupId} {serverId} {initConf}");
            System.out
                .println("Example: java com.alipay.sofa.jraft.example.counter.CounterServer /tmp/server1 counter 127.0.0.1:8081 127.0.0.1:8081,127.0.0.1:8082,127.0.0.1:8083");
            System.exit(1);
        }
        final String dataPath = args[0];
        final String groupId = args[1];
        final String serverIdStr = args[2];
        final String initConfStr = args[3];

        final NodeOptions nodeOptions = new NodeOptions();
        // 为了测试,调整 snapshot 间隔等参数
        // 设置选举超时时间为 1 秒
        nodeOptions.setElectionTimeoutMs(1000);
        // 关闭 CLI 服务。
        nodeOptions.setDisableCli(false);
        // 每隔30秒做一次 snapshot
        nodeOptions.setSnapshotIntervalSecs(30);
        // 解析参数
        final PeerId serverId = new PeerId();
        if (!serverId.parse(serverIdStr)) {
            throw new IllegalArgumentException("Fail to parse serverId:" + serverIdStr);
        }
        final Configuration initConf = new Configuration();
        // 解析并且添加设置的节点
        if (!initConf.parse(initConfStr)) {
            throw new IllegalArgumentException("Fail to parse initConf:" + initConfStr);
        }
        // 设置初始集群配置
        nodeOptions.setInitialConf(initConf);

        // 启动
        final KvServer kvServer = new KvServer(dataPath, groupId, serverId, nodeOptions);
        System.out.println("Started counter server at port:" + kvServer.getNode().getNodeId().getPeerId().getPort());
    }
}
