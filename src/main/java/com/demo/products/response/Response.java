package com.demo.products.response;

/**
 * Created by dd on 2018/1/2.
 */
public class Response {

  private int errcode = 0;
  private String msg;
  private Object data;

  public Response(int errcode, String msg, Object data) {
    this.errcode = errcode;
    this.msg = msg;
    this.data = data;
  }

  public int getErrcode() {
    return errcode;
  }

  public void setErrcode(int errcode) {
    this.errcode = errcode;
  }

  public String getMsg() {
    return msg;
  }

  public void setMsg(String msg) {
    this.msg = msg;
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "Response{"
        + "errcode=" + errcode
        + ", msg='" + msg + '\''
        + ", data=" + data
        + '}';
  }
}
