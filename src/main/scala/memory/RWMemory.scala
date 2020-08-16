package velonamp.memory

import chisel3._
import chisel3.util._

import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import velonamp.util._
import velonamp.common.{ABI, ISA}

import scala.collection.mutable

class MemoryReadInterface(val w_addr: Int, val w_data: Int) extends Bundle {
  val address = Input(UInt(w_addr.W))
  val data    = Decoupled(UInt(w_data.W))
}

class MemoryWriteInterface(val w_addr: Int, val w_data: Int) extends Bundle {
  val address = Input(UInt(w_addr.W))
  val data    = Flipped(Decoupled(UInt(w_data.W)))
}

class MemoryReadWriteInterface(val w_addr: Int, val w_data: Int)
    extends Bundle {
  val read  = new MemoryReadInterface(w_addr, w_data)
  val write = new MemoryWriteInterface(w_addr, w_data)
}

class ROMemory(val w_addr: Int, val w_data: Int, val size: Int) extends Module {
  val io = IO(new MemoryReadInterface(w_addr, w_data))

  // Constant register access detection
  val readFromConstantRegister = ABI.ConstantRegisters.map {
    case (caddr, cvalue) =>
      (caddr === io.address, cvalue)
  }

  val mem = SyncReadMem(size, UInt(w_data.W))

  // Read
  when(io.data.ready) {
    io.data.bits := mem.read(io.address)
    io.data.valid := true.B
  }.otherwise {
    io.data.valid := false.B
    io.data := DontCare
  }
}

class RWMemory(w_addr: Int, w_data: Int, size: Int) extends Module {
  val io = IO(new MemoryReadWriteInterface(w_addr, w_data))
  val mem = SyncReadMem(size, UInt(w_data.W))
  val writeToReadAddr = io.read.address === io.write.address && io.write.data.valid

  val doForwardWrite = RegNext(writeToReadAddr)
  val delayedWriteData = RegNext(io.write.data.bits)


  io.read.data.bits := Mux(doForwardWrite, delayedWriteData, mem.read(io.read.address))
  io.read.data.valid := true.B
  io.write.data.ready := false.B
  when(io.write.data.valid) {
    mem.write(io.write.address, io.write.data.bits)
  }
  io.write.data.ready := true.B
}

class RWMemoryTester(c: RWMemory) extends PeekPokeTester(c) {

  val addr    = 42
  val value_1 = 123
  val value_2 = value_1 + 1

  poke(c.io.write.address, addr)
  poke(c.io.read.address, addr)

  // Write
  poke(c.io.write.data.bits, value_1)
  poke(c.io.write.data.valid, true)
  step(1)
  poke(c.io.write.data.valid, false)

  // read
  poke(c.io.read.data.ready, true)
  step(1)
  expect(c.io.read.data.bits, value_1)

  // Simultanious read/write
  poke(c.io.read.data.ready, true)
  poke(c.io.write.data.valid, true)
  poke(c.io.write.data.bits, value_2)
  step(1)
  expect(c.io.read.data.bits, value_2)
  step(1)
  expect(c.io.read.data.bits, value_2)
}

class PodMemorySpec extends ChiselFlatSpec {
  "PodMemory" should "pass" in {
    Driver(() => new RWMemory(ISA.REG_WIDTH, ISA.REG_WIDTH, 256)) { c =>
      new RWMemoryTester(c)
    } should be(true)
  }
}
