package com.demo.products.service;
import com.demo.products.model.DemoInfo;
import com.demo.products.request.DemoParam;

public class InterDemoTest implements CommonService{
    @Override
    public Object execute(Object info) throws Exception {
        DemoParam param = (DemoParam)info;
        DemoInfo demInfo = new DemoInfo();
        demInfo.setResult(param.getDemoId().toString());
        return info;
    }
}
