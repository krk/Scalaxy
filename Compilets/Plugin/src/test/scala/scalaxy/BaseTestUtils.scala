package scalaxy.compilets
package test

import plugin._
import pluginBase._

import java.io._
import java.net.URLClassLoader

import scala.io.Source
import scala.concurrent._
import scala.concurrent.duration.Duration

import org.junit.Assert._

object Results {
  import java.io._
  import java.util.Properties
  def getPropertiesFileName(n: String) = n + ".perf.properties"
  val logs = new scala.collection.mutable.HashMap[String, (String, PrintStream, Properties)]
  def getLog(key: String) = {
    logs.getOrElseUpdate(key, {
      val logName = getPropertiesFileName(key)
      //println("Opening performance log file : " + logName)

      val logRes = getClass.getClassLoader.getResourceAsStream(logName)
      val properties = new java.util.Properties
      if (logRes != null) {
        println("Reading " + logName)
        properties.load(logRes)
      }
      (logName, new PrintStream(logName), properties)
    })
  }
  Runtime.getRuntime.addShutdownHook(new Thread { override def run {
    for ((_, (logName, out, _)) <- logs) {
      println("Wrote " + logName)
      out.close
    }
  }})
}
object BaseTestUtils {
  private var _nextId = 1
  def nextId = BaseTestUtils synchronized {
    val id = _nextId
    _nextId += 1
    id
  }
}
trait BaseTestUtils {
  import BaseTestUtils._

  implicit val baseOutDir = new File("target/testSnippetsClasses")
  baseOutDir.mkdirs

  def compilets: Seq[Compilet]
  
  def pluginDef: PluginDef = new ScalaxyPluginDefLike {
    override def compilets = Some(BaseTestUtils.this.compilets)
  }

  object SharedCompilerWithPlugins extends SharedCompiler(true, pluginDef)
  object SharedCompilerWithoutPlugins extends SharedCompiler(false, pluginDef)
  //def SharedCompilerWithPlugins = new SharedCompiler(true, pluginDef)
  //def SharedCompilerWithoutPlugins = new SharedCompiler(false, pluginDef)

  lazy val options: PluginOptions = {
    val o = pluginDef.createOptions(null)
    o.test = true
    o
  }

  def commonImports = ""

  def getSnippetBytecode(className: String, source: String, subDir: String, compiler: SharedCompiler) = {
    val src = "class " + className + " { " +
      commonImports + "\n" +
      source + //"def invoke(): Unit = {\n" + source + "\n}\n" +
      "\n}"
    //println(src)
    val outDir = new File(baseOutDir, subDir)
    outDir.mkdirs
    val srcFile = new File(outDir, className + ".scala")
    val out = new PrintWriter(srcFile)
    out.println(src)
    out.close
    new File(outDir, className + ".class").delete

    compiler.compile(
      Array(
        //"-Xprint:scalaxy-rewriter",
        //"-P:Scalaxy:compilets=scalaxy.compilets.RangeLoops",
        "-d",
        outDir.getAbsolutePath,
        srcFile.getAbsolutePath
      ) ++
      classPathArgs
    )

    val f = new File(outDir, className + ".class")
    if (!f.exists())
      throw new RuntimeException("Class file " + f + " not found !")


    val byteCodeSource = getClassByteCode(className, outDir.getAbsolutePath)
    val byteCode = byteCodeSource.mkString//("\n")
    /*
     println("COMPILED :")
     println("\t" + source.replaceAll("\n", "\n\t"))
     println("BYTECODE :")
     println("\t" + byteCode.replaceAll("\n", "\n\t"))
     */

    byteCode.
      //replaceAll(java.util.regex.Pattern.quote(className), "testClass").
      replaceAll("scala/reflect/ClassManifest", "scala/reflect/Manifest").
      replaceAll("#\\d+", "")
  }

