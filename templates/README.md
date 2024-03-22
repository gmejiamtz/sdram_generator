## Templates Directory

This directory holds several .json files to describe an SDRAM module. 

### File Structure

File names will be the SDRAM name as stated in its datasheet. The following is an example of a configuration for the Micron MT48LC1M16A1 SDRAM module:

```json
{
    "company": "Micron",
    "name": "MT48LC1M16A1",
    "config": {
        "num_of_read_channels": 1,
        "num_of_write_channels": 1,
        "data_width": 16,
        "address_width": 12,
        "frequency_scale": 1000000,
        "frequency": 125,
        "burst_length": 0,
        "burst_type": 0,
        "cas_latency": 3,
        "opcode": 0,
        "write_burst": 0,
        "t_rcd": 20,
        "t_ref": 64,
        "t_wr": 10
    }
  }
```

The first data values are for descriptions, the SDRAM targetted and the company that manufactures it. The configurations are held in "config" where all values are mapped to integer values to represent values such as clock frequency, CAS latency, number of channels, and several other timing values to be respected. Users are encouraged to implement a datasheet as a .json template and upload it for future users. 
