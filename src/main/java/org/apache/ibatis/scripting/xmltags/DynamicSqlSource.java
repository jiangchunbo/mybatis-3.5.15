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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  /**
   * 传递一个被 mybatis wrap 过的参数，得到 BoundSql
   *
   * @param parameterObject 获得参数对象，基本就是 paramMap；集合；单个参数
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 创建一个 context
    DynamicContext context = new DynamicContext(configuration, parameterObject);

    // apply 方法将会把节点的结果放入 context
    // context 最终解析完会得到 String sql 静态字符串
    // context 里面还有 binding 参数
    rootSqlNode.apply(context);

    // 解析之后的返回值就是 StaticSqlSource
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);

    // 参数是 null 就是 Object ？？？！！！这是什么
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();

    // 解析得到一个 StaticSqlSource 静态的
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);

    // 可能是那些 <binding> 标签的东西
    // 放到额外参数里
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
