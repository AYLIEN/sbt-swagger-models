package io.grhodes.sbt.swagger.models

import io.grhodes.simple.codegen.{ BaseScalaCodegen, SimplePlayCodegen }
import io.swagger.codegen.v3.config.CodegenConfigurator
import io.swagger.codegen.v3.{ CodegenConfig, DefaultGenerator }
import org.apache.commons.io.FilenameUtils
import sbt.*
import sbt.Keys.TaskStreams

import java.nio.file.{ FileSystems, Files, Path, Paths, StandardOpenOption }
import java.util
import java.util.{ Collections, ServiceLoader }
import scala.annotation.tailrec
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using

object ModelGenerator {

  private val fileFilter: FileFilter = "*.yml" || "*.yaml" || "*.json"

  private lazy val fileSystem = FileSystems.newFileSystem(
    classOf[SimplePlayCodegen].getClassLoader.getResource("templates").toURI,
    Collections.emptyMap[String, Any]
  )

  def apply(
    streams: TaskStreams,
    srcManagedDir: File,
    srcDir: File,
    baseDir: File,
    targetDir: File,
    specFile: Option[String],
    basePkg: Option[String],
    modelPkg: Option[String],
    apiPkg: Option[String],
    generator: String,
    verbose: Boolean,
    skipRouteGeneration: Boolean
  ): scala.Seq[java.io.File] = {

    streams.log.info(s"Source: $srcDir")

    val serviceSpec = specFile.map(srcDir / _)
    val cache = streams.cacheDirectory

    streams.log.info(s"Spec: $serviceSpec")

    val cachedCompile = FileFunction.cached(
      file(cache.getAbsolutePath) / "swagger",
      inStyle = FilesInfo.lastModified,
      outStyle = FilesInfo.exists
    )(
      compile(
        log = streams.log,
        srcManagedDir = srcManagedDir,
        baseDir = baseDir,
        targetDir = targetDir,
        specFile = serviceSpec,
        basePkg = basePkg,
        modelPkg = modelPkg,
        apiPkg = apiPkg,
        generator = generator,
        verbose = verbose,
        skipRouteGeneration = skipRouteGeneration
      )
    )

    cachedCompile((srcDir ** fileFilter).get.toSet).toSeq
  }

  /**
    * Resolves a swagger-codegen config instance by its generator name. We need
    * this because the underlying loading mechanism relies on
    * `ServiceLoader.load(java.lang.Class)`, which assumes the service
    * definition file can be loaded from the current thread's context
    * class loader. For SBT plugins, this isn't the case, and so we explicitly
    * get the loader of the `CodegenConfig` class and then attempt to load the
    * service loader directly. Assuming we can load it, we then search all
    * instances for a config by the given name.
    */
  private def resolveConfigFromName(name: String): Option[CodegenConfig] = {
    val cls = classOf[CodegenConfig]
    val it = ServiceLoader.load(cls, cls.getClassLoader).iterator()

    @tailrec
    def loop(): Option[CodegenConfig] = {
      if (it.hasNext) {
        val config = it.next()
        if (config.getName == name) Some(config) else loop()
      } else {
        None
      }
    }

    loop()
  }

  private def compile(
    log: Logger,
    srcManagedDir: File,
    baseDir: File,
    targetDir: File,
    specFile: Option[File],
    basePkg: Option[String],
    modelPkg: Option[String],
    apiPkg: Option[String],
    generator: String,
    verbose: Boolean,
    skipRouteGeneration: Boolean
  )(in: Set[File]) = {
    in.foreach { swaggerFile =>
      log.debug(s"Swagger spec: $swaggerFile")
      log.debug(s"Spec file: $specFile")
      log.debug(s"Is spec: ${specFile.exists(_.asFile == swaggerFile.asFile)}")
      if (specFile.isEmpty || specFile.exists(_.asFile == swaggerFile.asFile)) {
        log.info(s"[$generator] Generating source files from Swagger spec: $swaggerFile")
        val generatorConfig =
          resolveConfigFromName(generator).getOrElse(sys.error(s"Failed to locate a generator by name $generator!"))
        runCodegen(
          swaggerFile = swaggerFile.toURI.toString,
          srcManagedDir = srcManagedDir,
          baseDir = baseDir,
          targetDir = targetDir,
          generator = generatorConfig,
          basePkg = basePkg,
          modelPkg = modelPkg,
          apiPkg = apiPkg,
          verbose = verbose,
          skipRouteGeneration = skipRouteGeneration
        )
      }
    }

    (srcManagedDir ** "*.scala").get.toSet
  }

