package com.brisksn.libuv

import scala.scalanative.libc.stdio.{vprintf, vsprintf}
import scala.scalanative.unsafe.{CString, CVarArg, Zone, toCString, toCVarArgList}

object stdio {

  def printf(format: String, args: CVarArg*): Unit =
    Zone { implicit z =>
      vprintf(toCString(format), toCVarArgList(args.toSeq))
    }

  def sprintf(str: CString, format: String, args: CVarArg*): Int =
    Zone { implicit z =>
      vsprintf(str, toCString(format), toCVarArgList(args.toSeq))
    }

}
