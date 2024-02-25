<div align="center" id="top"> 
  <img src="./.github/app.gif" alt="SDRAM Controller Generation" />

  &#xa0;

  <!-- <a href="https://spaceinvaders.netlify.app">Demo</a> -->
</div>

<h1 align="center">SDRAM Controller Generation</h1>

<p align="center">
  <img alt="Github top language" src="https://img.shields.io/github/languages/top/gmejiamtz/sdram_controller_generator?color=56BEB8">

  <img alt="Github language count" src="https://img.shields.io/github/languages/count/gmejiamtz/sdram_controller_generator?color=56BEB8">

  <img alt="Repository size" src="https://img.shields.io/github/repo-size/gmejiamtz/sdram_controller_generator?color=56BEB8">

  <img alt="License" src="https://img.shields.io/github/license/gmejiamtz/sdram_controller_generator?color=56BEB8">

  <!-- <img alt="Github issues" src="https://img.shields.io/github/issues/colbarron/spaceinvaders?color=56BEB8" /> -->

  <!-- <img alt="Github forks" src="https://img.shields.io/github/forks/colbarron/spaceinvaders?color=56BEB8" /> -->

  <!-- <img alt="Github stars" src="https://img.shields.io/github/stars/colbarron/spaceinvaders?color=56BEB8" /> -->
</p>

<!-- Status -->

<!-- <h4 align="center"> 
	🚧  Spaceinvaders 🚀 Under construction...  🚧
</h4> 

<hr> -->

<p align="center">
  <a href="#dart-about">About</a> &#xa0; | &#xa0;
  <a href="#rocket-technologies">Tools</a> &#xa0; | &#xa0;
  <a href="#memo-license">License</a> &#xa0;
</p>

<br>

## About ##

In this project, we provide a Chisel generator for SDRAM controllers.

## :hammer: Tools ##

The following tools were used in this project:

- [Chisel 3.6](https://github.com/chipsalliance/chisel) 
- [Yosys Open SYnthesis Suite](https://yosyshq.net/yosys/)
- [Verilator](https://www.veripool.org/verilator/)


<pre>
Root
|
+-- dvi_test
|  |
|  +-- Makefile
|  +-- top_square.sv
|
+-- enemy
|  |
|  +-- enemy.sv
|  +--Invoice.pdf
|
+-- gameSM
|  |
|  +-- gameSM.sv
|
+-- player
|  |
|  +-- player.sv
|  +-- testbench.sv
|  +-- Makefile
|
+-- provided_modules
|  |
|  +-- and2.sv
|  +-- counter.sv
|  +-- dff.sv
|  +-- inv.sv
|  +-- memory_init_file.hex
|  +-- or2.sv
|  +-- pipeline.sv
|  +-- ram_1r1w_sync.sv
|  +-- sync_reset.sv
|  +-- synchronizer.sv
|
+-- sprites
|  |
|  +-- render_enemy.sv
|
+-- top_module
|  |
|  +-- generate (a.out)
|  +-- hex_generator.c
|  +-- Makefile
|  +-- mem_init.cpp
|  +-- memory_bullets.hex
|  +-- memory_enemy.hex
|  +-- top.sv
|
+-- utils
|  |
|  +-- clock_gen_25MHz.sv
|  +-- dvi_controller.sv
|  +-- icebreaker.pcf
|  +-- nonsynth_clock_gen.sv
|  +-- nonsynth_reset_gen.sv
|
+-- fpga.mk
+-- LICENSE
+-- README.md
+-- simulation.mk
</pre>

## :memo: License ##

This project is under license from MIT. For more details, see the [LICENSE](LICENSE) file.


Made by <a href="https://github.com/gmejiamtz" target="_blank">Gary Mejia</a> and <a href="https://github.com/jlortiz0" target="_blank">Joaquin Ortiz</a>

&#xa0;

<a href="#top">Back to top</a>