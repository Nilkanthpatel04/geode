/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.management.internal.cli.functions;

import java.util.List;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheClosedException;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.cache.query.Index;
import org.apache.geode.cache.query.QueryService;
import org.apache.geode.internal.cache.execute.InternalFunction;
import org.apache.geode.internal.cache.xmlcache.CacheXml;
import org.apache.geode.management.internal.cli.domain.IndexInfo;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.configuration.domain.XmlEntity;

public class DestroyIndexFunction implements InternalFunction {
  private static final long serialVersionUID = -868082551095130315L;

  @Override
  public void execute(FunctionContext context) {
    IndexInfo indexInfo = (IndexInfo) context.getArguments();
    String memberId = null;

    CliFunctionResult result;
    try {
      Cache cache = context.getCache();
      memberId = cache.getDistributedSystem().getDistributedMember().getId();
      QueryService queryService = cache.getQueryService();
      String indexName = indexInfo.getIndexName();
      String regionPath = indexInfo.getRegionPath();

      XmlEntity xmlEntity =
          new XmlEntity(CacheXml.REGION, "name", regionPath, CacheXml.INDEX, "name", indexName);

      if (regionPath != null && !regionPath.isEmpty()) {
        Region<?, ?> region = cache.getRegion(regionPath);

        if (region != null) {
          if (indexName == null || indexName.isEmpty()) {
            queryService.removeIndexes(region);
            result = new CliFunctionResult(memberId, xmlEntity,
                "Destroyed all indexes on region " + regionPath);
          } else {
            Index index = queryService.getIndex(region, indexName);

            if (index != null) {
              queryService.removeIndex(index);
              result = new CliFunctionResult(memberId, xmlEntity,
                  "Destroyed index " + indexName + " on region " + regionPath);
            } else if (indexInfo.isIfExists()) {
              result = new CliFunctionResult(memberId, true,
                  "Index " + indexName + " not found - skipped");
            } else {
              result = new CliFunctionResult(memberId, false,
                  CliStrings.format(CliStrings.DESTROY_INDEX__INDEX__NOT__FOUND, indexName));
            }
          }
        } else {
          result = new CliFunctionResult(memberId, false,
              CliStrings.format(CliStrings.DESTROY_INDEX__REGION__NOT__FOUND, regionPath));
        }
      } else {
        if (indexName == null || indexName.isEmpty()) {
          queryService.removeIndexes();
          result = new CliFunctionResult(memberId, xmlEntity, "Destroyed all indexes");
        } else {
          boolean indexRemoved = removeIndexByName(indexName, queryService);
          if (indexRemoved) {
            result = new CliFunctionResult(memberId, xmlEntity, "Destroyed index " + indexName);
          } else if (indexInfo.isIfExists()) {
            result = new CliFunctionResult(memberId, true,
                "Index " + indexName + " not found - skipped");
          } else {
            result = new CliFunctionResult(memberId, false,
                CliStrings.format(CliStrings.DESTROY_INDEX__INDEX__NOT__FOUND, indexName));
          }
        }
      }
    } catch (CacheClosedException e) {
      result = new CliFunctionResult(memberId, e, e.getMessage());
    } catch (Exception e) {
      result = new CliFunctionResult(memberId, e, e.getMessage());
    }

    context.getResultSender().lastResult(result);
  }

  /***
   *
   * @param name
   * @param queryService
   * @return true if the index was found and removed/false if the index was not found.
   */
  private boolean removeIndexByName(String name, QueryService queryService) {
    List<Index> indexes = (List<Index>) queryService.getIndexes();
    boolean removed = false;

    if (indexes != null) {
      for (Index index : indexes) {
        if (index.getName().equals(name)) {
          queryService.removeIndex(index);
          removed = true;
        }
      }
    }
    return removed;
  }

  @Override
  public String getId() {
    return DestroyIndexFunction.class.getName();
  }

}
