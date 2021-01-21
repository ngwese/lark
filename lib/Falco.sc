//
// Falco, a wavetable synth
//
Falco {
  var <server;
  var <default_table;

  var <>oscA_type;
  var <>oscA_enabled;
  var <>oscA_table;

  var <>oscB_type;
  var <>oscB_enabled;
  var <>oscB_table;

  // Create and initialize a new Falco instance on the given server
  *new { arg server;
    ^super.new.init(server)
  }

  // Initialize class and define server side resources
  init { arg srv;
    server = srv;
    default_table = FalcoTable.new(srv).load(FalcoWaves.default);

    oscA_type = \falco_osc1;
    oscA_enabled = true;
    oscA_table = default_table;

    oscB_type = \falco_osc1;
    oscB_enabled = false;
    oscB_table = default_table;

    SynthDef(\falco_osc1, {
      arg out=0, hz=300, gate=1, amp=0.3, i_atk=0.2, i_decay=0.3, i_sus=0.7, i_rel=1,
          i_buf=0, i_numBuf=1, bufPos=0;
      var sig, pos, ampEnv, posEnv, detune;

      posEnv = EnvGen.kr(
        Env([0,1,0.5,0],[i_atk,i_sus,i_rel],[1,0,-1]),
        gate
      );

      ampEnv = EnvGen.kr(
        Env.adsr(i_atk, i_decay, i_sus, i_rel),
        gate: gate,
        levelScale: amp,
        doneAction: Done.freeSelf,
      );

      pos = i_buf + (i_numBuf * posEnv);
      sig = VOsc.ar(pos, freq: hz, mul: 0.3) * ampEnv;
      sig = LeakDC.ar(sig);

      Out.ar(out, sig!2);
    }).add;


    SynthDef(\falco_osc3, {
      arg out=0, hz=300, gate=1, amp=0.3, i_atk=0.2, i_decay=0.3, i_sus=0.7, i_rel=1,
          i_buf=0, i_numBuf=1, bufPos=0, spread=0.2, spreadHz=0.2;
      var sig, pos, ampEnv, posEnv, detune;

      posEnv = EnvGen.kr(
        Env([0,1,0.5,0],[i_atk,i_sus,i_rel],[1,0,-1]),
        gate
      );

      ampEnv = EnvGen.kr(
        Env.adsr(i_atk, i_decay, i_sus, i_rel),
        gate: gate,
        levelScale: amp,
        doneAction: Done.freeSelf,
      );

      pos = i_buf + (i_numBuf * posEnv);
      detune = LFNoise1.kr(spreadHz!3).bipolar(spread).midiratio;

      sig = VOsc3.ar(pos.poll, freq1: hz*detune[0], freq2: hz*detune[1], freq3: hz*detune[2], mul: 0.3) * ampEnv;
      sig = Splay.ar(sig);
      sig = LeakDC.ar(sig);

      Out.ar(out, sig);
    }).add;

  }

  // Play a note
  noteOn {
    arg hz=120, amp=0.1, bufPos=0, rel=0.2, spread=0.2, spreadHz=0.2, atk=0.2, decay=0.3, sus=0.7;
    ^if(oscA_enabled, {
      Synth(this.oscA_type, [
        \hz, hz,
        \amp, amp,
        \bufPos, bufPos,
        \i_buf, this.oscA_table.baseBuf,
        \i_numBuf, this.oscA_table.numBuf,
        \i_rel, rel,
        \spread, spread,
        \spread_hz, spreadHz,
        \i_atk, atk,
        \i_decay, decay,
        \i_sus, sus,
        \i_rel, rel,
      ]);
    }, { nil });
  }


  // TODO: methods to set various parameters
}

//
// FalcoWaves provides various helper methods to load or generate collections
// of wavetables suitable for passing to FalcoTable.
//
FalcoWaves {

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

    postln("FalcoWaves.fromFile(\"" ++ path ++ "\", " ++ tableSize ++ ")");
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
// FalcoTable allocates server Buffers and loads waves into the server
//
FalcoTable {
  var <server;
  var <buffers;
  var <baseBuf;
  var <numBuf;
  var <numLoaded;

  *fromFile { arg server, path, waveSize;
    ^this.new(server).load(FalcoWaves.fromFile(path, waveSize));
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
    this.free;    // release any Buffers held by this instance

    numLoaded = 0;

    buffers = Buffer.allocConsecutive(waves.size, server, waves[0].size);
    buffers.do({ arg buf, i;
      buf.loadCollection(waves[i], action: { arg buf; numLoaded = numLoaded + 1; });
    });

    baseBuf = buffers[0].bufnum;
    numBuf = buffers.size - 2;

    postln("FalcoTable.load: base = " ++ baseBuf ++ ", num = " ++ numBuf);
  }

  free {
    if(buffers.notNil, {
      buffers.do({ arg buf; buf.free; });
      numLoaded = 0;
      baseBuf = 0;
      numBuf = 0;
    });
  }
}




/*

~q = FalcoTable.new(s).load(FalcoWaves.fromFile("/Applications/WaveEdit/banks/ROM B.wav", 256));
~q = FalcoTable.fromFile(s, "/Applications/WaveEdit/banks/ROM B.wav", 256);
~q.numBuf
~q.numLoaded
~q.free
~f = Falco.new(s);
~f.loadBuffers(FalcoWaves.random());

~w = FalcoWaves.fromFile("/Applications/WaveEdit/banks/ROM B.wav", 256);
~w = FalcoWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/4088.wav", 2048);
~w = FalcoWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/Jno.wav", 2048);
~w = FalcoWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Analog/SawRounded.wav", 2048);

~f.loadBuffers(~w);

~f.loadBuffers(FalcoWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/Digital/SubBass_1.wav", 2048));

~f.loadBuffers(FalcoWaves.fromFile("/Library/Audio/Presets/Xfer Records/Serum Presets/Tables/User/AF kaivo test2.wav", 2048));


~f.buffers[0].query;
~f.buffers[0].plot;
~x = ~f.noteOn(80, bufPos: 0, rel: 4);
~x = ~f.noteOn(80, rel: 4, spread: 0.8, spreadHz: 0.17);
~x = ~f.noteOn(440, rel: 4, spread: 0.14, spreadHz: 0.03);
~x = ~f.noteOn(220, rel: 4, spread: 0, spreadHz: 0);
~x = ~f.noteOn(120, rel: 4, spread: 0.14, spreadHz: 0.03);

~x = ~f.noteOn(40, rel: 4, spread: 0.14, spreadHz: 0.03);
~x.free;
~x.set(\gate, 0);

~y = ~f.noteOn(120, rel: 4, spread: 0.14, spreadHz: 0.03);
~y.free;
~y.set(\gate, 0);

~z = ~f.noteOn(80, rel: 4, spread: 0.14, spreadHz: 0.03);
~z.free;
~z.set(\gate, 0);


~wt = FalcoWaves.random();
~w.do({arg w, i; w.plot(i.asString)});


~wt.size;
~wt[0].plot;
~wt[1].plot;

*/