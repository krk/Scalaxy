# Scalaxy/MacroExtensions

New trivial syntax to define class enrichments as macros ([BSD-licensed](https://github.com/ochafik/Scalaxy/blob/master/LICENSE).

Scalaxy/MacroExtensions's compiler plugin supports the following syntax:
```scala
@extend(Int) def str1: String = self.toString
@extend(Int) def str2(quote: String): String = quote + self + quote
@extend(Int) def str3: String = macro {
  println("Extension macro is executing!") 
  reify(self.splice.toString)
}
```
Which allows calls such as:
```scala
println(1.str1)
println(2.str2("'"))
println(3.str3)
```
This is done by rewriting the `@extend` declarations above during compilation of the extensions.
In the case of `str2`, this yields the following:
```scala
import scala.language.experimental.macros
implicit class scalaxy$extensions$str2$1(self: Int) {
  def str2(quote: String) = macro scalaxy$extensions$str2$1.str
}
object scalaxy$extensions$str$1 {
  def str2(c: scala.reflect.macros.Context)
          (quote: c.Expr[String]): c.Expr[String] = {
    import c.universe._
    val Apply(_, List(selfTree$1)) = c.prefix.tree
    val self = c.Expr[String](selfTree$1)
    {
      reify(quote.splice + self.splice + quote.splice)
    }
  }
}
```

# Usage

If you're using `sbt` 0.12.2+, just put the following lines in `build.sbt`:
```scala
// Only works with 2.10.0+
scalaVersion := "2.10.0"

autoCompilerPlugins := true

addCompilerPlugin("com.nativelibs4java" %% "scalaxy-macro-extensions" % "0.3-SNAPSHOT")

// Scalaxy/Extensions snapshots are published on the Sonatype repository.
resolvers += Resolver.sonatypeRepo("snapshots")
```

General macro rules regarding compilation apply: you cannot use macros from the same compilation unit that defines them.

# Hack

Test with:
```
sbt "project scalaxy-extensions" "run examples/extension.scala -Xprint:scalaxy-extensions"
sbt "project scalaxy-extensions" "run examples/run.scala"
```

# Known Issues

- Default parameter values are not supported (due to macros not supporting them)
- Doesn't check macro extensions are defined in publicly available static objects (but compiler does)