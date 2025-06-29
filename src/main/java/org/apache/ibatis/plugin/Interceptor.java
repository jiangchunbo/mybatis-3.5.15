/*
 *    Copyright 2009-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * MyBatis 插件机制的核心类，所以插件就是拦截器。
 * <p>
 * 这个类的设计跟 Spring AOP 的 MethodInterceptor 相似
 *
 * @author Clinton Begin
 */
public interface Interceptor {

  /**
   * 这其实是一种 Around Advice
   */
  Object intercept(Invocation invocation) throws Throwable;

  /**
   * 编织
   */
  default Object plugin(Object target) {
    // 一个静态方法，意思就是用 this 拦截器，去增强 target 对象
    // 与 Spring 的思想差不多，target 就是被增强的对象
    return Plugin.wrap(target, this);
  }

  default void setProperties(Properties properties) {
    // NOP
  }

}
