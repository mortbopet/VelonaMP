package velonamp

import chisel3._
import chisel3.util.Mux1H
import chisel3.experimental.ChiselEnum


package object util {
  // Instantiation configuration
  def F_HZ = 100000000

  def LOADER_UART_BAUD = 115200
}