  private def runCodegen(
    swaggerFile: String,
    srcManagedDir: File,
    baseDir: File,
    targetDir: File,
    generator: CodegenConfig,
    basePkg: Option[String],
    modelPkg: Option[String],
    apiPkg: Option[String],
    verbose: Boolean,
    skipRouteGeneration: Boolean
  ): util.List[File] = {
    val configurator = new CodegenConfigurator()
    configurator.setVerbose(verbose)

    /* The `inputSpec` within the configurator can be a stringified URI.
       However it seems if you pass it a file:/ URI, it fails because it tests
       for a prefix of "file://". Therefore if we're dealing with a file URI,
       we stringify it to conform to that expectation. */
    val specLocation = if (swaggerFile.toLowerCase.startsWith("file:")) {
      s"file:///${swaggerFile.substring(6)}"
    } else {
      swaggerFile
    }

    configurator.setLang(generator.getClass.getName)
    configurator.setInputSpecURL(specLocation)
    configurator.setOutputDir(baseDir.toString)
    configurator.addAdditionalProperty(BaseScalaCodegen.ARG_SRC_MANAGED_DIRECTORY, srcManagedDir.toString)
    configurator.addAdditionalProperty(SimplePlayCodegen.ARG_SKIP_ROUTES_GENERATION, skipRouteGeneration.toString)

    // determine the scala package of the generated code by the
    // filename of the OpenAPI specification
    val invokerPackage = basePkg.getOrElse(FilenameUtils.getBaseName(swaggerFile))
    val apiPackage = apiPkg.orElse(Option(invokerPackage).filter(_.nonEmpty).map(_ + ".api"))
    val modelPackage = modelPkg.orElse(Option(invokerPackage).filter(_.nonEmpty).map(_ + ".model"))

    configurator.setInvokerPackage(invokerPackage)
    configurator.setModelPackage(modelPackage.getOrElse("model"))
    configurator.setApiPackage(apiPackage.getOrElse("api"))

    val templatesTargetPath = (targetDir / "templates" / generator.getName).toPath.toAbsolutePath

    extractTemplatesFromResources(generator, templatesTargetPath)

    // sets a custom template dir to one which we copied over to /target
    configurator.setTemplateDir(templatesTargetPath.toString)

    val input = configurator.toClientOptInput

    // configurator.toClientOptInput() attempts to read the file and parse an
    // OpenAPI specification. If it fails for any reason, that reason is
    // swallowed and null is returned. For now, this is our best bet at
    // providing a slightly less hostile error message, but we should look
    // manually constructing ClientOptInput, so we can control how parsing
    // errors are reported.
    Option(input.getOpenAPI)
      .map(_ => new DefaultGenerator().opts(input).generate())
      .getOrElse(sys.error(s"Failed to load OpenAPI specification from $swaggerFile! Is it valid?"))
  }

  /**
    * Swagger has issues with reading template files which are stored in jar resources at the classpath.
    * Therefore, we copy them to a directory in /target so that we can read the template from a regular directory.
    */
  private def extractTemplatesFromResources(generator: CodegenConfig, templatesTargetPath: Path): Unit = {
    val templatesResource = s"templates/${generator.getName}"

    Files.createDirectories(templatesTargetPath)

    synchronized {
      Files.list(fileSystem.getPath(templatesResource)).iterator().asScala.foreach { path =>
        val target = Paths.get(templatesTargetPath.toString, path.getFileName.toString)
        Using(Source.fromInputStream(Files.newInputStream(path)))(source =>
          Files.write(
            target,
            source.getLines().toVector.asJava,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE
          )
        )
      }
    }
  }
}
