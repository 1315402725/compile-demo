package com.demo.products.service;
import com.demo.products.classloader.MemoryClassLoader;
import com.demo.products.constant.DemoConstant;
import com.demo.products.request.DemoParam;
import org.springframework.stereotype.Service;

/**
 * 首页接口服务
 */
@Service
public class DemoService {

    public Object getDemoInfo(DemoParam param) throws Exception {
        Class c = MemoryClassLoader.getInstance().loadClass(DemoConstant.DEMO_INTERFACE);
        Object oo = c.newInstance();
        Object object = ((CommonService)oo).execute(param);
        return object;
    }

}
