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
   * 所有拦截器
   */
  private final List<Interceptor> interceptors = new ArrayList<>();

  /**
   * 这个方法会由其他可以 AOP 切入的类进行调用
   *
   * @param target 准备植入插件的对象
   */
  public Object pluginAll(Object target) {
    // 遍历每个拦截器
    for (Interceptor interceptor : interceptors) {
      // 植入插件，得到一个新的对象
      // 应该是一个代理对象
      // 这儿的 plugin 其实是个接口 default 方法。直接调用了 Plugin.wrap 方法
      target = interceptor.plugin(target);
    }
    return target;
  }

  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }

  public List<Interceptor> getInterceptors() {
    return Collections.unmodifiableList(interceptors);
  }

}
