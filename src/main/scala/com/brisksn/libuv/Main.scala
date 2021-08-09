package com.brisksn.libuv

import scala.scalanative.unsafe._
import scala.scalanative.libc._

import LibUV._, LibUVConstants._

object Main {

  def main(args: Array[String]): Unit = {
    println("hello, world!")

    val loop  = uv_default_loop()
    val timer = stdlib.malloc(uv_handle_size(UV_TIMER_T))
    val ret   = uv_timer_init(loop, timer)

    println(s"uv_timer_init returned $ret")
    uv_timer_start(timer, timerCB, 1000, 0)
    println("invoking loop")
    uv_run(loop, UV_RUN_DEFAULT)
    println("done")
  }

  val timerCB = CFuncPtr1.fromScalaFunction((h: TimerHandle) => println("timer fired!"))
}
