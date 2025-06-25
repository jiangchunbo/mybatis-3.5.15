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
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

public class ParamNameResolver {

  public static final String GENERIC_NAME_PREFIX = "param";

  private final boolean useActualParamName;

  /**
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified, the parameter index is
   * used. Note that this index could be different from the actual index when the method has special parameters (i.e.
   * {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   * <p>
   * 这个 map 最终存储的是什么呢，key 是索引 index，value 是参数名
   */
  private final SortedMap<Integer, String> names;

  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    // 获得是否使用实际的参数名
    this.useActualParamName = config.isUseActualParamName();

    // 获得参数类型列表
    final Class<?>[] paramTypes = method.getParameterTypes();

    // 获得方法参数注解，第一维是方法参数，第二维是注解
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();

    // 获得 map
    final SortedMap<Integer, String> map = new TreeMap<>();

    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 是否是特殊参数，如果是 RowBounds 或者 ResultHandler 那么不用处理
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }

      // 遍历注解，其实有啥能遍历的呢？不就是关注 @Param
      String name = null;
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          // 记录一下，参数里面存在 @Param，这个标记只对单个参数有用
          hasParamAnnotation = true;
          name = ((Param) annotation).value();
          break;
        }
      }

      // 如果这里还是 null，说明就没有注解
      // 有没有可能是一个无效注解比如 @Param ？不可能！因为 Java 注解的规范是，如果注解的字段没有 default 值，则必须提供一个值，否则编译都无法通过
      if (name == null) {
        // @Param was not specified.
        if (useActualParamName) {
          // 使用来自反射得到的参数名，如果没有使用 Java 8 + -parameter 编译，则这里只能拿到 arg0
          name = getActualParamName(method, paramIndex);
        }

        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          // 如果还是没有名字，只能使用 0 1 2 这种作为名字了
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name);
    }
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   *
   * @return the names
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * <p>
   * A single non-special parameter is returned without a name. Multiple parameters are named using the naming rule. In
   * addition to the default names, this method also adds the generic names (param1, param2, ...).
   * </p>
   * <p>
   * 经过这个方法处理之后，返回值要不就是 null；要不就是非 Collection 非 Array 的对象；要不就是 ParamMap
   *
   * @param args the args
   * @return the named params
   */
  public Object getNamedParams(Object[] args) {
    // 没有参数，就返回 null
    final int paramCount = names.size();
    if (args == null || paramCount == 0) {
      return null;
    }

    // 参数只有 1 个，而且没有注解
    if (!hasParamAnnotation && paramCount == 1) {
      Object value = args[names.firstKey()];
      // 单个参数。如果是 Collection 或者 Array，那么就包装为 ParamMap
      return wrapToMapIfCollection(value, useActualParamName ? names.get(names.firstKey()) : null);
    }

    // 其他情况，要不就是有注解，要不就是多个方法参数
    else {
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;

      // 遍历所有的名称，key 是 索引 (0,1,2,...) value 就是参数的名字
      for (Map.Entry<Integer, String> entry : names.entrySet()) {

        // 颠三倒四
        // key 是参数名, value 是参数值
        param.put(entry.getValue(), args[entry.getKey()]);

        // add generic param names (param1, param2, ...)
        // 添加一些通用的参数名 param1 param2 --> 这种参数名有什么用，可以帮助 mybatis 判断目前有几个参数，比如 Jdbc3KeyGenerator
        // 但是，我觉得你甚至可以自己使用这种命名，误导 mybatis
        final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);

        // ensure not to overwrite parameter named with @Param
        // 如果之前解析出来没有
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }

  /**
   * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
   *
   * @param object          a parameter object
   * @param actualParamName an actual parameter name (If specify a name, set an object to {@link ParamMap} with specified name)
   * @return a {@link ParamMap}
   * @since 3.5.5
   */
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    // 看方法名就知道，如果是 collection 就包装成 wrap
    // 不过这个方法处理的更多，collection 不仅仅是 Java 的集合，如果是 Array 也包装起来

    // 处理 Collection
    if (object instanceof Collection) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("collection", object);

      // 如果刚巧还是个 List，也添加一个 list
      if (object instanceof List) {
        map.put("list", object);
      }
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }

    // 如果是数组，那么添加一个 array
    if (object != null && object.getClass().isArray()) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("array", object);
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }

    // 都不是，直接返回
    return object;
  }

}
