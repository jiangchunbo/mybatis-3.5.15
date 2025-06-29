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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Clinton Begin
 */
public class InterceptorChain {

  /**
   * 所有注册的拦截器
   */
  private final List<Interceptor> interceptors = new ArrayList<>();

  /**
   * 这个方法会由其他可以 AOP 切入的类进行调用
   *
   * @param target 被代理的对象
   */
  public Object pluginAll(Object target) {
    // 遍历每个拦截器
    for (Interceptor interceptor : interceptors) {
      // 编织，可能得到一个新对象
      // 如果这个拦截器不支持编织这个对象，那么就返回原始对象
      target = interceptor.plugin(target);
    }
    return target;
  }

  /**
   * 这个方法一般就是被 Configuration 调用
   */
  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }

  public List<Interceptor> getInterceptors() {
    return Collections.unmodifiableList(interceptors);
  }

}
