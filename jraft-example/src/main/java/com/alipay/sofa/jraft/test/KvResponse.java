package com.alipay.sofa.jraft.test;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KvResponse {
    private int code;
    private String val;
}
