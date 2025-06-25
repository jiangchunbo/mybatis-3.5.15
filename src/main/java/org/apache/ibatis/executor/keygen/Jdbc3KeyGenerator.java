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
package org.apache.ibatis.executor.keygen;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ArrayUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSession.StrictMap;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.apache.ibatis.util.MapUtil;

/**
 * 用于给 Key 字段赋值
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

  private static final String SECOND_GENERIC_PARAM_NAME = ParamNameResolver.GENERIC_NAME_PREFIX + "2";

  /**
   * A shared instance.
   *
   * @since 3.4.3
   */
  public static final Jdbc3KeyGenerator INSTANCE = new Jdbc3KeyGenerator();

  private static final String MSG_TOO_MANY_KEYS = "Too many keys are generated. There are only %d target objects. "
    + "You either specified a wrong 'keyProperty' or encountered a driver bug like #1523.";

  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // do nothing
  }

  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    processBatch(ms, stmt, parameter);
  }

  public void processBatch(MappedStatement ms, Statement stmt, Object parameter) {
    // 获得 key 的属性，有可能多个，难道是这么设置的 key1,key2 ?
    final String[] keyProperties = ms.getKeyProperties();

    // 如果没有设置，算了
    if (keyProperties == null || keyProperties.length == 0) {
      return;
    }

    // 从 Statement 获得一个特殊的结果集 (这里面可以取出主键）
    try (ResultSet rs = stmt.getGeneratedKeys()) {
      // 元数据
      final ResultSetMetaData rsmd = rs.getMetaData();
      final Configuration configuration = ms.getConfiguration();

      // 如果数据库返回的列比较少，就不正常，没办法满足开发者定义的数据
      if (rsmd.getColumnCount() < keyProperties.length) {
        // Error?
      } else {
        // 赋值
        assignKeys(configuration, rs, rsmd, keyProperties, parameter);
      }
    } catch (Exception e) {
      throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
    }
  }

  @SuppressWarnings("unchecked")
  private void assignKeys(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd, String[] keyProperties,
                          Object parameter) throws SQLException {
    if (parameter instanceof ParamMap || parameter instanceof StrictMap) {
      // Multi-param or single param with @Param
      assignKeysToParamMap(configuration, rs, rsmd, keyProperties, (Map<String, ?>) parameter);
    } else if (parameter instanceof ArrayList && !((ArrayList<?>) parameter).isEmpty()
      && ((ArrayList<?>) parameter).get(0) instanceof ParamMap) {
      // Multi-param or single param with @Param in batch operation
      assignKeysToParamMapList(configuration, rs, rsmd, keyProperties, (ArrayList<ParamMap<?>>) parameter);
    } else {
      // Single param without @Param
      assignKeysToParam(configuration, rs, rsmd, keyProperties, parameter);
    }
  }

  private void assignKeysToParam(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
                                 String[] keyProperties, Object parameter) throws SQLException {
    Collection<?> params = collectionize(parameter);
    if (params.isEmpty()) {
      return;
    }
    List<KeyAssigner> assignerList = new ArrayList<>();
    for (int i = 0; i < keyProperties.length; i++) {
      assignerList.add(new KeyAssigner(configuration, rsmd, i + 1, null, keyProperties[i]));
    }
    Iterator<?> iterator = params.iterator();
    while (rs.next()) {
      if (!iterator.hasNext()) {
        throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, params.size()));
      }
      Object param = iterator.next();
      assignerList.forEach(x -> x.assign(rs, param));
    }
  }

  private void assignKeysToParamMapList(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
                                        String[] keyProperties, ArrayList<ParamMap<?>> paramMapList) throws SQLException {
    Iterator<ParamMap<?>> iterator = paramMapList.iterator();
    List<KeyAssigner> assignerList = new ArrayList<>();
    long counter = 0;
    while (rs.next()) {
      if (!iterator.hasNext()) {
        throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, counter));
      }
      ParamMap<?> paramMap = iterator.next();
      if (assignerList.isEmpty()) {
        for (int i = 0; i < keyProperties.length; i++) {
          assignerList
            .add(getAssignerForParamMap(configuration, rsmd, i + 1, paramMap, keyProperties[i], keyProperties, false)
              .getValue());
        }
      }
      assignerList.forEach(x -> x.assign(rs, paramMap));
      counter++;
    }
  }

  private void assignKeysToParamMap(Configuration configuration, ResultSet rs, ResultSetMetaData rsmd,
                                    String[] keyProperties, Map<String, ?> paramMap) throws SQLException {
    if (paramMap.isEmpty()) {
      return;
    }
    Map<String, Entry<Iterator<?>, List<KeyAssigner>>> assignerMap = new HashMap<>();
    for (int i = 0; i < keyProperties.length; i++) {
      Entry<String, KeyAssigner> entry = getAssignerForParamMap(configuration, rsmd, i + 1, paramMap, keyProperties[i],
        keyProperties, true);
      Entry<Iterator<?>, List<KeyAssigner>> iteratorPair = MapUtil.computeIfAbsent(assignerMap, entry.getKey(),
        k -> MapUtil.entry(collectionize(paramMap.get(k)).iterator(), new ArrayList<>()));
      iteratorPair.getValue().add(entry.getValue());
    }
    long counter = 0;
    while (rs.next()) {
      for (Entry<Iterator<?>, List<KeyAssigner>> pair : assignerMap.values()) {
        if (!pair.getKey().hasNext()) {
          throw new ExecutorException(String.format(MSG_TOO_MANY_KEYS, counter));
        }
        Object param = pair.getKey().next();
        pair.getValue().forEach(x -> x.assign(rs, param));
      }
      counter++;
    }
  }

  private Entry<String, KeyAssigner> getAssignerForParamMap(Configuration config, ResultSetMetaData rsmd,
                                                            int columnPosition, Map<String, ?> paramMap, String keyProperty, String[] keyProperties, boolean omitParamName) {
    Set<String> keySet = paramMap.keySet();
    // A caveat : if the only parameter has {@code @Param("param2")} on it,
    // it must be referenced with param name e.g. 'param2.x'.
    boolean singleParam = !keySet.contains(SECOND_GENERIC_PARAM_NAME);
    int firstDot = keyProperty.indexOf('.');
    if (firstDot == -1) {
      if (singleParam) {
        return getAssignerForSingleParam(config, rsmd, columnPosition, paramMap, keyProperty, omitParamName);
      }
      throw new ExecutorException("Could not determine which parameter to assign generated keys to. "
        + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
        + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
        + keySet);
    }
    String paramName = keyProperty.substring(0, firstDot);
    if (keySet.contains(paramName)) {
      String argParamName = omitParamName ? null : paramName;
      String argKeyProperty = keyProperty.substring(firstDot + 1);
      return MapUtil.entry(paramName, new KeyAssigner(config, rsmd, columnPosition, argParamName, argKeyProperty));
    }
    if (singleParam) {
      return getAssignerForSingleParam(config, rsmd, columnPosition, paramMap, keyProperty, omitParamName);
    } else {
      throw new ExecutorException("Could not find parameter '" + paramName + "'. "
        + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
        + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
        + keySet);
    }
  }

  private Entry<String, KeyAssigner> getAssignerForSingleParam(Configuration config, ResultSetMetaData rsmd,
                                                               int columnPosition, Map<String, ?> paramMap, String keyProperty, boolean omitParamName) {
    // Assume 'keyProperty' to be a property of the single param.
    String singleParamName = nameOfSingleParam(paramMap);
    String argParamName = omitParamName ? null : singleParamName;
    return MapUtil.entry(singleParamName, new KeyAssigner(config, rsmd, columnPosition, argParamName, keyProperty));
  }

  private static String nameOfSingleParam(Map<String, ?> paramMap) {
    // There is virtually one parameter, so any key works.
    return paramMap.keySet().iterator().next();
  }

  private static Collection<?> collectionize(Object param) {
    if (param instanceof Collection) {
      return (Collection<?>) param;
    }
    if (param instanceof Object[]) {
      return Arrays.asList((Object[]) param);
    } else {
      return Arrays.asList(param);
    }
  }

  private static class KeyAssigner {
    private final Configuration configuration;
    private final ResultSetMetaData rsmd;
    private final TypeHandlerRegistry typeHandlerRegistry;
    private final int columnPosition;
    private final String paramName;
    private final String propertyName;
    private TypeHandler<?> typeHandler;

    /**
     *
     * @param configuration  用于生成 MetaObject 反射操作器，以及在构造器里面获取 TypeHandlerRegistry
     * @param rsmd           JDBC 的规范，其中包含了结果集元数据
     * @param columnPosition 第几列
     * @param paramName      MyBatis 的参数名，可能是 arg0，或者是 null 之类
     * @param propertyName   Key 在 Java 字段中的属性名，一般通过 XML keyProperty 设置
     */
    protected KeyAssigner(Configuration configuration, ResultSetMetaData rsmd, int columnPosition, String paramName,
                          String propertyName) {
      this.configuration = configuration;
      this.rsmd = rsmd;
      this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      this.columnPosition = columnPosition;
      this.paramName = paramName;
      this.propertyName = propertyName;
    }

    protected void assign(ResultSet rs, Object param) {
      // paramName 如果有名字，一定被组织成了 Map 结构，直接从中取出
      // 除非只有 1 个参数，且没有设置 name，才没有名字
      if (paramName != null) {
        // If paramName is set, param is ParamMap
        param = ((ParamMap<?>) param).get(paramName);
      }

      // 获取对象操作器
      MetaObject metaParam = configuration.newMetaObject(param);
      try {
        // 如果没有指定 typeHandler，也找不到 setter，那就完蛋了，报错
        if (typeHandler == null) {
          if (!metaParam.hasSetter(propertyName)) {
            throw new ExecutorException("No setter found for the keyProperty '" + propertyName + "' in '"
              + metaParam.getOriginalObject().getClass().getName() + "'.");
          }

          // 获取 Setter 的类型
          Class<?> propertyType = metaParam.getSetterType(propertyName);

          // 获取 typeHandler
          typeHandler = typeHandlerRegistry.getTypeHandler(propertyType,
            // rsmd 还记得吗，元数据，获得第 columnPosition 列的类型，返回的是 JDBC 规定的 int 值
            // MyBatis 自己做了映射，使用 JdbcType.forCode 转换成 MyBatis 自己的 JdbcType
            JdbcType.forCode(rsmd.getColumnType(columnPosition)));
        }

        // 还是没有 typeHandler
        if (typeHandler == null) {
          // Error?
        } else {
          // 获得 typeHandler 转换的 result
          Object value = typeHandler.getResult(rs, columnPosition);
          metaParam.setValue(propertyName, value);
        }
      } catch (SQLException e) {
        throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e,
          e);
      }
    }
  }
}
