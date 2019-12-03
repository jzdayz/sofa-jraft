package com.alipay.sofa.jraft.test;

import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.remoting.rpc.protocol.AsyncUserProcessor;

public class KvRequestProcessor extends AsyncUserProcessor<KvRequest> {
    @Override
    public void handleRequest(BizContext bizCtx, AsyncContext asyncCtx, KvRequest request) {

    }

    @Override
    public String interest() {
        return KvRequestProcessor.class.getName();
    }
}
