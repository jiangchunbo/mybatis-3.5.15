/*
 *    Copyright 2009-2023 the original author or authors.
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.util.MapUtil;

/**
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

  /**
   * 被增强的 target 对象。可以 pass 给我们自己实现的拦截器，拿到这个对象。
   */
  private final Object target;

  /**
   * 这个 Plugin 背后的 interceptor
   */
  private final Interceptor interceptor;
  private final Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  public static Object wrap(Object target, Interceptor interceptor) {
    // 获取签名的映射，
    // 没仔细看，估计 key 是那几大可以切入的类型吧
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);

    // 获得 target 的类型，必须是那几大类型
    Class<?> type = target.getClass();

    // 取交集，获得哪些接口可以用
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);

    // 如果存在接口，那么就可以增强
    // 而且一下子可以增强 N 个
    // 得益于 Proxy 的接口增强可以多个
    // Proxy 里面的 handler h 属性使用 Plugin
    if (interfaces.length > 0) {
      return Proxy.newProxyInstance(type.getClassLoader(), interfaces, new Plugin(target, interceptor, signatureMap));
    }
    return target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // 反正两层过滤：过滤类；过滤方法

      // 过滤类：
      // 获得 method 声明的类，检查这个可以被插件的 map 里面有没有
      // 如果没有可能是调用的非接口方法，或者是其他接口的方法，反正不是 mybatis 四大接口
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());

      // 过滤方法：
      // 若命中了方法，那么这个方法就是开发者声明的，要切入的方法
      if (methods != null && methods.contains(method)) {
        // 使用 interceptor 切入执行
        return interceptor.intercept(new Invocation(target, method, args));
      }
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    // 获得 Intercepts 拦截器
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);

    // issue #251
    // 如果没有这个注解，那么错误
    if (interceptsAnnotation == null) {
      throw new PluginException(
          "No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
    }

    // @Intercepts 只有一个属性 values
    Signature[] sigs = interceptsAnnotation.value();

    //
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
    for (Signature sig : sigs) {
      // 得到一个容器，初次是空，但绝对不是 null
      Set<Method> methods = MapUtil.computeIfAbsent(signatureMap, sig.type(), k -> new HashSet<>());

      // 切入的 method 是以 name 方式指定的，因此这里用反射的方式，使用 methodName + args 尝试得到 Method
      try {
        Method method = sig.type().getMethod(sig.method(), sig.args());
        methods.add(method);
      } catch (NoSuchMethodException e) {
        // 找不到方法也很严重，说明用户写错了
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e,
            e);
      }
    }
    return signatureMap;
  }

  /**
   * 本质上就是取交集。type 实现了哪些接口，与 signatureMap 的 key set 取交集
   */
  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    // 汇总的容器
    Set<Class<?>> interfaces = new HashSet<>();

    // 双重循环
    // 遍历类的层次
    while (type != null) {
      // 对于每个层次，遍历每个实现的接口，记为 c
      for (Class<?> c : type.getInterfaces()) {
        // 如果这个接口 c 命中 signatureMap，其实就是我们写的插件上面注解声明的类型（那些类型一定是接口）
        if (signatureMap.containsKey(c)) {
          // 命中了，就把 c 放进去
          interfaces.add(c);
        }
      }
      type = type.getSuperclass();
    }
    return interfaces.toArray(new Class<?>[0]);
  }

}
