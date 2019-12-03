package com.alipay.sofa.jraft.test;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KvRequest {
    private Op op;

    private String key;

    private String val;
}
