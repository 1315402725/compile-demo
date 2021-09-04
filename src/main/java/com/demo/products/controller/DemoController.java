package com.demo.products.controller;

import com.demo.products.request.DemoParam;
import com.demo.products.response.Response;
import com.demo.products.service.DemoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Created by dd on 2018/1/2.
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

  @Resource
  private DemoService demoService;

  @PostMapping("/get_demo_info")
  public Response GetUserBasic(@Validated @RequestBody DemoParam param) throws Exception {
    Object demoInfo = demoService.getDemoInfo(param);
    return new Response(0,"success",demoInfo);
  }


}
