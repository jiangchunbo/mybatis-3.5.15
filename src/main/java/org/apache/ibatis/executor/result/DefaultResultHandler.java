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
package org.apache.ibatis.executor.result;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;

/**
 * 一般的 ResultHandler
 *
 * @author Clinton Begin
 */
public class DefaultResultHandler implements ResultHandler<Object> {

  private final List<Object> list;

  public DefaultResultHandler() {
    list = new ArrayList<>();
  }

  @SuppressWarnings("unchecked")
  public DefaultResultHandler(ObjectFactory objectFactory) {
    list = objectFactory.create(List.class);
  }

  @Override
  public void handleResult(ResultContext<?> context) {
    list.add(context.getResultObject());
  }

  public List<Object> getResultList() {
    return list;
  }

}
