/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.carbondata.indexserver

import org.apache.spark.{Partition, TaskContext}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.hive.DistributionUtil
import scala.collection.JavaConverters._

import org.apache.carbondata.core.cache.CacheProvider
import org.apache.carbondata.core.datamap.DataMapStoreManager
import org.apache.carbondata.hadoop.CarbonInputSplit
import org.apache.carbondata.spark.rdd.CarbonRDD

class DistributedShowCacheRDD(@transient private val ss: SparkSession, tableName: String)
  extends CarbonRDD[String](ss, Nil) {

  val executorsList: Array[String] = DistributionUtil.getNodeList(ss.sparkContext)

  override protected def internalGetPartitions: Array[Partition] = {
    executorsList.zipWithIndex.map {
      case (executor, idx) =>
        // create a dummy split for each executor to accumulate the cache size.
        val dummySplit = new CarbonInputSplit()
        dummySplit.setLocation(Array(executor))
        new DataMapRDDPartition(id, idx, dummySplit)
    }
  }

  override def internalCompute(split: Partition, context: TaskContext): Iterator[String] = {
    val dataMaps = DataMapStoreManager.getInstance().getAllDataMaps.asScala
    val iterator = dataMaps.collect {
      case (table, tableDataMaps) if table.isEmpty ||
                                     (tableName.nonEmpty && tableName.equalsIgnoreCase(table)) =>
        val sizeAndIndexLengths = tableDataMaps.asScala
          .map(_.getBlockletDetailsFetcher.getCacheSize)
        // return tableName_indexFileLength_indexCachesize for each executor.
        sizeAndIndexLengths.map {
          x => s"$table:$x"
        }
    }.flatten.toIterator
    iterator
  }
}
