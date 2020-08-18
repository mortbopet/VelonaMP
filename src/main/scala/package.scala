package velonamp

import chisel3._
import chisel3.util.Mux1H
import chisel3.experimental.ChiselEnum

import velonamp.common.ISA

package object util {
  // Instantiation configuration
  def F_HZ = 100000000
  def LOADER_UART_BAUD = 11520000
  def MASTER_INIT_PC = 0x0


  /** Generates a bitmask for write-masking byte accessed memory which is
   * accessible in ISA.REG_BYTES-sized words.
   *
   * @param address byte-aligned address
   * @param access_len length (in bytes) of write access
   * @return bitmask with bits set according to request
   */
  def genWriteBitMask(address : UInt, access_len : UInt) : Vec[Bool] = {
    val offset = address % ISA.REG_BYTES.U
    val mask = Wire(Vec(ISA.REG_BYTES, Bool()))
    (0 until ISA.REG_BYTES).map{_.U}.foreach{i =>
      when (offset < i) { mask(i) := 0.B }
      .elsewhen(access_len > (i - offset)) { mask(i) := 1.B }
      .otherwise {mask(i) := 0.B }
    }
    mask
  }
}