package io.grhodes.sbt.swagger.models

import sbt.*
import sbt.Keys.*
import sbt.plugins.JvmPlugin

object SbtSwaggerModelsPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = JvmPlugin

  object autoImport {
    val swaggerGenerateModels: TaskKey[Seq[File]] =
      taskKey[Seq[java.io.File]]("Generate models for swagger APIs")
    val swaggerSourceDirectory: SettingKey[File] =
      settingKey[File]("Directory containing input swagger files")
    val swaggerOutputPackage: SettingKey[Option[String]] =
      settingKey[Option[String]]("Package into which the source code should be generated")
    val swaggerApiPackage: SettingKey[Option[String]] =
      settingKey[Option[String]]("Package into which the api source code should be generated")
    val swaggerModelPackage: SettingKey[Option[String]] =
      settingKey[Option[String]]("Package into which the model source code should be generated")
    val swaggerSpecFilename: SettingKey[Option[String]] =
      settingKey[Option[String]](
        "Optionally specify the service swagger spec file (all other swagger files are client specs)"
      )
    val swaggerGenerator: SettingKey[String] =
      settingKey[String]("The swagger-codegen generator to use")
    val swaggerGeneratorVerbose: SettingKey[Boolean] =
      settingKey[Boolean]("Turn on verbose for swagger-codegen generator")
    val swaggerSkipRouteGeneration: SettingKey[Boolean] =
      settingKey[Boolean]("Turn off the generation of Play routes in simple-play generator")
  }

  import autoImport.*

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    swaggerSourceDirectory := (Compile / resourceDirectory).value,
    swaggerOutputPackage := None,
    swaggerModelPackage := None,
    swaggerApiPackage := None,
    swaggerSpecFilename := Some("api.yaml"),
    swaggerGenerator := "simple-scala",
    swaggerGeneratorVerbose := false,
    swaggerSkipRouteGeneration := false,
    swaggerGenerateModels := ModelGenerator(
      streams = streams.value,
      srcManagedDir = (Compile / sourceManaged).value / "swagger",
      srcDir = (swaggerGenerateModels / swaggerSourceDirectory).value,
      baseDir = (Compile / baseDirectory).value,
      targetDir = (Compile / target).value,
      specFile = (swaggerGenerateModels / swaggerSpecFilename).value,
      basePkg = (swaggerGenerateModels / swaggerOutputPackage).value,
      modelPkg = (swaggerGenerateModels / swaggerModelPackage).value,
      apiPkg = (swaggerGenerateModels / swaggerApiPackage).value,
      generator = swaggerGenerator.value,
      verbose = swaggerGeneratorVerbose.value,
      skipRouteGeneration = swaggerSkipRouteGeneration.value
    ),
    Compile / sourceGenerators += swaggerGenerateModels.taskValue
  )

}
