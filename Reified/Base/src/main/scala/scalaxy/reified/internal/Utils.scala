package scalaxy.reified.internal

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox

import scalaxy.reified.ReifiedValue

/**
 * Internal utility methods used by Scalaxy/Reified's implementation.
 * Should not be called by users of the library, API might change even in minor / patch versions.
 */
object Utils {

  private[reified] def newExpr[A](tree: Tree): Expr[A] = {
    Expr[A](
      currentMirror,
      CurrentMirrorTreeCreator(tree))
  }

  /**
   * Internal method to type-check a runtime AST (API might change at any future version).
   * This might transform the AST's structure (e.g. introduce TypeApply nodes), and might prevent
   * toolboxes from compiling it (requiring a call to `scala.tools.ToolBox.resetAllAttrs` prior
   * to compiling with `scala.tools.ToolBox.compile`).
   */
  def typeCheck[A](expr: Expr[A]): Expr[A] = {
    newExpr[A](typeCheck(expr.tree))
  }

  private[reified] val optimisingToolbox = currentMirror.mkToolBox(options = "-optimise")

  private[reified] def getModulePath(u: scala.reflect.api.Universe)(moduleSym: u.ModuleSymbol): u.Tree = {
    import u._
    val elements = moduleSym.fullName.split("\\.").toList
    def rec(root: Tree, sub: List[String]): Tree = sub match {
      case Nil => root
      case name :: rest => rec(Select(root, name: TermName), rest)
    }
    rec(Ident(elements.head: TermName), elements.tail)
  }

  private[reified] def resolveModulePaths(u: scala.reflect.api.Universe)(root: u.Tree): u.Tree = {
    import u._
    new Transformer {
      override def transform(tree: Tree) = tree match {
        case Ident() if tree.symbol != null && tree.symbol.isModule =>
          //println("REPLACING " + tree + " BY MODULE PATH")
          getModulePath(u)(tree.symbol.asModule)
        case _ =>
          super.transform(tree)
      }
    }.transform(root)
  }

  private def typeCheck(tree: Tree, pt: Type = WildcardType): Tree = {
    if (tree.tpe != null && tree.tpe != NoType)
      tree
    else {
      try {
        optimisingToolbox.typeCheck(tree, pt)
      } catch {
        case ex: Throwable =>
          throw new RuntimeException(s"Failed to typeCheck($tree, $pt): $ex", ex)
      }
    }
  }
}