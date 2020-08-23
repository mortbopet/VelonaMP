package velonamp.peripherals

import chisel3._
import chisel3.util._

import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import velonamp.memory._
import velonamp.common._
import velonamp.interconnect._

/*
OPERATION SELECTION:
    1: transmit 1 BYTE containing request code



PROGRAM LOADING:
    1: transmit 1 BYTE containing # of nsections_reg
    for # of nsections_reg:
        a: transmit 4 BYTEs containing start address of section
        b: transmit 4 BYTEs containing # of bytes in section
        c: transmit # BYTES.U (specified by (3) containing section data

*/
object Loader {
    // Request codes
    def REQ_RESET = 1
    def REQ_LOAD = 2

    // Expected number of bytes
    def nsections_reg_BYTES = 1
    def secstart_reg_BYTES = ISA.REG_BYTES
    def secsize_reg_BYTES = ISA.REG_BYTES
}

class Loader extends Module {
    val io = IO(new Bundle {
        val data_in = Flipped(Decoupled(UInt(8.W)))
        val ocp_interface = new OCPMasterInterface()

        val system_reset = Output(Bool())
    })

    val s_ready :: s_reset :: s_load_section_cnt :: s_load_section_start :: s_load_section_size :: s_load_section_data :: Nil = Enum(6)

     // System is per default kept in reset mode until loader explicitly
     // pulls down reset signal
    val sys_reset_reg = RegInit(1.B)
    val has_program_reg = RegInit(0.B)
    val state = RegInit(s_ready)
    val bytes_to_rec_reg = Reg(UInt(32.W))
    val nsections_reg = Reg(UInt((Loader.nsections_reg_BYTES * 8).W))
    val secsize_reg = Reg(UInt((Loader.secsize_reg_BYTES * 8).W))
    val secstart_reg = Reg(UInt((Loader.secstart_reg_BYTES * 8).W))

    io.ocp_interface.master.bits.mAddr := DontCare
    io.ocp_interface.master.bits.mData := DontCare
    io.ocp_interface.master.bits.mByteEn := DontCare
    io.ocp_interface.master.bits.mCmd := OCP.MCmd.idle.U
    io.ocp_interface.master.valid := 0.B

    // System is forced to reset during any other state but s_ready. This ensures
    // that the bus will be freely accessible to load a program into memory.
    io.system_reset := sys_reset_reg || ~has_program_reg
    sys_reset_reg := Mux(state === s_ready, 0.B, 1.B)


    io.data_in.ready := 1.B
    when(state === s_ready && io.data_in.valid) {
        // Wait for request
        switch(io.data_in.bits) {
            is(Loader.REQ_RESET.U) {
                state := s_reset
            }
            is(Loader.REQ_LOAD.U) {
                state := s_load_section_cnt
                bytes_to_rec_reg := Loader.nsections_reg_BYTES.U
            }
        }
    } .elsewhen(state === s_reset) {
        state := s_ready
    } .elsewhen(state === s_load_section_cnt) {
        when(io.data_in.valid && bytes_to_rec_reg =/= 0.U) {
            // Wait for number of nsections_reg to have been communicated
            nsections_reg := io.data_in.bits
            bytes_to_rec_reg := bytes_to_rec_reg - 1.U
        } .elsewhen(bytes_to_rec_reg === 0.U) {
            when(nsections_reg =/= 0.U){
                state := s_load_section_start
                bytes_to_rec_reg := Loader.secstart_reg_BYTES.U
                nsections_reg := nsections_reg - 1.U
            } .otherwise {
                state := s_ready
            }
        }
    }
    .elsewhen(state === s_load_section_start) {
        when(io.data_in.valid && bytes_to_rec_reg =/= 0.U) {
            // Wait for number of nsections_reg to have been communicated
            secstart_reg := io.data_in.bits ## secstart_reg(Loader.secstart_reg_BYTES * 8 - 1, 8)
            bytes_to_rec_reg := bytes_to_rec_reg - 1.U
        }.elsewhen(bytes_to_rec_reg === 0.U) {
            state := s_load_section_size
            bytes_to_rec_reg := Loader.secsize_reg_BYTES.U
        }
    }
    .elsewhen(state === s_load_section_size) {
        when(io.data_in.valid && bytes_to_rec_reg =/= 0.U) {
            // Wait for number of nsections_reg to have been communicated
            secsize_reg := io.data_in.bits ## secsize_reg(31, 8)
            bytes_to_rec_reg := bytes_to_rec_reg - 1.U
        } .elsewhen(bytes_to_rec_reg === 0.U) {
            state := s_load_section_data
        }
    }
    .elsewhen(state === s_load_section_data) {
        io.ocp_interface.master.valid := 1.B // Request bus access
        when(secsize_reg =/= 0.U) { // Not all of the section has been written
            when( io.data_in.valid && io.ocp_interface.master.ready) {
                val offset = secstart_reg % ISA.REG_BYTES.U
                // Each byte is written separately by masking the appropriate byte
                // within the word currently written to
                io.ocp_interface.master.bits.mAddr := secstart_reg & ~(0x3.U((Loader.secstart_reg_BYTES * 8).W))
                io.ocp_interface.master.bits.mData := io.data_in.bits << (offset * 8.U)
                io.ocp_interface.master.bits.mCmd := OCP.MCmd.write.U
                io.ocp_interface.master.bits.mByteEn := velonamp.util.genWriteBitMask(secstart_reg, 1.U)
            }
            when(io.ocp_interface.slave.sResp === OCP.SResp.dva.U) {
                secsize_reg := secsize_reg - 1.U
                secstart_reg := secstart_reg + 1.U
            }
        }.elsewhen(secsize_reg === 0.U) {
            when(nsections_reg =/= 0.U) {
                // Continue loading section
                nsections_reg := nsections_reg - 1.U
                state := s_load_section_start
                bytes_to_rec_reg := Loader.secstart_reg_BYTES.U
            } .otherwise {
                // Loading finished, move to ready state, pulling system out of
                // reset.
                has_program_reg := 1.B
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
    val nsections_reg = 2
    val secSize = 5

    poke(c.io.ocp_interface.master.ready, 1) // Always grant bus
    poke(c.io.ocp_interface.slave.sResp, OCP.SResp.dva) // Always grant bus

    tx(Loader.REQ_LOAD, 1)
    tx(nsections_reg, Loader.nsections_reg_BYTES)
    for (i <- 0 until nsections_reg) {
        tx(secStart, Loader.secstart_reg_BYTES)
        tx(secSize, Loader.secsize_reg_BYTES)
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