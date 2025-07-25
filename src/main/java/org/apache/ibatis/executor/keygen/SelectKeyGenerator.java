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

import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.RowBounds;

/**
 * @author Clinton Begin
 * @author Jeff Butler
 */
public class SelectKeyGenerator implements KeyGenerator {

  public static final String SELECT_KEY_SUFFIX = "!selectKey";
  private final boolean executeBefore;
  private final MappedStatement keyStatement;

  public SelectKeyGenerator(MappedStatement keyStatement, boolean executeBefore) {
    this.executeBefore = executeBefore;
    this.keyStatement = keyStatement;
  }

  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // 处理 <selectKey> 标签

    // 如果设置了 order="BEFORE" 是这样吧？
    if (executeBefore) {
      //
      processGeneratedKeys(executor, ms, parameter);
    }
  }

  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    if (!executeBefore) {
      processGeneratedKeys(executor, ms, parameter);
    }
  }

  private void processGeneratedKeys(Executor executor, MappedStatement ms, Object parameter) {
    try {
      if (parameter != null && keyStatement != null && keyStatement.getKeyProperties() != null) {

        // 主键的属性是什么，比如 ["id"]
        String[] keyProperties = keyStatement.getKeyProperties();
        final Configuration configuration = ms.getConfiguration();

        // 传入的 Java Bean 的操作器
        final MetaObject metaParam = configuration.newMetaObject(parameter);


        // Do not close keyExecutor.
        // The transaction will be closed by parent executor.

        // 竟然又创建了一个 Executor
        Executor keyExecutor = configuration.newExecutor(executor.getTransaction(), ExecutorType.SIMPLE);

        // Executor 调用某个 SQL 语句，查询下一个记录的主键
        List<Object> values = keyExecutor.query(keyStatement, parameter, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);

        // 不能是空记录
        if (values.size() == 0) {
          throw new ExecutorException("SelectKey returned no data.");
        }

        // 也必须只有 1 个记录
        if (values.size() > 1) {
          throw new ExecutorException("SelectKey returned more than one value.");
        } else {
          MetaObject metaResult = configuration.newMetaObject(values.get(0));

          // 如果主键只有一个属性，比如 ["id"]
          if (keyProperties.length == 1) {
            // 这个 mysql 不会出现吧
            if (metaResult.hasGetter(keyProperties[0])) {
              setValue(metaParam, keyProperties[0], metaResult.getValue(keyProperties[0]));
            } else {
              // no getter for the property - maybe just a single value object
              // so try that
              setValue(metaParam, keyProperties[0], values.get(0));
            }
          }
          // 处理主键是“符合主键”
          else {
            handleMultipleProperties(keyProperties, metaParam, metaResult);
          }
        }
      }
    } catch (ExecutorException e) {
      throw e;
    } catch (Exception e) {
      throw new ExecutorException("Error selecting key or setting result to parameter object. Cause: " + e, e);
    }
  }

  private void handleMultipleProperties(String[] keyProperties, MetaObject metaParam, MetaObject metaResult) {
    String[] keyColumns = keyStatement.getKeyColumns();

    if (keyColumns == null || keyColumns.length == 0) {
      // no key columns specified, just use the property names
      for (String keyProperty : keyProperties) {
        setValue(metaParam, keyProperty, metaResult.getValue(keyProperty));
      }
    } else {
      if (keyColumns.length != keyProperties.length) {
        throw new ExecutorException(
            "If SelectKey has key columns, the number must match the number of key properties.");
      }
      for (int i = 0; i < keyProperties.length; i++) {
        setValue(metaParam, keyProperties[i], metaResult.getValue(keyColumns[i]));
      }
    }
  }

  private void setValue(MetaObject metaParam, String property, Object value) {
    if (!metaParam.hasSetter(property)) {
      throw new ExecutorException("No setter found for the keyProperty '" + property + "' in "
          + metaParam.getOriginalObject().getClass().getName() + ".");
    }
    metaParam.setValue(property, value);
  }
}
