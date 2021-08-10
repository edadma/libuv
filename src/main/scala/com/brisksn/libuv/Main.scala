package com.brisksn.libuv

import scala.scalanative.unsafe._
import scala.scalanative.libc.stdlib._
import scala.scalanative.libc.string._
import scala.scalanative.unsigned._

import stdio._
import LibUV._
import LibUVConstants._

object Main {

  type ClientState = CStruct3[Ptr[Byte], CSize, CSize]

  def main(args: Array[String]): Unit = {
    println("hello!")
    serve_tcp(c"0.0.0.0", 8080, 0, 100, CFuncPtr2.fromScalaFunction(connectionCB))
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

  def connectionCB(handle: TCPHandle, status: Int): Unit = {
    println("received connection")
    // initialize the new client tcp handle and its state
    val client = malloc(uv_handle_size(UV_TCP_T)).asInstanceOf[TCPHandle]
    check_error(uv_tcp_init(loop, client), "uv_tcp_init(client)")
    var client_state_ptr = (!client).asInstanceOf[Ptr[ClientState]]
    client_state_ptr = initialize_client_state(client)
    // accept the incoming connection into the new handle
    check_error(uv_accept(handle, client), "uv_accept")
    // set up callbacks for incoming data
    check_error(uv_read_start(client, CFuncPtr3.fromScalaFunction(allocCB), CFuncPtr3.fromScalaFunction(readCB)),
                "uv_read_start")
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

  def allocCB(client: TCPHandle, size: CSize, buffer: Ptr[Buffer]): Unit = {
    println("allocating 4096 bytes")
    val buf = malloc(4096.toUInt)
    buffer._1 = buf
    buffer._2 = 4096.toUInt
  }

  def readCB(client: TCPHandle, size: CSSize, buffer: Ptr[Buffer]): Unit = {
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

  def append_data(state: Ptr[ClientState], size: CSSize, buffer: Ptr[Buffer]): Unit = {
    val copy_position = state._1 + state._3
    strncpy(copy_position, buffer._1, size.toUInt)
    // be sure to update the length of the data since we have copied into it
    state._3 = state._3 + size.toUInt
    println(s"client $state: ${state._3}/${state._2} bytes used")
  }

  def send_response(client: TCPHandle, state: Ptr[ClientState]): Unit = {
    val resp        = malloc(uv_req_size(UV_WRITE_REQ_T)).asInstanceOf[WriteReq]
    val resp_buffer = malloc(sizeof[Buffer]).asInstanceOf[Ptr[Buffer]]
    resp_buffer._1 = make_response(state)
    resp_buffer._2 = strlen(resp_buffer._1)
    !resp = resp_buffer.asInstanceOf[Ptr[Byte]]
    check_error(uv_write(resp, client, resp_buffer, 1, CFuncPtr2.fromScalaFunction(writeCB)), "uv_write")
  }

  def make_response(state: Ptr[ClientState]): CString = {
    val response_format = "received response:\n%s\n"
    val response_data   = malloc(response_format.length.toUInt + state._3)
    sprintf(response_data, response_format, state._1)
    response_data
  }

  def writeCB(writeReq: WriteReq, status: Int): Unit = {
    println("write completed")
    val resp_buffer = (!writeReq).asInstanceOf[Ptr[Buffer]]
    free(resp_buffer._1)
    free(resp_buffer.asInstanceOf[Ptr[Byte]])
    free(writeReq.asInstanceOf[Ptr[Byte]])
  }

  def shutdown(client: TCPHandle): Unit = {
    val shutdown_req = malloc(uv_req_size(UV_SHUTDOWN_REQ_T)).asInstanceOf[ShutdownReq]

    !shutdown_req = client.asInstanceOf[Ptr[Byte]]
    check_error(uv_shutdown(shutdown_req, client, CFuncPtr2.fromScalaFunction(shutdownCB)), "uv_shutdown")
  }

  def shutdownCB(shutdownReq: ShutdownReq, status: Int): Unit = {
    println("all pending writes complete, closing TCP connection")
    val client = (!shutdownReq).asInstanceOf[TCPHandle]
    check_error(uv_close(client, CFuncPtr1.fromScalaFunction(closeCB)), "uv_close")
    free(shutdownReq.asInstanceOf[Ptr[Byte]])
  }

  def closeCB(client: TCPHandle): Unit = {
    println("closed client connection")
    val client_state_ptr = (!client).asInstanceOf[Ptr[ClientState]]
    free(client_state_ptr._1)
    free(client_state_ptr.asInstanceOf[Ptr[Byte]])
    free(client.asInstanceOf[Ptr[Byte]])
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
