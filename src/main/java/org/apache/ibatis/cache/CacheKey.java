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
package org.apache.ibatis.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.ibatis.reflection.ArrayUtil;

/**
 * MyBatis 内部使用的缓存键对象，比较简单，其实将一组对象放在一起，算出一个键
 *
 * @author Clinton Begin
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;

  /**
   * 给其他类使用，表示一个空的缓存键，省的创建太多 new CacheKey()
   */
  public static final CacheKey NULL_CACHE_KEY = new CacheKey() {

    private static final long serialVersionUID = 1L;

    @Override
    public void update(Object object) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }

    @Override
    public void updateAll(Object[] objects) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }
  };

  private static final int DEFAULT_MULTIPLIER = 37;

  private static final int DEFAULT_HASHCODE = 17;

  private final int multiplier;

  private int hashcode;

  private long checksum;

  private int count;

  // 8/21/2017 - Sonarlint flags this as needing to be marked transient. While true if content is not serializable, this
  // is not always true and thus should not be marked transient.
  private List<Object> updateList;

  public CacheKey() {
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLIER;
    this.count = 0;
    this.updateList = new ArrayList<>();
  }

  public CacheKey(Object[] objects) {
    this();
    updateAll(objects);
  }

  /**
   * 返回扰动次数
   *
   * @return 扰动次数
   */
  public int getUpdateCount() {
    return updateList.size();
  }

  /**
   * 调用一次 update 方法就会扰动内部的值
   *
   * @param object 传入对象
   */
  public void update(Object object) {
    // null 视作 1

    int baseHashCode = object == null ? 1 : ArrayUtil.hashCode(object);

    count++;

    // 校验和，所有对象的 hash code 之和
    checksum += baseHashCode;
    baseHashCode *= count;

    // 计算 hash 值
    hashcode = multiplier * hashcode + baseHashCode;

    // 记录有哪些对象参与了 update
    updateList.add(object);
  }

  /**
   * 传入多个对象扰动
   *
   * @param objects 多个对象
   */
  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  @Override
  public boolean equals(Object object) {
    // 比较经典的 equals 范式

    // 快速判断是否是同一个对象
    if (this == object) {
      return true;
    }

    // 比较类型
    if (!(object instanceof CacheKey)) {
      return false;
    }

    final CacheKey cacheKey = (CacheKey) object;

    // 检查每个字段
    if ((hashcode != cacheKey.hashcode) || (checksum != cacheKey.checksum) || (count != cacheKey.count)) {
      return false;
    }

    // 如果字段是 List 之类的，需要使用 equals 比较每个值
    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (!ArrayUtil.equals(thisObject, thatObject)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  @Override
  public String toString() {
    StringJoiner returnValue = new StringJoiner(":");
    returnValue.add(String.valueOf(hashcode));
    returnValue.add(String.valueOf(checksum));
    updateList.stream().map(ArrayUtil::toString).forEach(returnValue::add);
    return returnValue.toString();
  }

  @Override
  public CacheKey clone() throws CloneNotSupportedException {
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    clonedCacheKey.updateList = new ArrayList<>(updateList);
    return clonedCacheKey;
  }

}
