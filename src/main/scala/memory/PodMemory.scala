package velonamp.memory

import chisel3._
import chisel3.util._

import velonamp.util._
import velonamp.common.{ABI, ISA}

import scala.collection.mutable

class MemoryReadInterface(val n_addr: Int, val n_data: Int) extends Bundle {
  val address  = Input(UInt(n_addr.W))
  val data_out = Decoupled(UInt(n_data.W))
}

class MemoryReadWriteInterface(n_addr: Int, n_data: Int)
    extends MemoryReadInterface(n_addr, n_data) {
  val data_in = Flipped(Decoupled(UInt(n_data.W)))
}

class PodMemory(n: Int, size: Int) extends Module {
  val io = IO(new Bundle {
    val ports =
      Vec(n, new MemoryReadWriteInterface(ISA.REG_WIDTH, ISA.REG_WIDTH))
  })

  val MEM_TOTAL = size
  val MEM_PER   = MEM_TOTAL / n
  val mems = Seq.fill(n)(SyncReadMem(MEM_PER, UInt(ISA.REG_WIDTH.W)))

  def ramAccess(mem: SyncReadMem[UInt], conn: MemoryReadWriteInterface) = {
    // Constant register access detection
    val cRegAccessMatch = ABI.ConstantRegisters.map {
        case (caddr, cvalue) =>
          (caddr === conn.address, cvalue)
      }

      // Read
      when(conn.data_out.ready) {
        when(listContains(cRegAccessMatch.map { _._1 }, true.B)) {
          conn.data_out.bits := Mux1H(cRegAccessMatch)
        }. otherwise {
            conn.data_out.bits := mem.read(conn.address)
        }
        conn.data_out.valid := true.B
      } .otherwise {
        conn.data_out.valid := false.B
      }

      // Write
      when(conn.data_in.valid) {
        when(!listContains(cRegAccessMatch.map { _._1 }, true.B)) {
            // Ignore writes to constant registers
            mem.write(conn.address, conn.data_in.bits)
          }
          conn.data_in.ready := true.B
        } .otherwise {
          conn.data_in.ready := false.B
      }
  }

  io.ports.zipWithIndex.map{case (port, i) => ramAccess(mems(i), port)}
}
