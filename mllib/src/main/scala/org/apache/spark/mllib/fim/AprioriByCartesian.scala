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

package org.apache.spark.mllib.fim

import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.{Logging, SparkContext}

/**
 * Calculate frequent item set using Apriori algorithm with minSupport.
 * The apriori algorithm have two steps:
 * step one is scan the data set to get L1 by minSuppprt
 * step two is scan the data set multiple to get Lk
 */
object AprioriByCartesian extends Logging with Serializable {

  /**
   * create L1
   * @param dataSet For mining frequent item sets dataset
   * @param minCount The minimum degree of support
   * @return L1
   */
  def aprioriStepOne(dataSet: RDD[Set[String]],
                     minCount: Double): RDD[(Set[String], Int)] = {
    dataSet.flatMap(line => line)
      .map(v => (v, 1))
      .reduceByKey(_ + _)
      .filter(_._2 >= minCount)
      .map(x => (Set(x._1), x._2))
  }

  /**
   * create Lk from Ck.Lk is generated by Ck when the frequent of Ck bigger than minSupport
   * @param Ck Candidate set
   * @param minCount The minimum degree of support
   * @return Lk
   */
  def scanD(dataSet: RDD[Set[String]],
            Ck: RDD[Set[String]],
            minCount: Double,
            sc: SparkContext): RDD[(Set[String], Int)] = {

    dataSet.cartesian(Ck).map(x =>
      if (x._2.subsetOf(x._1)) {
        (x._2, 1)
      } else {
        (x._2, 0)
      }).reduceByKey(_ + _).filter(x => x._2 >= minCount)
  }

  /**
   * create Ck by Lk
   * @param Lk
   * @param k
   * @return Ck
   */
  def aprioriGen(Lk: RDD[Set[String]],
                 k: Int): RDD[Set[String]] = {
    Lk.cartesian(Lk)
      .map(x => x._1 ++ x._2)
      .filter(x => x.size == k)
      .distinct()
  }

  /**
   * apriori algorithm using cartesian of two RDD.
   * Solving frequent item sets based on the data set and the minimum degree of support.
   * The first phase, the scan time data sets, computing frequent item sets L1.
   * The second stage, multiple scan data sets, computing frequent item sets Lk.
   * @param input  For mining frequent item sets dataset
   * @param minSupport The minimum degree of support
   * @param sc
   * @return frequent item sets
   */
  def apriori(input: RDD[Array[String]],
              minSupport: Double,
              sc: SparkContext): Array[(Set[String], Int)] = {

    // dataSet length
    val dataSetLen: Long = input.count()
    // the count line for minSupport
    val minCount = minSupport * dataSetLen
    // This algorithm finds frequent item set, so convert each element of RDD to set
    val dataSet = input.map(_.toSet)

    // definite L collection that using save all of frequent item set
    val L = collection.mutable.ArrayBuffer[RDD[(Set[String], Int)]]()

    val L1: RDD[(Set[String], Int)] = aprioriStepOne(dataSet, minCount)
    if (L1.count() > 0) {
      L += L1
      var Lk = L1

      // step counter
      var k = 2

      while (L(k - 2).count() > 1) {

        // get candidate of frequent item set
        val Ck: RDD[Set[String]] = aprioriGen(L(k - 2).map(x => x._1), k)

        // scan input data set to calculate degree of support for each candidate,
        // and filter out the one not ineligible
        Lk = scanD(dataSet, Ck, minCount, sc)

        k = k + 1
        L += Lk
      }
      // return all result in L
      val retArr = collection.mutable.ArrayBuffer[(Set[String], Int)]()
      for (l <- L) {
        retArr.appendAll(l.collect())
      }
      retArr.toArray
    } else {
      Array[(Set[String], Int)]()
    }
  }

  def printLk(Lk: RDD[(Set[String], Int)], k: Int) {
    print("L" + (k - 2) + " size " + Lk.count() + " value: ")
    Lk.collect().foreach(x => print("(" + x._1 + ", " + x._2 + ") "))
    println()
  }

  def printCk(Ck: RDD[Set[String]], k: Int) {
    print("C" + (k - 2) + " size " + Ck.count() + " value: ")
    Ck.collect().foreach(print)
    println()
  }
}
