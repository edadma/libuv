package com.brisksn.libuv

import scala.scalanative.unsafe._

@link("uv")
@extern
object LibUV {
  type TimerHandle = Ptr[Byte]
  type PipeHandle  = Ptr[Ptr[Byte]]

  type Loop    = Ptr[Byte]
  type TimerCB = CFuncPtr1[TimerHandle, Unit]

  def uv_default_loop(): Loop              = extern
  def uv_loop_size(): CSize                = extern
  def uv_is_active(handle: Ptr[Byte]): Int = extern
  def uv_handle_size(h_type: Int): CSize   = extern
  def uv_req_size(r_type: Int): CSize      = extern

  def uv_timer_init(loop: Loop, handle: TimerHandle): Int                                = extern
  def uv_timer_start(handle: TimerHandle, cb: TimerCB, timeout: Long, repeat: Long): Int = extern
  def uv_timer_stop(handle: TimerHandle): Int                                            = extern

  def uv_run(loop: Loop, runMode: Int): Int = extern

  def uv_strerror(err: Int): CString = extern
  def uv_err_name(err: Int): CString = extern
}