  def ensurePluginCompilesSnippet(source: String) = {
    val (_, testMethodName) = testClassInfo
    assertNotNull(getSnippetBytecode(testMethodName, source, "temp", SharedCompilerWithPlugins))
  }
  def ensurePluginCompilesSnippetsToSameByteCode(sourcesAndReferences: Traversable[(String, String)]): Unit = {
    def flatten(s: Traversable[String]) = s.map("{\n" + _ + "\n};").mkString("\n")
    ensurePluginCompilesSnippetsToSameByteCode(flatten(sourcesAndReferences.map(_._1)), flatten(sourcesAndReferences.map(_._2)))
  }
  def ensurePluginCompilesSnippetsToSameByteCode(source: String, reference: String, allowSameResult: Boolean = false, printDifferences: Boolean = true) = {
    val (_, testMethodName) = testClassInfo

    import ExecutionContext.Implicits.global

    val withPluginFut = future {
      getSnippetBytecode(testMethodName, source, "withPlugin", SharedCompilerWithPlugins)
    }
    val expected =
      getSnippetBytecode(testMethodName, reference, "expected", SharedCompilerWithoutPlugins)

    val withoutPlugin = if (allowSameResult) null else
      getSnippetBytecode(testMethodName, source, "withoutPlugin", SharedCompilerWithoutPlugins)

    val withPlugin = Await.result(withPluginFut, Duration.Inf)

    if (printDifferences && (
        !allowSameResult && expected == withoutPlugin ||
        expected != withPlugin
    )) {
      def trans(tit: String, s: String) =
        println(tit + " :\n\t" + s.replaceAll("\n", "\n\t"))

      trans("EXPECTED", expected)
      trans("FOUND", withPlugin)
    }

    if (!allowSameResult) {
      assertTrue("Expected result already found without any plugin !!! (was the Scala compiler improved ?)", expected != withoutPlugin)
    }
    if (expected != withPlugin) {
      assertEquals(expected, withPlugin)
    }

  }
  def getClassByteCode(className: String, classpath: String) = {
    val args = Array("-c", "-classpath", classpath, className)
    val p = Runtime.getRuntime.exec("javap " + args.mkString(" "))//"javap", args)

    var err = new StringBuffer
    import ExecutionContext.Implicits.global
    future {
      import scala.util.control.Exception._
      val inputStream = new BufferedReader(new InputStreamReader(p.getErrorStream))
      var str: String = null
      //ignoring(classOf[IOException]) {
        while ({ str = inputStream.readLine; str != null }) {
          //err.synchronized {
            println(str)
            err.append(str).append("\n")
          //}
        //}
      }
    }

    val out = Source.fromInputStream(p.getInputStream).toList
    if (p.waitFor != 0) {
      Thread.sleep(100)
      sys.error("javap (args = " + args.mkString(" ") + ") failed with :\n" + err.synchronized { err.toString } + "\nAnd :\n" + out)
    }
    out
  }

  import java.io.File
  /*val outputDirectory = {
    val f = new File(".")//target/classes")
    if (!f.exists)
      f.mkdirs
    f
  }*/

  import java.io._

  val packageName = "tests"

  case class Res(withPlugin: Boolean, output: AnyRef, time: Double)
  type TesterGen = Int => (Boolean => Res)

  def fail(msg: String) = {
    println(msg)
    println()
    assertTrue(msg, false)
  }

  trait RunnableMethod {
    def apply(args: Any*): Any
  }
  abstract class RunnableCode(val pluginOptions: PluginOptions) {
    def newInstance(constructorArgs: Any*): RunnableMethod
  }

  protected def compileCodeWithPlugin(decls: String, code: String) =
    compileCode(withPlugin = true, code, "", decls, "")

  def jarPath(c: Class[_]) =      
    c.getProtectionDomain.getCodeSource.getLocation.getFile

  val classPath = Set(
    jarPath(classOf[MatchAction]),
    //jarPath(scalaxy.compilets.ForLoops.getClass),
    jarPath(classOf[plugin.ScalaxyPlugin]))
    
  def classPathArgs = Seq[String](
    //"-usejavacp",
    "-toolcp",
    classPath.mkString(File.pathSeparator),
    //"-bootclasspath",
    //(classPath ++ Set(jarPath(classOf[List[_]]))).
    //  mkString(File.pathSeparator),
    "-cp",
    classPath.mkString(File.pathSeparator))

