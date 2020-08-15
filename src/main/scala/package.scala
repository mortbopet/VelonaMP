package velonamp

import chisel3._
import chisel3.util.Mux1H
import chisel3.experimental.ChiselEnum


package object util {
    // Provides a synthesizable method of checking whether a list of fixed variables
    // contains a runtime-defined signal
  def listContains[T <: UInt](lst : List[T], v : T) : Bool = {
    val hits = lst.map{i => (i === v, true.B)} :+ (true.B, false.B)
    return Mux1H(hits)
  }
}