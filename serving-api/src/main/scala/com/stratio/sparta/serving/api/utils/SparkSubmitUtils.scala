/*
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.sparta.serving.api.utils

import com.github.nscala_time.time.Imports._
import com.stratio.sparta.driver.SpartaClusterJob
import com.stratio.sparta.serving.core.config.SpartaConfig
import com.stratio.sparta.serving.core.constants.AppConstant._
import com.stratio.sparta.serving.core.helpers.PolicyHelper
import com.stratio.sparta.serving.core.models.policy.{PolicyModel, SubmitArgument}
import com.stratio.sparta.serving.core.utils.{HdfsUtils, PolicyConfigUtils}
import com.typesafe.config.Config

import scala.collection.JavaConversions._
import scala.util.{Failure, Properties, Success, Try}

trait SparkSubmitUtils extends PolicyConfigUtils with ArgumentsUtils {

  val SpartaDriverClass = SpartaClusterJob.getClass.getCanonicalName.replace("$", "")

  // Properties mapped to Spark Configuration
  val SubmitDeployMode = "--deploy-mode"
  val SubmitName = "--name"
  val SubmitNameConf = "spark.app.name"
  val SubmitTotalExecutorCores = "--total-executor-cores"
  val SubmitTotalExecutorCoresConf = "spark.cores.max"
  val SubmitPackages = "--packages"
  val SubmitPackagesConf = "spark.jars.packages"
  val SubmitJars = "--jars"
  val SubmitJarsConf = "spark.jars"
  val SubmitDriverJavaOptions = "--driver-java-options"
  val SubmitDriverJavaOptionsConf = "spark.driver.extraJavaOptions"
  val SubmitDriverLibraryPath = "--driver-library-path"
  val SubmitDriverLibraryPathConf = "spark.driver.extraLibraryPath"
  val SubmitDriverClassPath = "--driver-class-path"
  val SubmitDriverClassPathConf = "spark.driver.extraClassPath"
  val SubmitExcludePackages = "--exclude-packages"
  val SubmitExcludePackagesConf = "spark.jars.excludes"
  val SubmitDriverCores = "--driver-cores"
  val SubmitDriverCoresConf = "spark.driver.cores"
  val SubmitDriverMemory = "--driver-memory"
  val SubmitDriverMemoryConf = "spark.driver.memory"
  val SubmitExecutorCores = "--executor-cores"
  val SubmitExecutorCoresConf = "spark.executor.cores"
  val SubmitExecutorMemory = "--executor-memory"
  val SubmitExecutorMemoryConf = "spark.executor.memory"
  val SubmitGracefullyStopConf = "spark.streaming.stopGracefullyOnShutdown"
  val SubmitAppNameConf = "spark.app.name"

  // Properties only available in spark-submit
  val SubmitPropertiesFile = "--properties-file"
  val PropertiesFileEnv = "SUBMIT_PROPERTIES_FILE"
  val SubmitRepositories = "--repositories"
  val RepositoriesEnv = "SUBMIT_REPOSITORIES"
  val SubmitProxyUser = "--proxy-user"
  val ProxyUserEnv = "SUBMIT_PROXY_USER"
  val SubmitYarnQueue = "--queue"
  val SubmitFiles = "--files"
  val SubmitArchives = "--archives"
  val SubmitAddJars = "--addJars"
  val SubmitNumExecutors = "--num-executors"
  val SubmitPrincipal = "--principal"
  val PrincipalEnv = "SUBMIT_PRINCIPAL"
  val SubmitKeyTab = "--keytab"
  val KeyTabEnv = "SUBMIT_KEYTAB"
  val SubmitSupervise = "--supervise"
  val SuperviseEnv = "SUBMIT_SUPERVISE"

  // Spark submit arguments supported
  val SubmitArguments = Seq(SubmitDeployMode, SubmitName, SubmitPropertiesFile, SubmitTotalExecutorCores,
    SubmitPackages, SubmitRepositories, SubmitExcludePackages, SubmitJars, SubmitProxyUser, SubmitDriverJavaOptions,
    SubmitDriverLibraryPath, SubmitDriverClassPath, SubmitYarnQueue, SubmitFiles, SubmitArchives, SubmitAddJars,
    SubmitNumExecutors, SubmitDriverCores, SubmitDriverMemory, SubmitExecutorCores, SubmitExecutorMemory,
    SubmitPrincipal, SubmitKeyTab, SubmitSupervise)

  // Spark submit arguments and their spark configuration related
  val SubmitArgumentsToConfProperties = Map(
    SubmitName -> SubmitNameConf,
    SubmitTotalExecutorCores -> SubmitTotalExecutorCoresConf,
    SubmitPackages -> SubmitPackagesConf,
    SubmitExcludePackages -> SubmitExcludePackagesConf,
    SubmitJars -> SubmitJarsConf,
    SubmitDriverJavaOptions -> SubmitDriverJavaOptionsConf,
    SubmitDriverLibraryPath -> SubmitDriverLibraryPathConf,
    SubmitDriverClassPath -> SubmitDriverClassPathConf,
    SubmitDriverCores -> SubmitDriverCoresConf,
    SubmitDriverMemory -> SubmitDriverMemoryConf,
    SubmitExecutorCores -> SubmitExecutorCoresConf,
    SubmitExecutorMemory -> SubmitExecutorMemoryConf
  )

  val submitArgumentsToMarathonEnv = Map(
    SubmitPropertiesFile -> PropertiesFileEnv,
    SubmitRepositories -> RepositoriesEnv,
    SubmitProxyUser -> ProxyUserEnv,
    SubmitPrincipal -> PrincipalEnv,
    SubmitKeyTab -> KeyTabEnv,
    SubmitSupervise -> SuperviseEnv
  )

  def extractDriverSubmit(policy: PolicyModel, detailConfig: Config, hdfsConfig: Option[Config]): String = {
    val driverStorageLocation = Try(optionFromPolicyAndProperties(policy.driverUri, detailConfig, DriverURI))
      .getOrElse(DefaultProvidedDriverURI)
    if (driverLocation(driverStorageLocation) == ConfigHdfs) {
      val Hdfs = HdfsUtils()
      val Uploader = ClusterSparkFilesUtils(policy, Hdfs)

      Uploader.uploadDriverFile(driverStorageLocation)
    } else driverStorageLocation
  }

  def extractSparkHome(clusterConfig: Config): String =
    Properties.envOrElse("SPARK_HOME", clusterConfig.getString(SparkHome)).trim

  /**
   * Checks if we have a valid Spark home.
   */
  def validateSparkHome(clusterConfig: Config): Unit = require(Try(extractSparkHome(clusterConfig)).isSuccess,
    "You must set the $SPARK_HOME path in configuration or environment")

  def extractDriverArguments(policy: PolicyModel,
                             driverFile: String,
                             clusterConfig: Config,
                             zookeeperConfig: Config,
                             executionMode: String,
                             pluginsFiles: Seq[String]): Seq[String] = {
    val driverLocationKey = driverLocation(driverFile)
    val driverLocationConfig = SpartaConfig.initOptionalConfig(driverLocationKey, SpartaConfig.mainConfig)

    Seq(
      policy.id.get.trim,
      keyConfigEncoded("zookeeper", zookeeperConfig),
      keyConfigEncoded("config", DetailConfig),
      pluginsEncoded(pluginsFiles),
      keyOptionConfigEncoded(driverLocationKey, driverLocationConfig),
      keyOptionConfigEncoded(executionMode, Option(clusterConfig))
    )
  }

  def extractSubmitArgumentsAndSparkConf(policy: PolicyModel,
                                         clusterConfig: Config,
                                         pluginsFiles: Seq[String]): (Map[String, String], Map[String, String]) = {
    val sparkConfFromProps = PolicyHelper.getSparkConfFromProps(clusterConfig)
    val sparkConfFromPolicy = PolicyHelper.getSparkConfigFromPolicy(policy)
    val submitArgumentsFromProps = submitArgsFromProps(clusterConfig)
    val sparkConfFromSubmitArgumentsProps = submitArgsToConf(submitArgumentsFromProps)
    val submitArgumentsFromPolicy = submitArgsFromPolicy(policy.sparkSubmitArguments)
    val sparkConfFromSubmitArgumentsPolicy = submitArgsToConf(submitArgumentsFromPolicy)

    (addSupervisedArgument(addKerberosArguments(
      submitArgsFiltered(submitArgumentsFromProps) ++ submitArgsFiltered(submitArgumentsFromPolicy))),
      addAppNameConf(addGracefulStopConf(addPluginsFilesToConf(
        sparkConfFromSubmitArgumentsProps ++ sparkConfFromProps ++
          sparkConfFromSubmitArgumentsPolicy ++ sparkConfFromPolicy,
        pluginsFiles
      ), gracefulStop(policy)), policy))
  }

  /** Protected Methods **/

  protected def addAppNameConf(sparkConfs: Map[String, String], policy: PolicyModel): Map[String, String] = {
    if (!sparkConfs.contains(SubmitAppNameConf)) {
      val format = DateTimeFormat.forPattern("yyyy/MM/dd-hh:mm:ss")
      sparkConfs ++ Map(SubmitAppNameConf -> s"${policy.name}-${format.print(DateTime.now)}")
    } else sparkConfs
  }

  protected def driverLocation(driverPath: String): String = {
    val begin = 0
    val end = 4

    Try(driverPath.substring(begin, end) match {
      case "hdfs" => "hdfs"
      case _ => "provided"
    }).getOrElse(DefaultDriverLocation)
  }

  protected def optionFromPolicyAndProperties(policyOption: Option[String],
                                              configuration: Config, configurationKey: String): String =
    policyOption.filter(_.trim.nonEmpty).getOrElse(configuration.getString(configurationKey)).trim

  protected def submitArgsFromPolicy(submitArgs: Seq[SubmitArgument]): Map[String, String] =
    submitArgs.flatMap(argument => {
      if (argument.submitArgument.nonEmpty) {
        if (!SubmitArguments.contains(argument.submitArgument))
          log.warn(s"Spark submit argument added unrecognized by Sparta.\t" +
            s"Argument: ${argument.submitArgument}\tValue: ${argument.submitValue}")
        Some(argument.submitArgument.trim -> argument.submitValue.trim)
      } else None
    }).toMap

  protected def submitArgsFromProps(clusterConfig: Config): Map[String, String] =
    toMap(DeployMode, SubmitDeployMode, clusterConfig) ++
      toMap(Name, SubmitName, clusterConfig) ++
      toMap(PropertiesFile, SubmitPropertiesFile, clusterConfig) ++
      toMap(TotalExecutorCores, SubmitTotalExecutorCores, clusterConfig) ++
      toMap(Packages, SubmitPackages, clusterConfig) ++
      toMap(Repositories, SubmitRepositories, clusterConfig) ++
      toMap(ExcludePackages, SubmitExcludePackages, clusterConfig) ++
      toMap(Jars, SubmitJars, clusterConfig) ++
      toMap(ProxyUser, SubmitProxyUser, clusterConfig) ++
      toMap(DriverJavaOptions, SubmitDriverJavaOptions, clusterConfig) ++
      toMap(DriverLibraryPath, SubmitDriverLibraryPath, clusterConfig) ++
      toMap(DriverClassPath, SubmitDriverClassPath, clusterConfig) ++
      toMap(DriverCores, SubmitDriverCores, clusterConfig) ++
      toMap(DriverMemory, SubmitDriverMemory, clusterConfig) ++
      toMap(ExecutorCores, SubmitExecutorCores, clusterConfig) ++
      toMap(ExecutorMemory, SubmitExecutorMemory, clusterConfig) ++
      // Yarn only
      toMap(YarnQueue, SubmitYarnQueue, clusterConfig) ++
      toMap(Files, SubmitFiles, clusterConfig) ++
      toMap(Archives, SubmitArchives, clusterConfig) ++
      toMap(AddJars, SubmitAddJars, clusterConfig) ++
      toMap(NumExecutors, SubmitNumExecutors, clusterConfig) ++
      toMap(Supervise, SubmitSupervise, clusterConfig)

  protected def toMap(key: String, newKey: String, config: Config): Map[String, String] =
    Try(config.getString(key)) match {
      case Success(value) =>
        Map(newKey.trim -> value.trim)
      case Failure(_) =>
        log.debug(s"The key $key was not defined in config.")
        Map.empty[String, String]
    }

  protected def submitArgsToConf(submitArgs: Map[String, String]): Map[String, String] =
    submitArgs.flatMap { case (argument, value) =>
      SubmitArgumentsToConfProperties.find { case (submitArgument, confProp) => submitArgument == argument }
        .map { case (submitArgument, confProp) => confProp -> value }
    }

  protected def submitArgsFiltered(submitArgs: Map[String, String]): Map[String, String] =
    submitArgs.filter { case (argument, value) => !SubmitArgumentsToConfProperties.contains(argument) }

  protected def addPluginsFilesToConf(sparkConfs: Map[String, String], pluginsFiles: Seq[String])
  : Map[String, String] = {
    if (pluginsFiles.exists(_.trim.nonEmpty)) {
      if (sparkConfs.contains(SubmitJarsConf))
        sparkConfs.map { case (confKey, value) =>
          if (confKey == SubmitJarsConf) confKey -> s"$value,${pluginsFiles.mkString(",")}"
          else confKey -> value
        }
      else sparkConfs ++ Map(SubmitJarsConf -> pluginsFiles.mkString(","))
    } else sparkConfs
  }

  def addGracefulStopConf(sparkConfs: Map[String, String], gracefullyStop: Boolean): Map[String, String] =
    if (gracefullyStop) sparkConfs ++ Map(SubmitGracefullyStopConf -> gracefullyStop.toString)
    else sparkConfs

  protected def addKerberosArguments(submitArgs: Map[String, String]): Map[String, String] =
    (HdfsUtils.getPrincipalName, HdfsUtils.getKeyTabPath) match {
      case (Some(principalName), Some(keyTabPath)) =>
        log.info(s"Launching Spark Submit with Kerberos security, adding principal and keyTab arguments... \n\t")
        submitArgs ++ Map(SubmitPrincipal -> principalName, SubmitKeyTab -> keyTabPath)
      case _ =>
        submitArgs
    }

  protected def addSupervisedArgument(submitArgs: Map[String, String]): Map[String, String] =
    submitArgs.flatMap { case (argumentKey, value) =>
      if (argumentKey == SubmitSupervise)
        if (value == "true") Some(SubmitSupervise -> "") else None
      else Some(argumentKey -> value)
    }
}
