package velonamp

import chisel3._
import chisel3.util.Mux1H
import chisel3.experimental.ChiselEnum

import velonamp.common.ISA

package object util {

  // ================================ Memory map ===============================
  def ADDRESS_IMEM = 0x00000000
  def ADDRESS_DMEM = 0x00100000

  // Hardware peripherals are placed in an uncacheable address space
  def UNCACHEABLE_START = 0x10000000

  def ADDRESS_LED       = UNCACHEABLE_START
  def ADDRESS_SWITCHES  = 0x10000004

  def UNCACHEABLE_END   = ADDRESS_SWITCHES + 4

  // ======================= Instantiation configuration =======================
  def N_CORES          = 1 /* Number of VelonaCore's*/
  def F_HZ             = 100000000 /* Board clock freuquency */
  def LOADER_UART_BAUD = 11520000 /* Baud rate of program loder UART */
  def MASTER_INIT_PC   = ADDRESS_IMEM /* Initial PC for master core */
  def N_LEDS           = 16
  def N_SWITHCES       = 16
  def N_BUTTONS        = 4

  /** Generates a bitmask for write-masking byte accessed memory which is
    * accessible in ISA.REG_BYTES-sized words.
    *
    * @param address byte-aligned address
    * @param access_len length (in bytes) of write access
    * @return bitmask with bits set according to request
    */
  def genWriteBitMask(address: UInt, access_len: UInt): Vec[Bool] = {
    val offset = address % ISA.REG_BYTES.U
    val mask   = Wire(Vec(ISA.REG_BYTES, Bool()))
    (0 until ISA.REG_BYTES).map { _.U }.foreach { i =>
      when(offset < i) { mask(i) := 0.B }
        .elsewhen(access_len > (i - offset)) { mask(i) := 1.B }
        .otherwise { mask(i) := 0.B }
    }
    mask
  }
}
