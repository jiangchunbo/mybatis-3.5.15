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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class DefaultParameterHandler implements ParameterHandler {

  private final TypeHandlerRegistry typeHandlerRegistry;

  private final MappedStatement mappedStatement;

  private final Object parameterObject;

  private final BoundSql boundSql;

  private final Configuration configuration;

  public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    this.mappedStatement = mappedStatement;
    this.configuration = mappedStatement.getConfiguration();
    this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();

    // 这个参数应该就是原生参数
    this.parameterObject = parameterObject;
    this.boundSql = boundSql;
  }

  @Override
  public Object getParameterObject() {
    return parameterObject;
  }

  /**
   * 设置参数，而且只设置 mode = IN 的
   *
   * @param ps PreparedStatement
   */
  @Override
  public void setParameters(PreparedStatement ps) {
    ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());

    // 从 BoundSql 中获取所有的 ParameterMapping，也就是那些 #{} 参数
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

    // 健壮判空
    if (parameterMappings != null) {
      MetaObject metaObject = null;
      for (int i = 0; i < parameterMappings.size(); i++) {
        // 获取 ParameterMapping
        ParameterMapping parameterMapping = parameterMappings.get(i);

        // 只关注 mode = IN 的
        if (parameterMapping.getMode() != ParameterMode.OUT) {
          Object value;

          // 获得属性名字。比如 #{id} 属性名字是 id
          String propertyName = parameterMapping.getProperty();

          // 首先从 additional 参数访问
          if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
            value = boundSql.getAdditionalParameter(propertyName);
          }
          // 如果没有传参，直接设置为 null
          else if (parameterObject == null) {
            value = null;
          }
          // 如果 TypeHandler 注册表能够处理这个参数类型，那么参数就是 parameterObject
          // >>> 这个情况非常少见，只有少
          else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            value = parameterObject;
          }
          // 使用反射从 parameterObject 获取
          // >>> 大部分情况可能都是走这里
          else {
            if (metaObject == null) {
              metaObject = configuration.newMetaObject(parameterObject);
            }
            value = metaObject.getValue(propertyName);
          }

          // 检查开发者有没有自己配置 TypeHandler
          // 如果没有配置，这里可能是 UnknownTypeHandler
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          JdbcType jdbcType = parameterMapping.getJdbcType();
          if (value == null && jdbcType == null) {
            jdbcType = configuration.getJdbcTypeForNull();
          }
          try {
            typeHandler.setParameter(ps, i + 1, value, jdbcType);
          } catch (TypeException | SQLException e) {
            throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
          }
        }
      }
    }
  }

}
