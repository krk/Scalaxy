package scalaxy.enums

import scala.language.experimental.macros

import scala.reflect._
import scala.reflect.macros.Context

package object internal {

  private def getNames(c: Context): List[String] = {
    import c.universe._

    val valueType = typeOf[enum#value]//weakTypeTag[V].tpe
    def isEnumValue(s: Symbol) = {
      if (s.isModule) {
        println("valueType = " + valueType + ", object = " + s)
        val t = s.asModule.moduleClass.asType.toType
        println("s.asModule.moduleClass.asType.toType = " + t)
        println("\t" + (t <:< valueType))
      }
      s.isModule &&
        s.asModule.moduleClass.asType.toType <:< valueType ||
      s.isTerm && s.asTerm.isGetter &&
        s.asTerm.accessed.typeSignature.normalize <:< valueType
    }


    // val ModuleDef(_, _, Template(_, _, body)) = 
    //   c.typeCheck(c.enclosingClass, withMacrosDisabled = true)

    // val namesToPos = (body.collect {
    //   case vd @ ValDef(_, name, tpt, _)
    //       if namesSet(name.toString.trim) =>
    //     name.toString.trim -> vd.pos
    // }).toMap

    // println("names = " + names.mkString(", "))
    // println("names to pos = " + namesToPos)
    // println("valueType = " + valueType)
    // println("c.prefix = " + c.prefix)
    // println("c.macroApplication = " + c.macroApplication)
    // println("\tpos = " + c.macroApplication.pos)
    // println("c.enclosingClass = " + c.enclosingClass)
    // println("\tsym = " + c.enclosingClass.symbol)
    // println("c.enclosingMethod = " + c.enclosingMethod)
    // println("c.enclosingPosition = " + c.enclosingPosition)
    // println("c.enclosingImplicits = " + c.enclosingImplicits)

    c.enclosingClass.symbol.typeSignature.members.sorted.toList collect {
      case m if isEnumValue(m) =>
        m.name.toString
    }
  }

  private def newArray(c: Context)(elementType: c.universe.Type, elements: List[c.universe.Tree]): c.universe.Tree = {
    import c.universe._

    val arraySym = rootMirror.staticModule("scala.Array")
    Apply(
      TypeApply(
        Select(
          Ident(arraySym),
          "apply": TermName
        ),
        List(TypeTree(elementType))
      ),
      elements
    )
  }

  private def getModulePath(u: scala.reflect.api.Universe)(moduleSym: u.ModuleSymbol): u.Tree = {
    import u._
    def rec(relements: List[String]): Tree = relements match {
      case name :: Nil =>
        Ident(name: TermName)
      case ("`package`") :: rest =>
        //rec(rest)
        Select(rec(rest), "package": TermName)
      case name :: rest =>
        Select(rec(rest), name: TermName)
    }
    rec(moduleSym.fullName.split("\\.").reverse.toList)
  }

  def enumValueNames[T: c.WeakTypeTag](c: Context): c.Expr[T] = {
    import c.universe._

    val names = getNames(c)
    println("names = " + names)
    try {
      val res =
        Apply(
          Select(
            New(TypeTree(weakTypeTag[T].tpe)),
            nme.CONSTRUCTOR
          ),
          List(
            newArray(c)(
              typeOf[String],
              names.map(name => Literal(Constant(name)))
            ),
            Function(
              Nil,
              newArray(c)(
                typeOf[AnyRef],
                names.map(name => {
                  Select(
                    getModulePath(c.universe)(c.enclosingClass.symbol.asModule),
                    name: TermName
                  )
                })
              )
            )
          )
        )
      println("Res = " + res)
      c.Expr[T](
        c.typeCheck(res, weakTypeTag[T].tpe)
      )
    } catch { case ex: Throwable =>
      ex.printStackTrace(System.out);
      throw ex
    }
  }

  def enumValueData[T: c.WeakTypeTag](c: Context): c.Expr[T] = {
    import c.universe._

    // reify(enum.this.nextEnumValueData)
    try {
      val res = c.Expr[T](
        // Apply(
        c.typeCheck(
          Ident("nextEnumValueData": TermName),
          // Select(
          //   Ident(c.enclosingClass.symbol),
          //   // This("enum": TypeName),
          //   "nextEnumValueData": TermName
          // ),
          weakTypeTag[T].tpe
        )
          // Nil
        // )
      )
      println("res = " + res)
      res
    } catch { case ex: Throwable =>
      ex.printStackTrace(System.out);
      throw ex
    }
  }
}