  protected def compileCode(withPlugin: Boolean, code: String, constructorArgsDecls: String = "", decls: String = "", methodArgsDecls: String = ""): RunnableCode = {
    val (testClassName, testMethodName) = testClassInfo

    val suffixPlugin = (if (withPlugin) "Optimized" else "Normal")
    val className = "Test_" + testMethodName + "_" + suffixPlugin + "_" + nextId
    val src = "package " + packageName + "\nclass " + className + "(" + constructorArgsDecls + """) {
      """ + (if (decls == null) "" else decls) + """
      def """ + testMethodName + "(" + methodArgsDecls + ")" + """ = {
      """ + code + """
      }
    }"""

    val outputDirectory = new File("tmpTestClasses" + suffixPlugin)
    def del(dir: File): Unit = {
      val fs = dir.listFiles
      if (fs != null)
        fs foreach del

      dir.delete
    }

    del(outputDirectory)
    outputDirectory.mkdirs

    var tmpFile = new File(outputDirectory, testMethodName + ".scala")
    val pout = new PrintStream(tmpFile)
    pout.println(src)
    //println("Source = \n\t" + src.replaceAll("\n", "\n\t"))
    pout.close
    //println(src)
    val compileArgs = Array(
      "-d",
      outputDirectory.getAbsolutePath,
      tmpFile.getAbsolutePath
    ) ++ classPathArgs

    //println("Compiling '" + tmpFile.getAbsolutePath + "' with args '" + compileArgs.mkString(" ") +"'")
    val pluginOptions = (
      if (withPlugin)
        SharedCompilerWithPlugins
      else
        SharedCompilerWithoutPlugins
    ).compile(compileArgs)

    //println("CLASS LOADER WITH PATH = '" + outputDirectory + "'")
    val loader = new URLClassLoader(Array(
      outputDirectory.toURI.toURL,
      new File(CompilerMain.bootClassPath).toURI.toURL
    ))

    val parent =
      if (packageName == "")
        outputDirectory
      else
        new File(outputDirectory, packageName.replace('.', File.separatorChar))

    val f = new File(parent, className + ".class")
    if (!f.exists())
      throw new RuntimeException("Class file " + f + " not found !")

    //compileFile(tmpFile, withPlugin, outputDirectory)

    val testClass = loader.loadClass(packageName + "." + className)
    val testMethod = testClass.getMethod(testMethodName)//, classOf[Int])
    val testConstructor = testClass.getConstructors.head

    new RunnableCode(pluginOptions) {
      override def newInstance(constructorArgs: Any*) = new RunnableMethod {
        val inst =
          testConstructor.newInstance(constructorArgs.map(_.asInstanceOf[AnyRef]):_*).asInstanceOf[AnyRef]

        assert(inst != null)

        override def apply(args: Any*): Any = {
          try {
            testMethod.invoke(inst, args.map(_.asInstanceOf[AnyRef]):_*)
          } catch { case ex: Throwable =>
            ex.printStackTrace
            throw ex
          }
        }
      }
    }
  }


  private def getTesterGen(withPlugin: Boolean, decls: String, code: String) = {
    val runnableCode = compileCode(withPlugin, code, "n: Int", decls, "")

    (n: Int) => {
      val i = runnableCode.newInstance(n)
      (isWarmup: Boolean) => {
        if (isWarmup) {
          i()
          null
        } else {
          System.gc
          Thread.sleep(50)
          val start = System.nanoTime
          val o = i().asInstanceOf[AnyRef]
          val time: Double = System.nanoTime - start
          Res(withPlugin, o, time)
        }
      }
    }
  }
  def testClassInfo = {
    val testTrace = new RuntimeException().getStackTrace.filter(se => se.getClassName.endsWith("Test")).last
    val testClassName = testTrace.getClassName
    val methodName = testTrace.getMethodName
    (testClassName, methodName)
  }

  val defaultExpectedFasterFactor = Option(System.getenv(pluginDef.envVarPrefix + "MIN_PERF")).map(_.toDouble).getOrElse(0.95)
  val perfRuns = Option(System.getenv(pluginDef.envVarPrefix + "PERF_RUNS")).map(_.toInt).getOrElse(4)

