package com.alipay.sofa.jraft.test;

public interface KvService {

    String get(String key);

    void put(String key,String val);

}
