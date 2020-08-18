package velonamp.peripherals

import chisel3._
import chisel3.util._

import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import velonamp.memory._
import velonamp.common._

/*
OPERATION SELECTION:
    1: transmit 1 BYTE containing request code



PROGRAM LOADING:
    1: transmit 1 BYTE containing # of nSections
    for # of nSections:
        a: transmit 4 BYTEs containing start address of section
        b: transmit 4 BYTEs containing # of bytes in section
        c: transmit # BYTES.U (specified by (3) containing section data

*/
object Loader {
    // Request codes
    def REQ_RESET = 1
    def REQ_LOAD = 2

    // Expected number of bytes
    def nSections_BYTES = 1
    def sectionStart_BYTES = ISA.REG_BYTES
    def sectionSize_BYTES = ISA.REG_BYTES
}

class Loader extends Module {
    val io = IO(new Bundle {
        val data_in = Flipped(Decoupled(UInt(8.W)))
        val mem_port = Flipped(new MemoryWriteInterface(ISA.REG_WIDTH, ISA.REG_BYTES))

        val system_reset = Output(Bool())
    })

    val s_ready :: s_reset :: s_load_section_cnt :: s_load_section_start :: s_load_section_size :: s_load_section_data :: Nil = Enum(6)

    val state = RegInit(s_ready)
    val bytesToReceive = Reg(UInt(32.W))
    val nSections = Reg(UInt((Loader.nSections_BYTES * 8).W))
    val sectionSize = Reg(UInt((Loader.sectionSize_BYTES * 8).W))
    val sectionStart = Reg(UInt((Loader.sectionStart_BYTES * 8).W))

    io.data_in.ready := 1.B
    io.mem_port.address := DontCare
    io.mem_port.data.bits := DontCare
    io.mem_port.mask := DontCare
    io.mem_port.data.valid := 0.B
    io.system_reset := 0.B

    when(state === s_ready && io.data_in.valid) {
        // Wait for request
        switch(io.data_in.bits) {
            is(Loader.REQ_RESET.U) { state := s_reset }
            is(Loader.REQ_LOAD.U) {
                state := s_load_section_cnt
                bytesToReceive := Loader.nSections_BYTES.U
            }
        }
    } .elsewhen(state === s_reset) {
        // Reset processing system
        io.system_reset := 1.B
        state := s_ready
    } .elsewhen(state === s_load_section_cnt) {
        when(io.data_in.valid && bytesToReceive != 0.U) {
            // Wait for number of nSections to have been communicated
            nSections := io.data_in.bits
            bytesToReceive := bytesToReceive - 1.U
        } .elsewhen(bytesToReceive === 0.U) {
            when(nSections != 0.U){
                state := s_load_section_start
                bytesToReceive := Loader.sectionStart_BYTES.U
                nSections := nSections - 1.U
            } .otherwise {
                state := s_ready
            }
        }
    }
    .elsewhen(state === s_load_section_start) {
        when(io.data_in.valid && bytesToReceive != 0.U) {
            // Wait for number of nSections to have been communicated
            sectionStart := io.data_in.bits ## sectionStart(31, 8)
            bytesToReceive := bytesToReceive - 1.U
        }.elsewhen(bytesToReceive === 0.U) {
            state := s_load_section_size
            bytesToReceive := Loader.sectionSize_BYTES.U
        }
    }
    .elsewhen(state === s_load_section_size) {
        when(io.data_in.valid && bytesToReceive != 0.U) {
            // Wait for number of nSections to have been communicated
            sectionSize := io.data_in.bits ## sectionSize(31, 8)
            bytesToReceive := bytesToReceive - 1.U
        } .elsewhen(bytesToReceive === 0.U) {
            state := s_load_section_data
        }
    }
    .elsewhen(state === s_load_section_data) {
        when(io.data_in.valid && sectionSize != 0.U) {
            val offset = sectionStart % ISA.REG_BYTES.U
            io.mem_port.address := sectionStart / ISA.REG_BYTES.U // Word addressed
            io.mem_port.data.bits := io.data_in.bits << (offset * 8.U)
            io.mem_port.data.valid := 1.U
            io.mem_port.mask := velonamp.util.genWriteBitMask(sectionStart, 1.U)

            sectionSize := sectionSize - 1.U
            sectionStart := sectionStart + 1.U
        } .elsewhen(sectionSize === 0.U) {
            when(nSections != 0.U) {
                // Continue loading section
                nSections := nSections - 1.U
                state := s_load_section_start
                bytesToReceive := Loader.sectionStart_BYTES.U
            } .otherwise {
                state := s_ready
            }
        }
    }
}


class LoaderTester(c: Loader) extends PeekPokeTester(c) {
    def tx(value : Int, bytes : Int) {
        var v = value
        for (i <- 0 until bytes) {
            poke(c.io.data_in.bits, v)
            poke(c.io.data_in.valid, 1.U)
            step(1)
            poke(c.io.data_in.bits, 0.U)
            poke(c.io.data_in.valid, 0.U)
            step(1)
            v >>= 8
        }
    }

    var secStart = 100
    val nSections = 2
    val secSize = 5

    tx(Loader.REQ_LOAD, 1)
    tx(nSections, Loader.nSections_BYTES)
    for (i <- 0 until nSections) {
        tx(secStart, Loader.sectionStart_BYTES)
        tx(secSize, Loader.sectionSize_BYTES)
        for(j <- 0 until secSize) {
            tx(j, 1)
        }
        secStart *= 2
    }

    step(10)
}

class LoaderSpec extends ChiselFlatSpec {
  "LoaderSpec" should "pass" in {
    Driver.execute(Array("--generate-vcd-output", "on") ,()
        => new Loader()) { c =>
      new LoaderTester(c)
    } should be(true)
  }
}