  def ensureCodeWithSameResult(code: String): Unit = {
    val (testClassName, testMethodName) = testClassInfo

    val gens @ Array(genWith, genWithout) = Array(getTesterGen(true, "", code), getTesterGen(false, "", code))

    val testers @ Array(testerWith, testerWithout) = gens.map(_(-1))

    val firstRun = testers.map(_(false))
    val Array(optimizedOutput, normalOutput) = firstRun.map(_.output)

    val pref = "[" + testClassName + "." + testMethodName + "] "
    if (normalOutput != optimizedOutput) {
      fail(pref + "ERROR: Output is not the same !\n" + pref + "\t   Normal output = " + normalOutput + "\n" + pref + "\tOptimized output = " + optimizedOutput)
    }
  }
  def ensureFasterCodeWithSameResult(decls: String, code: String, params: Seq[Int] = Array(2, 10, 1000, 100000)/*10000, 100, 20, 2)*/, minFaster: Double = 1.0, nRuns: Int = perfRuns): Unit = {

    //println("Ensuring faster code with same result :\n\t" + (decls + "\n#\n" + code).replaceAll("\n", "\n\t"))
    val (testClassName, methodName) = testClassInfo

    val gens @ Array(genWith, genWithout) = Array(getTesterGen(true, decls, code), getTesterGen(false, decls, code))

    def run = params.toList.sorted.map(param => {
      //println("Running with param " + param)
      val testers @ Array(testerWith, testerWithout) = gens.map(_(param))

      val firstRun = testers.map(_(false))
      val Array(optimizedOutput, normalOutput) = firstRun.map(_.output)

      val pref = "[" + testClassName + "." + methodName + ", n = " + param + "] "
      if (normalOutput != optimizedOutput) {
        fail(pref + "ERROR: Output is not the same !\n" + pref + "\t   Normal output = " + normalOutput + "\n" + pref + "\tOptimized output = " + optimizedOutput)
      }

      val runs: List[Res] = firstRun.toList ++ (1 until nRuns).toList.flatMap(_ => testers.map(_(false)))
      def calcTime(list: List[Res]) = {
        val times = list.map(_.time)
        times.sum / times.size.toDouble
      }
      val (runsWithPlugin, runsWithoutPlugin) = runs.partition(_.withPlugin)
      val (timeWithPlugin, timeWithoutPlugin) = (calcTime(runsWithPlugin), calcTime(runsWithoutPlugin))

      (param, timeWithoutPlugin / timeWithPlugin)
    }).toMap

    val (logName, log, properties) = Results.getLog(testClassName)

    //println("Cold run...")
    val coldRun = run

    //println("Warming up...");
    // Warm up the code being benchmarked :
    {
      val testers = gens.map(_(5))
      (0 until 2500).foreach(_ => testers.foreach(_(true)))
    };

    //println("Warm run...")
    val warmRun = run


    val errors = coldRun.flatMap { case (param, coldFactor) =>
      val warmFactor = warmRun(param)
      //println("coldFactor = " + coldFactor + ", warmFactor = " + warmFactor)

      def f2s(f: Double) = ((f * 10).toInt / 10.0) + ""
      def printFacts(warmFactor: Double, coldFactor: Double) = {
        val txt = methodName + "\\:" + param + "=" + Array(warmFactor, coldFactor).map(f2s).mkString(";")
        //println(txt)
        log.println(txt)
      }
      //def printFact(factor: Double) = log.println(methodName + "\\:" + param + "=" + f2s(factor))
      val (expectedWarmFactor, expectedColdFactor) = {
      //val expectedColdFactor = {
        val p = Option(properties.getProperty(methodName + ":" + param)).map(_.split(";")).orNull
        if (p != null && p.length == 2) {
          //val Array(c) = p.map(_.toDouble)
          //val c = p.toDouble; printFact(c); c
          //log.print("# Test result (" + (if (actualFasterFactor >= f) "succeeded" else "failed") + "): ")
          val Array(w, c) = p.map(_.toDouble)
          printFacts(w, c)
          (w, c)
        } else {
          //printFact(coldFactor - 0.1); 1.0
          printFacts(warmFactor - 0.1, coldFactor - 0.1)
          (defaultExpectedFasterFactor, defaultExpectedFasterFactor)
        }
      }

      def check(warm: Boolean, factor: Double, expectedFactor: Double) = {
        val pref = "[" + testClassName + "." + methodName + ", n = " + param + ", " + (if (warm) "warm" else "cold") + "] "

        if (factor >= expectedFactor) {
          println(pref + "  OK (" + factor + "x faster, expected > " + expectedFactor + "x)")
          Nil
        } else {
          val msg = "ERROR: only " + factor + "x faster (expected >= " + expectedFactor + "x)"
          println(pref + msg)
          List(msg)
        }
      }

      check(false, coldFactor, expectedColdFactor) ++
      check(true, warmFactor, expectedWarmFactor)
    }
    try {
      if (!errors.isEmpty)
        assertTrue(errors.mkString("\n"), false)
    } finally {
      println()
    }
  }

}
