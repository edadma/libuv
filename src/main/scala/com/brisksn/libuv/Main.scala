package com.brisksn.libuv

import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.string._
import scala.scalanative.unsigned._

import LibUV._
import LibUVConstants._

object Main {
  type ClientState = CStruct3[Ptr[Byte], CSize, CSize]

  def main(args: Array[String]): Unit = {
    println("hello!")
    serve_tcp(c"0.0.0.0", 8080, 0, 100, connectionCB)
    println("done?")
  }

  val loop = uv_default_loop()

  def serve_tcp(address: CString, port: Int, flags: Int, backlog: Int, callback: ConnectionCB): Unit = {
    val addr         = stackalloc[Byte]
    val addr_convert = uv_ip4_addr(address, port, addr)

    println(s"uv_ip4_addr returned $addr_convert")

    val handle = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]

    check_error(uv_tcp_init(loop, handle), "uv_tcp_init(server)")
    check_error(uv_tcp_bind(handle, addr, flags), "uv_tcp_bind")
    check_error(uv_listen(handle, backlog, callback), "uv_tcp_listen")
    uv_run(loop, UV_RUN_DEFAULT)
  }

  val connectionCB = new ConnectionCB {

    def apply(handle: TCPHandle, status: Int): Unit = {
      println("received connection")
      // initialize the new client tcp handle and its state
      val client = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
      check_error(uv_tcp_init(loop, client), "uv_tcp_init(client)")
      var client_state_ptr = (!client).asInstanceOf[Ptr[ClientState]]
      client_state_ptr = initialize_client_state(client)
      // accept the incoming connection into the new handle
      check_error(uv_accept(handle, client), "uv_accept")
      // set up callbacks for incoming data
      check_error(uv_read_start(client, allocCB, readCB), "uv_read_start")
    }
  }

  def initialize_client_state(client: TCPHandle): Ptr[ClientState] = {
    val client_state_ptr = malloc(sizeof[ClientState]).asInstanceOf[Ptr[ClientState]]

    println(s"allocated data at $client_state_ptr; assigning into handle storage at $client")

    val client_state_data = malloc(4096.toUInt)

    client_state_ptr._1 = client_state_data
    client_state_ptr._2 = 4096.toUInt // total
    client_state_ptr._3 = 0.toUInt    // used
    !client = client_state_ptr.asInstanceOf[Ptr[Byte]]
    client_state_ptr
  }

  val allocCB = new AllocCB {

    def apply(client: TCPHandle, size: CSize, buffer: Ptr[Buffer]): Unit = {
      println("allocating 4096 bytes")
      val buf = malloc(4096.toUInt)
      buffer._1 = buf
      buffer._2 = 4096.toUInt
    }
  }

  val readCB = new ReadCB {

    def apply(client: TCPHandle, size: CSSize, buffer: Ptr[Buffer]): Unit = {
      println(s"read $size bytes")
      var client_state_ptr = (!client).asInstanceOf[Ptr[ClientState]]
      if (size < 0) {
        send_response(client, client_state_ptr)
        println("connection is closed, shutting down")
        shutdown(client)
      } else {
        append_data(client_state_ptr, size, buffer)
        free(buffer._1)
      }
    }
  }

  def append_data(state: Ptr[ClientState], size: CSSize, buffer: Ptr[Buffer]): Unit = {
    val copy_position = state._1 + state._3
    strncpy(copy_position, buffer._1, size.toUInt)
    // be sure to update the length of the data since we have copied into it
    state._3 = state._3 + size
    println(s"client $state: ${state._3}/${state._2} bytes used")
  }
}

//    println("hello, world!")
//
//    val loop  = uv_default_loop()
//    val timer = stdlib.malloc(uv_handle_size(UV_TIMER_T))
//    val ret   = uv_timer_init(loop, timer)
//
//    println(s"uv_timer_init returned $ret")
//
//    val timerCB = CFuncPtr1.fromScalaFunction((h: TimerHandle) => println("timer fired!"))
//
//    uv_timer_start(timer, timerCB, 1000, 0)
//    println("invoking loop")
//    uv_run(loop, UV_RUN_DEFAULT)
//    println("done")
