package com.jiangchunbo.mybatis;

import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

public class TestDemo {

  public static void main(String[] args) {
    TypeHandlerRegistry typeHandlerRegistry = new TypeHandlerRegistry();

    TypeHandler<String> typeHandler = typeHandlerRegistry.getTypeHandler(String.class);
    System.out.println(typeHandler.getClass());
  }

}
