package com.github.j5ik2o.akka.persistence.s3.utils

import scala.reflect.runtime.universe

object ClassUtil {

  def create[T](clazz: Class[T], className: String): T = {
    val runtimeMirror = universe.runtimeMirror(clazz.getClassLoader)
    val classSymbol = runtimeMirror.staticClass(className)
    val classMirror = runtimeMirror.reflectClass(classSymbol)
    val constructorMethod =
      classSymbol.typeSignature.decl(universe.termNames.CONSTRUCTOR).asMethod
    val constructorMirror = classMirror.reflectConstructor(constructorMethod)
    val newInstance = constructorMirror()
    newInstance.asInstanceOf[T]
  }

}
