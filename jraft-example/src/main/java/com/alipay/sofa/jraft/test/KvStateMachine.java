package com.alipay.sofa.jraft.test;

import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.example.counter.CounterOperation;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.ConcurrentHashMap;

/**
 *  没有快照功能
 */
@Log4j2
public class KvStateMachine extends StateMachineAdapter implements KvService {

    private ConcurrentHashMap<String,String> data = new ConcurrentHashMap<>();

    @Override
    public void onApply(Iterator iter) {

        while (iter.hasNext()){

            VkClosure done = null;
            KvOperation kvOperation = null;
            // 说明是leader，是自己发起的，所以可以直接获取Closure对象
            if (iter.done() != null) {
                done = (VkClosure) iter.done();
                kvOperation = done.getKvOperation();
            }else{
                try {
                    kvOperation = SerializerManager.getSerializer(SerializerManager.Hessian2).deserialize(
                            iter.getData().array(), CounterOperation.class.getName());
                } catch (CodecException e) {
                    log.error("Fail to decode kv", e);
                }
            }

            if (kvOperation!=null){
                String res = null;
                switch (kvOperation.getOp()){
                    case GET:
                        res = data.get(kvOperation.getKey());
                        break;
                    case PUT:
                        data.put(kvOperation.getKey(),kvOperation.getVal());
                        break;
                    default:
                        log.error(" unknown request ");
                        break;
                }
                if (done!=null){
                    done.setResponse(KvResponse.builder().code(200).val(res).build());
                    done.run(Status.OK());
                }
            }

            iter.next();
        }

    }

    @Override
    public String get(String key) {
        return data.get(key);
    }

    @Override
    public void put(String key, String val) {
        data.put(key,val);
    }
}
