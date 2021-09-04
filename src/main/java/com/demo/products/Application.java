package com.demo.products;


import com.demo.products.classloader.MemoryClassLoader;
import com.demo.products.util.Global;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;


@SpringBootApplication
@EnableScheduling
public class Application {

  private static Logger logger = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) throws Exception {
    SpringApplication.run(Application.class, args);
    logger.info("服务已启动");
  }

  //模拟从远端配置文件拉去的代码
  static final String MULTIPLE_JAVA = "/* a single class to many files */   "
          + "package com.demo.products.service;                                           "
          + "import com.demo.products.model.DemoInfo;                                            "
          + "import com.demo.products.request.DemoParam;"
          + "public class InterDemo implements CommonService{                                       "
          + "    public Object execute(Object info) throws Exception {                  "
          + "    DemoParam param = (DemoParam)info;                            "
          + "    DemoInfo demInfo = new DemoInfo();                                "
          + "    demInfo.setResult(param.getDemoId().toString());                                      "
          + "    return info;                                  "
          + "    }                                                          "
          + "    }                               ";
  @Scheduled(fixedDelay = 60000)
  public void uploadApolloClass() {
    //如果满足条件，则开始读取远端配置
    //读取配置文件
    try {
      MemoryClassLoader loader = MemoryClassLoader.genInstance();
      loader.registerJava("InterDemo",MULTIPLE_JAVA);

    } catch (Exception e) {
      logger.error("update apollo interface err {}",e);
    }
  }
}
