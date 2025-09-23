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
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;
import org.apache.ibatis.util.MapUtil;

/**
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

  /**
   * 这里维护了一个 map，而且 TransactionalCache 里面包含了一个 delegate Cache 缓存，其就是 key
   * <p>
   * 感觉这里面又有点像 AOP，通过 Cache 的 key 找到它的增强对象，然后操作
   * <p>
   * 为什么不直接操作 Cache 呢，这样这个执行器的事务就察觉不到了
   */
  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  /**
   * 底层会将事务缓存 clearOnCommit 置为 true，表示事务提交之后要把缓存都清掉
   * <p>
   * 一旦调用了 clear 以后再也无法从这个 cache 中获得任何 value
   */
  public void clear(Cache cache) {
    getTransactionalCache(cache).clear();
  }

  public Object getObject(Cache cache, CacheKey key) {
    // 获取 Cache 空间，获取缓存
    return getTransactionalCache(cache).getObject(key);
  }

  /**
   * 这个方法是缓存对象的关键
   *
   * @param cache 缓存对象
   * @param key   缓存的 key
   * @param value 缓存的值
   */
  public void putObject(Cache cache, CacheKey key, Object value) {

    // 对于同一个 Cache 不同事务有自己的 Cache 空间

    // 其实是找到 Cache 空间，然后向其中一个 HashMap 添加一个元素

    getTransactionalCache(cache).putObject(key, value);
  }

  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }

  public void rollback() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  /**
   * 好多方法都通过这个方法寻找 TransactionalCache
   */
  private TransactionalCache getTransactionalCache(Cache cache) {
    // 这个 computeIfAbsent API 设计得挺费解的
    // 第二个入参是 TransactionalCache::new，其实等价于 new TransactionalCache(cache)
    // 构造器的参数就是接收的 cache


    // TransactionalCache 你可以理解为一个缓存上下文
    return MapUtil.computeIfAbsent(transactionalCaches, cache, TransactionalCache::new);
  }

}
