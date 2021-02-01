//
// LarkWaves provides various helper methods to load or generate collections
// of wavetables suitable for passing to LarkTable.
//
LarkWaves {

  // Generate the default (sine) wave
  *default {
    var wave = Signal.sineFill(1024, [1], [0]).asWavetable;
    ^Array.fill(2, { wave })
  }

  // Generate a collection of 4 randomly shaped wavetables
  *random {
    ^Array.fill(4, {
      var numSegs = rrand(4, 20);
      Env(
        [0]++
        (({rrand(0.0, 1.0)}!(numSegs-1)) * [1, -1]).scramble
        ++[0],
        {exprand(1,20)}!numSegs,
        {rrand(-20,10)}!numSegs
      ).asSignal(1024).asWavetable;
    });
  }

  // Load a collection of concatenated wavetables from the given file.
  //
  // Reads the given file sound file splitting into tableSize chunks and
  // converting it to wavetable format. It is assumed that tableSize is a power
  // of 2.
  //
  *fromFile {
    arg path, tableSize;

    var f = SoundFile.openRead(path);
    var raw = Signal.newClear(f.numFrames);
    var numWaves = f.numFrames.div(tableSize);
    var waves = Array.newClear(numWaves);
    var offset = 0;

    postln("LarkWaves.fromFile(\"" ++ path ++ "\", " ++ tableSize ++ ")");
    postln("  numWaves = " ++ numWaves);
    postln("  extra = " ++ f.numFrames.mod(tableSize));
    // TODO: check waveSize is power of 2
    // TODO: check numFrames is a multiple of waveSize

    f.readData(raw);
    numWaves.do({
      arg i;
      var offset = i * tableSize;
      waves[i] = raw[offset..(offset + tableSize - 1)].asWavetable;
    });

    ^waves;
  }

}

//
// LarkTable allocates server Buffers and loads waves into the server
//
LarkTable {
  var <server;
  var <buffers;
  var <baseBuf;
  var <numBuf;
  var <numLoaded;
  var <isReady;

  *fromFile { arg server, path, waveSize;
    ^this.new(server).load(LarkWaves.fromFile(path, waveSize));
  }

  *new { arg server;
    ^super.new.init(server);
  }

  init { arg srv;
    server = srv;
    numLoaded = 0;
  }

  // Load the collection of wavetables into buffers on the server.
  //
  // The provided wavetables are assumed to be power of 2 in size and in
  // wavetable format (see Signal, Wavetable for more detail). Any previously
  // loaded wavetables are freed before loading the new set.
  //
  load { arg waves;
    var numWaves = waves.size;

    this.free;    // release any Buffers held by this instance

    numLoaded = 0;

    buffers = Buffer.allocConsecutive(numWaves, server, waves[0].size);
    buffers.do({ arg buf, i;
      buf.loadCollection(waves[i], action: { arg buf;
        numLoaded = numLoaded + 1;
        isReady = numLoaded == numWaves;
      });
    });

    baseBuf = buffers[0].bufnum;
    numBuf = buffers.size - 1;

    postln("LarkTable.load: base = " ++ baseBuf ++ ", num = " ++ numBuf);
  }

  free {
    if(buffers.notNil, {
      buffers.do({ arg buf; buf.free; });
      numLoaded = 0;
      baseBuf = 0;
      numBuf = 0;
    });
  }

  printOn {
    arg stream;
    stream << "LarkTable(" << baseBuf << ", " << numBuf << ", " << buffers << ")\n";
  }
}


LarkWaveLibrary {
  *nameFromPath { arg path;
    path = PathName.new(path);

  }
}


//
// Singleton registry of available tables which have been loaded on the server
//
LarkRegistry {
  classvar tables;
  classvar tableNames;

  *initClass {
    tables = Dictionary.new;
    tableNames = Dictionary.new;
  }

  *register { arg name, waves, server=Server.default;
    name = name.asSymbol;
    if(tables.keys.includes(name), {
      postln("warning: attempted to register table using existing key: '" ++ name ++ "'");
      ^false;
    }, {
      tableNames[tables.size] = name;
      tables[name] = LarkTable.new(server).load(waves);
      postln("LarkTableRegistry: registered: '" ++ name ++ "'");
      ^true;
    });
  }

  *remove { arg name;
    name = name.asSymbol;
    if(tables.keys.includes(name), {
      tables[name].free;
      tableNames.removeAt(tables[name].index);
      tables.removeAt(name);
    })
  }

  *clear {
    tables.do({ arg t; t.free; });
    tables.clear;
  }

  *get { arg index;
    ^tables[tableNames[index]];
  }

  *at { arg name;
    ^tables[name.asSymbol];
  }

  *size { ^tables.size }

  *names { ^tableNames.values }

  *free {
    tables.values.do({ arg table; table.free; });
  }
}

/*

LarkRegistry.size
LarkRegistry.names

LarkRegistry.register("4088", LarkWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/4088.wav", 2048));
LarkRegistry.at(\random1).baseBuf

PathName.new("/foo/bar.256.wav").fileNameWithoutExtension

PathName.new("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables").filesDo({arg a; a.postln;})

*/