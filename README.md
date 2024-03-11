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

## Usage ##

To use this generator follow the steps below:

### Step 1

Clone the repo:

```git clone https://github.com/gmejiamtz/sdram_controller_generator.git```

### Step 2

Supply your config.json file and provide the generator its path:

```bash
cd sdram_controller_gen
sbt -Xmx2048M run $PATH_TO_CONFIG_FILE
  ```

It may be useful to set `SBT_OPTS` to `-Xmx2048M` so that you do not have to type it multiple times.

### Step 3 

To test simply do the following commands:

```bash
cd sdram_controller_gen
sbt -Xmx2048M test
```

Tests at the moment only test for initialization of an MT48LC1M16A1 Micron SDRAM. More will be
added soon!

## To Do List ##

Targetting Micron MT48LC1M16A1 SDRAM - 512K x 16 x 2 banks

1. Build SDRAM controller state machine - In Progress - Init and Read(CAS of 1,2, and 3) States are verified, Write State in current testing

2. Build SDRAM controller model - In Progress - Mostly complete, needs more rigorous testing, proper decay parameterization

3. Generate tests for MT48LC1M16A1 controller - In Progress 

4. Build Main program to generate Verilog - In Progress

## :hammer: Tools ##

The following tools were used in this project:

- [Chisel 3.6](https://github.com/chipsalliance/chisel) 
- [Yosys Open SYnthesis Suite](https://yosyshq.net/yosys/)
- [Verilator](https://www.veripool.org/verilator/)

## :memo: License ##

This project is under license from MIT. For more details, see the [LICENSE](LICENSE) file.


Made by <a href="https://github.com/gmejiamtz" target="_blank">Gary Mejia</a> and <a href="https://github.com/jlortiz0" target="_blank">Joaquin Ortiz</a>

&#xa0;

<a href="#top">Back to top</a>
