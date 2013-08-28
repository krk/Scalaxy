package scalaxy.js


@JavaScript
@global
object Run {

  // val emptyArr = Array[Object]()
  val arr = Array(1, 2, 3, 4)
  val arr2 = new Array[Int](10)

  val emptyObj = Map[String, Any]()
  val obj = Map[String, Any]()

  val pair = (1, 2)
  val obj2 = Map(pair, "bleh" -> 1)

  val obj3 = Map(1 -> 2, 2 -> 3)

  println("This is run directly!")

  class Sub {
    println("Creating a sub class")
  }
  println(new Sub)

  // class Sub(val x: Int) {
  //   println("Creating a sub class with x = " + x)
  // }
  // println(new Sub(10))
}
