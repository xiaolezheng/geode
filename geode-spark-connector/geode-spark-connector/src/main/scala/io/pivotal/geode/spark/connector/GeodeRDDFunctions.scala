/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pivotal.geode.spark.connector

import io.pivotal.geode.spark.connector.internal.rdd.{GeodeOuterJoinRDD, GeodeJoinRDD, GeodeRDDWriter}
import org.apache.spark.Logging
import org.apache.spark.api.java.function.{PairFunction, Function}
import org.apache.spark.rdd.RDD

/**
 * Extra gemFire functions on non-Pair RDDs through an implicit conversion.
 * Import `io.pivotal.geode.spark.connector._` at the top of your program to 
 * use these functions.  
 */
class GeodeRDDFunctions[T](val rdd: RDD[T]) extends Serializable with Logging {

  /**
   * Save the non-pair RDD to Geode key-value store.
   * @param regionPath the full path of region that the RDD is stored  
   * @param func the function that converts elements of RDD to key/value pairs
   * @param connConf the GeodeConnectionConf object that provides connection to Geode cluster
   * @param opConf the optional parameters for this operation
   */
  def saveToGeode[K, V](
      regionPath: String, 
      func: T => (K, V), 
      connConf: GeodeConnectionConf = defaultConnectionConf,
      opConf: Map[String, String] = Map.empty): Unit = {
    connConf.getConnection.validateRegion[K, V](regionPath)
    if (log.isDebugEnabled)
      logDebug(s"""Save RDD id=${rdd.id} to region $regionPath, partitions:\n  ${getRddPartitionsInfo(rdd)}""")
    else
      logInfo(s"""Save RDD id=${rdd.id} to region $regionPath""")
    val writer = new GeodeRDDWriter[T, K, V](regionPath, connConf, opConf)
    rdd.sparkContext.runJob(rdd, writer.write(func) _)
  }

  /** This version of saveToGeode(...) is just for Java API. */
  private[connector] def saveToGeode[K, V](
      regionPath: String, 
      func: PairFunction[T, K, V], 
      connConf: GeodeConnectionConf, 
      opConf: Map[String, String]): Unit = {
    saveToGeode[K, V](regionPath, func.call _, connConf, opConf)
  }

  /**
   * Return an RDD containing all pairs of elements with matching keys in `this` RDD
   * and the Geode `Region[K, V]`. The join key from RDD element is generated by
   * `func(T) => K`, and the key from the Geode region is just the key of the
   * key/value pair.
   *
   * Each pair of elements of result RDD will be returned as a (t, v) tuple, 
   * where (t) is in `this` RDD and (k, v) is in the Geode region.
   *
   * @param regionPath the region path of the Geode region
   * @param func the function that generate region key from RDD element T
   * @param connConf the GeodeConnectionConf object that provides connection to Geode cluster
   * @tparam K the key type of the Geode region
   * @tparam V the value type of the Geode region
   * @return RDD[T, V]
   */
  def joinGeodeRegion[K, V](regionPath: String, func: T => K, 
    connConf: GeodeConnectionConf = defaultConnectionConf): GeodeJoinRDD[T, K, V] = {
    new GeodeJoinRDD[T, K, V](rdd, func, regionPath, connConf)    
  }

  /** This version of joinGeodeRegion(...) is just for Java API. */
  private[connector] def joinGeodeRegion[K, V](
    regionPath: String, func: Function[T, K], connConf: GeodeConnectionConf): GeodeJoinRDD[T, K, V] = {
    joinGeodeRegion(regionPath, func.call _, connConf)
  }

  /**
   * Perform a left outer join of `this` RDD and the Geode `Region[K, V]`.
   * The join key from RDD element is generated by `func(T) => K`, and the
   * key from region is just the key of the key/value pair.
   *
   * For each element (t) in `this` RDD, the resulting RDD will either contain
   * all pairs (t, Some(v)) for v in the Geode region, or the pair
   * (t, None) if no element in the Geode region have key `func(t)`
   *
   * @param regionPath the region path of the Geode region
   * @param func the function that generate region key from RDD element T
   * @param connConf the GeodeConnectionConf object that provides connection to Geode cluster
   * @tparam K the key type of the Geode region
   * @tparam V the value type of the Geode region
   * @return RDD[ T, Option[V] ]
   */
  def outerJoinGeodeRegion[K, V](regionPath: String, func: T => K,
    connConf: GeodeConnectionConf = defaultConnectionConf): GeodeOuterJoinRDD[T, K, V] = {
    new GeodeOuterJoinRDD[T, K, V](rdd, func, regionPath, connConf)
  }

  /** This version of outerJoinGeodeRegion(...) is just for Java API. */
  private[connector] def outerJoinGeodeRegion[K, V](
    regionPath: String, func: Function[T, K], connConf: GeodeConnectionConf): GeodeOuterJoinRDD[T, K, V] = {
    outerJoinGeodeRegion(regionPath, func.call _, connConf)
  }

  private[connector] def defaultConnectionConf: GeodeConnectionConf =
    GeodeConnectionConf(rdd.sparkContext.getConf)

}


