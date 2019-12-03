package com.alipay.sofa.jraft.test;

import com.alipay.sofa.jraft.Closure;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class VkClosure implements Closure {

    private KvOperation kvOperation;

    private KvResponse response;

}
