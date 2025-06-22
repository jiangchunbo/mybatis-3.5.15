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
package org.apache.ibatis.cursor.defaults;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetWrapper;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * This is the default implementation of a MyBatis Cursor. This implementation is not thread safe.
 *
 * @author Guillaume Darmont / guillaume@dropinocean.com
 */
public class DefaultCursor<T> implements Cursor<T> {

  // ResultSetHandler stuff
  private final DefaultResultSetHandler resultSetHandler;
  private final ResultMap resultMap;
  private final ResultSetWrapper resultSetWrapper;
  private final RowBounds rowBounds;
  protected final ObjectWrapperResultHandler<T> objectWrapperResultHandler = new ObjectWrapperResultHandler<>();

  private final CursorIterator cursorIterator = new CursorIterator();
  private boolean iteratorRetrieved;

  /**
   * 这表示构造器创建完这个对象，状态是 CREATED
   */
  private CursorStatus status = CursorStatus.CREATED;

  private int indexWithRowBound = -1;

  private enum CursorStatus {

    /**
     * A freshly created cursor, database ResultSet consuming has not started.
     */
    CREATED,
    /**
     * A cursor currently in use, database ResultSet consuming has started.
     */
    OPEN,
    /**
     * A closed cursor, not fully consumed.
     */
    CLOSED,
    /**
     * A fully consumed cursor, a consumed cursor is always closed.
     */
    CONSUMED
  }

  public DefaultCursor(DefaultResultSetHandler resultSetHandler, ResultMap resultMap, ResultSetWrapper resultSetWrapper,
      RowBounds rowBounds) {
    this.resultSetHandler = resultSetHandler;
    this.resultMap = resultMap;
    this.resultSetWrapper = resultSetWrapper;
    this.rowBounds = rowBounds;
  }

  @Override
  public boolean isOpen() {
    return status == CursorStatus.OPEN;
  }

  @Override
  public boolean isConsumed() {
    return status == CursorStatus.CONSUMED;
  }

  @Override
  public int getCurrentIndex() {
    return rowBounds.getOffset() + cursorIterator.iteratorIndex;
  }

  @Override
  public Iterator<T> iterator() {
    if (iteratorRetrieved) {
      throw new IllegalStateException("Cannot open more than one iterator on a Cursor");
    }
    if (isClosed()) {
      throw new IllegalStateException("A Cursor is already closed.");
    }
    iteratorRetrieved = true;
    return cursorIterator;
  }

  @Override
  public void close() {
    if (isClosed()) {
      return;
    }

    ResultSet rs = resultSetWrapper.getResultSet();
    try {
      if (rs != null) {
        rs.close();
      }
    } catch (SQLException e) {
      // ignore
    } finally {
      status = CursorStatus.CLOSED;
    }
  }

  protected T fetchNextUsingRowBound() {
    T result = fetchNextObjectFromDatabase();

    // 需要满足 rowBounds 如果还没有跳过 offset，就需要不断调用 fetchNextObjectFromDatabase 丢弃行
    // 默认我们都不用 rowBounds 因此 offset 是 0
    while (objectWrapperResultHandler.fetched && indexWithRowBound < rowBounds.getOffset()) {
      result = fetchNextObjectFromDatabase();
    }
    return result;
  }

  protected T fetchNextObjectFromDatabase() {
    // 若已关闭，直接返回 null
    if (isClosed()) {
      return null;
    }

    try {
      // 把 fetched 置为 false (算初始化吧，表示还没抓取)
      // 状态改为 OPEN
      objectWrapperResultHandler.fetched = false;
      status = CursorStatus.OPEN;

      // 处理 RowValue，如果抓取到数据了，其中会把 fetched 置为 true
      if (!resultSetWrapper.getResultSet().isClosed()) {
        // 这里面会做的事情有：
        // 从 ResultSet 读取下一行
        // 按 resultMap 做对象映射
        // 交给 ObjectWrapperResultHandler.handleResult()
        resultSetHandler.handleRowValues(resultSetWrapper, resultMap, objectWrapperResultHandler, RowBounds.DEFAULT, null);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    // 从 objectWrapperResultHandler 获取下一个对象
    // 但是，如果根本没有记录，是不会被 resultHandler 处理，这里也是 null
    T next = objectWrapperResultHandler.result;

    // 因为 objectWrapperResultHandler 将 fetched 置为 true，因此这里 index + 1
    // 如果根本没有被 resultHandler 处理，这里也是默认 false
    if (objectWrapperResultHandler.fetched) {
      indexWithRowBound++;
    }

    // No more object or limit reached
    // 没有更多对象了
    if (!objectWrapperResultHandler.fetched || getReadItemsCount() == rowBounds.getOffset() + rowBounds.getLimit()) {
      close();
      status = CursorStatus.CONSUMED;
    }
    objectWrapperResultHandler.result = null;

    return next;
  }

  private boolean isClosed() {
    return status == CursorStatus.CLOSED || status == CursorStatus.CONSUMED;
  }

  private int getReadItemsCount() {
    return indexWithRowBound + 1;
  }

  protected static class ObjectWrapperResultHandler<T> implements ResultHandler<T> {

    /**
     * 结果处理器
     */
    protected T result;

    /**
     * 是否抓取到了数据，默认是 false，如果是 false 就会从数据库抓取
     */
    protected boolean fetched;

    @Override
    public void handleResult(ResultContext<? extends T> context) {
      // 保存那一行
      this.result = context.getResultObject();

      // 让 ResultHandler 停止
      context.stop();

      // 标记已经取到数据了
      fetched = true;
    }
  }

  protected class CursorIterator implements Iterator<T> {

    /**
     * Holder for the next object to be returned.
     */
    T object;

    /**
     * Index of objects returned using next(), and as such, visible to users.
     */
    int iteratorIndex = -1;

    /**
     * 返回是否有下一个
     */
    @Override
    public boolean hasNext() {
      // 是否取到数据，没取到就从数据库取
      if (!objectWrapperResultHandler.fetched) {
        object = fetchNextUsingRowBound();
      }

      return objectWrapperResultHandler.fetched;
    }

    @Override
    public T next() {
      // Fill next with object fetched from hasNext()
      T next = object;

      if (!objectWrapperResultHandler.fetched) {
        next = fetchNextUsingRowBound();
      }

      if (objectWrapperResultHandler.fetched) {
        objectWrapperResultHandler.fetched = false;
        object = null;
        iteratorIndex++;
        return next;
      }
      throw new NoSuchElementException();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Cannot remove element from Cursor");
    }
  }
}
