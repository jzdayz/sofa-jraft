package com.alipay.sofa.jraft.test;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.util.BytesUtil;

public class KvOpCaller {

    private KvStateMachine kvStateMachine;

    private Node node;

    public void doRequest(KvRequest request,VkClosure kvClosure){

    }

    private void doGet(String key,VkClosure kvClosure){
        node.readIndex(BytesUtil.EMPTY_BYTES,new ReadIndexClosure(){
            @Override
            public void run(Status status, long index, byte[] reqCtx) {
                if (status.isOk()){
                    kvClosure.setResponse(
                            KvResponse.builder().code(200).val(kvStateMachine.get(key)).build()
                    );
                    kvClosure.run(status);
                }

            }
        });
    }

}
