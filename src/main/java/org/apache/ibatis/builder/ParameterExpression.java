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
package org.apache.ibatis.builder;

import java.util.HashMap;

/**
 * Inline parameter expression parser. Supported grammar (simplified):
 * <p>
 * 一个内联参数由 3 部分组成:
 * <pre>
 * a) 属性名或表达式(二选一)
 * b) oldJdbcType
 * c) 属性 可选，可以有多个
 * </pre>
 *
 * <pre>
 * inline-parameter = (propertyName | expression) oldJdbcType attributes
 *
 * propertyName = /expression language's property navigation path/
 * propertyName 就是一条普通地对象属性访问路径，比如 user.address.street
 *
 * expression = '(' /expression language's expression/ ')'
 * expression 如果想写更复杂地的表达式，就放在一对圆括号里，例如 (#{age}+1)
 *
 * oldJdbcType = ':' /any valid jdbc type/
 * 在属性名或表达式后面加冒号，再写 JDBC 类型，表示强制指定类型: name:VARCHAR
 * 为什么称之为 old(旧的)？因为新的方式是使用属性，例如 #{user,jdbcType=VARCHAR}
 *
 * attributes = (',' attribute)*
 * 后面可以跟 0 到 N 个逗号分隔的 attribute
 *
 * attribute = name '=' value
 * attribute 的形式是 name=value，比如 javaType=int, typeHandler=MyHandler
 * </pre>
 *
 * @author Frank D. Martinez [mnesarco]
 */
public class ParameterExpression extends HashMap<String, String> {

  private static final long serialVersionUID = -2417552199605158680L;

  public ParameterExpression(String expression) {
    parse(expression);
  }

  private void parse(String expression) {
    // 找到下一个有效字符(跳过空白)
    int p = skipWS(expression, 0);

    // 如果以 '(' 开始，那么这是一个表达式
    if (expression.charAt(p) == '(') {
      expression(expression, p + 1);
    }
    // 否则，这是一个属性
    else {
      property(expression, p);
    }
  }

  private void expression(String expression, int left) {
    int match = 1;
    int right = left + 1;
    while (match > 0) {
      if (expression.charAt(right) == ')') {
        match--;
      } else if (expression.charAt(right) == '(') {
        match++;
      }
      right++;
    }
    put("expression", expression.substring(left, right - 1));
    jdbcTypeOpt(expression, right);
  }

  private void property(String expression, int left) {
    if (left < expression.length()) {
      int right = skipUntil(expression, left, ",:");
      put("property", trimmedStr(expression, left, right));
      jdbcTypeOpt(expression, right);
    }
  }

  /**
   * 从 expression[p] 开始，跳过空白字符，返回下一个非空白字符的位置
   */
  private int skipWS(String expression, int p) {
    for (int i = p; i < expression.length(); i++) {
      if (expression.charAt(i) > 0x20) {
        return i;
      }
    }

    // 如果全都是空白字符，就返回 expression 的长度
    return expression.length();
  }

  private int skipUntil(String expression, int p, final String endChars) {
    for (int i = p; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (endChars.indexOf(c) > -1) {
        return i;
      }
    }
    return expression.length();
  }

  private void jdbcTypeOpt(String expression, int p) {
    p = skipWS(expression, p);
    if (p < expression.length()) {
      if (expression.charAt(p) == ':') {
        jdbcType(expression, p + 1);
      } else if (expression.charAt(p) == ',') {
        option(expression, p + 1);
      } else {
        throw new BuilderException("Parsing error in {" + expression + "} in position " + p);
      }
    }
  }

  private void jdbcType(String expression, int p) {
    int left = skipWS(expression, p);
    int right = skipUntil(expression, left, ",");
    if (right <= left) {
      throw new BuilderException("Parsing error in {" + expression + "} in position " + p);
    }
    put("jdbcType", trimmedStr(expression, left, right));
    option(expression, right + 1);
  }

  private void option(String expression, int p) {
    int left = skipWS(expression, p);
    if (left < expression.length()) {
      int right = skipUntil(expression, left, "=");
      String name = trimmedStr(expression, left, right);
      left = right + 1;
      right = skipUntil(expression, left, ",");
      String value = trimmedStr(expression, left, right);
      put(name, value);
      option(expression, right + 1);
    }
  }

  private String trimmedStr(String str, int start, int end) {
    while (str.charAt(start) <= 0x20) {
      start++;
    }
    while (str.charAt(end - 1) <= 0x20) {
      end--;
    }
    return start >= end ? "" : str.substring(start, end);
  }

}
