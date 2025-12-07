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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 */
public class GenericTokenParser {

  private final String openToken;
  private final String closeToken;

  /**
   * Token 处理器。遇到 Token 会怎么处理。
   */
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }

    // search open token
    // 快速判断是否存在第一个需要处理的 token，如果没有，那么就是说这个文本没有任何需要处理的 token
    int start = text.indexOf(openToken);
    if (start == -1) {
      return text;
    }

    // 至少存在一个 1 个 token 需要处理
    // 转换为 char[]
    char[] src = text.toCharArray();

    int offset = 0; // 前一次处理 #{...} 的偏移量，准确来说是指前一次处理 #{...} 下一个索引位置
    final StringBuilder builder = new StringBuilder();
    StringBuilder expression = null;

    // 整个 do { 内部处理 #{...} } while

    do {
      // 这是处理类似 '\#{' 的情况需要跳过
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        // expression 用于存储 #{} 之间的内容，也就是 openToken 和 closeToken 之间的内容
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }

        // 接下来要处理 #{...}，所有先把之前的内容都追加到 builder 中
        // 例如 select * from user where id = #{id} 将会追加 'select * from user where id = '
        builder.append(src, offset, start - offset);
        offset = start + openToken.length();

        // 获取 closeToken 的位置，为什么下面还是一个循环，因为需要不停寻找非 \} 的 closeToken
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          // end <= offset 其实就是 end == offset
          // --> 意味着 (1) #{} 这是一个没有内容的表达式
          // --> 或者是 (2) 之前遇到 \} 紧接着就是 }
          if ((end <= offset) || (src[end - 1] != '\\')) {
            // 追加表达式内容，也就是 #{...} 里面的 ... 部分或者是紧接着之前 \} 之后的内容
            // end == offset              append 的是 ""
            // src[end - 1] != '\\'       append 的是 表达式内容
            expression.append(src, offset, end - offset);
            break;
          }

          // this close token is escaped. remove the backslash and continue.
          // 遇到的是 '\}' 但是不能把 '\' 加进来，所以要 -1。先把 } 之前的内容都加进来，然后添加 closeToken
          expression.append(src, offset, end - offset - 1).append(closeToken);

          // 接下来继续寻找 closeToken，计算 offset(下面从哪儿开始)，以及下一个 closeToken 的位置(end)
          offset = end + closeToken.length();
          end = text.indexOf(closeToken, offset);
        }

        // 没有找到 } 就此结束，把剩下来的字符都加进去
        if (end == -1) {
          // close token was not found.
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {

          // 把表达式内容进行处理，并返回处理之后的值，例如 #{} 一般会全部替换为 ?
          builder.append(handler.handleToken(expression.toString()));

          // 整个 #{...} 都处理完毕了，计算下面从哪儿开始
          offset = end + closeToken.length();
        }
      }

      // 计算下一次 openToken 的位置
      start = text.indexOf(openToken, offset);
    } while (start > -1);
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
