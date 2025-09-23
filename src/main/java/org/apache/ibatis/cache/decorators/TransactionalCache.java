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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session. Entries are sent to
 * the cache when commit is called or discarded if the Session is rolled back. Blocking cache support has been added.
 * Therefore any get() that returns a cache miss will be followed by a put() so any lock associated with the key can be
 * released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  private final Cache delegate;
  private boolean clearOnCommit;

  /**
   * 一个所谓的什么 entry，记录着当 commit 时要添加的 entry
   */
  private final Map<Object, Object> entriesToAddOnCommit;
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }


  /**
   * 获得缓存。如果没有命中，就添加到 entriesMissedInCache
   */
  @Override
  public Object getObject(Object key) {
    // issue #116
    // 从委托中获取
    Object object = delegate.getObject(key);

    // 如果没有缓存 value
    if (object == null) {
      // 记录一下未命中的，后面会清掉
      entriesMissedInCache.add(key);
    }

    // issue #146
    // 一旦设置了事务提交要清空缓存，那么干脆全都返回 null
    if (clearOnCommit) {
      return null;
    }
    return object;
  }


  @Override
  public void putObject(Object key, Object object) {
    // 其实就是向 HashMap 添加一个元素
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {

    // 做个标记，如果 commit 就清空
    // 这是什么意思，这说明缓存已经脏了
    clearOnCommit = true;

    // 在 commit 时，将缓存中心的 entry 也清空
    // 清空
    entriesToAddOnCommit.clear();
  }

  public void commit() {
    if (clearOnCommit) {
      delegate.clear();
    }
    flushPendingEntries();
    reset();
  }

  public void rollback() {
    unlockMissedEntries();
    reset();
  }

  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifying a rollback to the cache adapter. "
          + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
